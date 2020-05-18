package oddisey

import java.util.UUID

import scala.concurrent.duration._
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import dev.profunktor.redis4cats.RedisCommands
import fs2.concurrent.SignallingRef
import fs2.kafka.{ CommittableConsumerRecord, Deserializer, ProducerRecord, ProducerRecords, Serializer }
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import oddisey.grpc.example.Odysseus

class KafkaCachedSpec extends KafkaBaseSpec {
  implicit val stringCodec = RedisClient.stringCodec
  implicit val logger      = Slf4jLogger.create[IO].unsafeRunSync()

  val client = RedisClient.redisStandalone("localhost", None)

  val topic = "OddiseyTopic7"
  val group = "OddiseyConsumerGroup7"

  val keySerializer        = Serializer[IO, String]
  val keyDeserializer      = Deserializer[IO, String]
  val odysseusDeserializer = Deserializer.lift(bs => Odysseus.parseFrom(bs).pure[IO])
  val odysseusSerializer   = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])

  val producer    = KafkaClient.kafkaProducer(host, port, keySerializer, odysseusSerializer)
  val badProducer = KafkaClient.kafkaProducer(host, port, keySerializer, keySerializer)
  val consumer    = KafkaClient.kafkaConsumer(host, port, keyDeserializer, odysseusDeserializer, group)
  val words       = "But be content with the food and drink aboard our ship ..."
  val ids         = (1 to 10).map(_ => UUID.randomUUID().toString).toList
  val record      = ids.map(i => ProducerRecord(topic, i, Odysseus(i)))
  val badRecord   = ids.map(i => ProducerRecord(topic, i, words))

  val produce    = producer.use(p => p.produce(ProducerRecords(record)).flatten)
  val badProduce = badProducer.use(p => p.produce(ProducerRecords(badRecord)).flatten)

  def cache(redis: RedisCommands[IO, String, String], key: String): IO[Boolean] =
    for {
      isSet <- redis.hSetNx(key, "field", "value")
      _     <- redis.expire(key, 1.second)
    } yield isSet

  def consume(
    redis: RedisCommands[IO, String, String],
    msg: Ref[IO, List[Odysseus]],
    signal: SignallingRef[IO, Boolean]
  ) =
    consumer
      .evalTap(_.subscribeTo(topic))
      .evalTap(_ => logger.info("Starting consumer"))
      .use { c =>
        Ref.of[IO, Int](0).map(ct => ct).flatMap(counter =>
          c.stream
            .evalTap(_ => counter.get.flatMap(ct => signal.set(true).whenA(ct != 0 && ct % 3 == 0)))
            .interruptWhen(signal)
            .evalMap(m => cache(redis, m.record.key).flatMap(isNew => handler(m, msg).whenA(isNew)))
            .handleErrorWith(err => fs2.Stream.eval(logger.error(err)(s"Error in consumer")))
            .compile
            .drain
        )
      }

  def handler(
    message: CommittableConsumerRecord[IO, String, Odysseus],
    msgs: Ref[IO, List[Odysseus]]
  ) =
    msgs.update(message.record.value :: _) *> message.offset.commit

  test("kafka-cached") {
    val spec = RedisClient.redisStandalone("localhost", None).use { redis =>
      for {
        msgs    <- Ref.of[IO, List[Odysseus]](List())
        signal0 <- SignallingRef[IO, Boolean](false)
        signal1 <- SignallingRef[IO, Boolean](false)
        _ <- produce.start
        c <- fs2.Stream
              .repeatEval(consume(redis, msgs, signal0))
              .interruptWhen(signal1)
              .compile
              .drain
              .start
        _ <- fs2.Stream
              .repeatEval(msgs.get)
              .evalMap(r1 => (signal1.set(true) *> c.cancel).whenA(r1.size >= ids.size))
              .interruptWhen(signal1)
              .interruptAfter(25.seconds)
              .compile
              .drain
        result <- msgs.get
      } yield assertEquals(result.map(_.message).toSet, ids.toSet)
    }

    spec.unsafeRunSync()

  }

}
