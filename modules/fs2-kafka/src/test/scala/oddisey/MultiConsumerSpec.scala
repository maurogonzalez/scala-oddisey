package oddisey

import java.util.UUID.randomUUID

import cats.effect.{ IO, Resource }
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.parallel._
import fs2.concurrent.SignallingRef
import fs2.kafka._
import oddisey.grpc.example.{ Odysseus, OdysseusKey }

class MultiConsumerSpec extends KafkaBaseSpec {

  val odysseusDeserializer    = Deserializer.lift(bs => Odysseus.parseFrom(bs).pure[IO])
  val odysseusSerializer      = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])
  val odysseusKeyDeserializer = Deserializer.lift(bs => OdysseusKey.parseFrom(bs).pure[IO])
  val odysseusKeySerializer   = Serializer.lift[IO, OdysseusKey](_.toByteArray.pure[IO])

  test("producer/consumer") {
    // Single topic
    val topic = "MultiTopic"
    // Consumer group 0
    val group0 = "OddiseyConsumerGroup0"
    // Consumer group 1
    val group1 = "OddiseyConsumerGroup1"
    // Message
    val words = "But be content with the food and drink aboard our ship ..."

    // Custom IDs for Kafka key
    val customId0 = randomUUID().toString
    val customId1 = randomUUID().toString

    val producer = KafkaClient.kafkaProducer(host, port, odysseusKeySerializer, odysseusSerializer)

    // Multiple consumers for consumer group 0
    val consumer00 = KafkaClient.kafkaConsumer(host, port, odysseusKeyDeserializer, odysseusDeserializer, group0)
    val consumer01 = KafkaClient.kafkaConsumer(host, port, odysseusKeyDeserializer, odysseusDeserializer, group0)
    // Multiple consumers for consumer group 1
    val consumer10 = KafkaClient.kafkaConsumer(host, port, odysseusKeyDeserializer, odysseusDeserializer, group1)
    val consumer11 = KafkaClient.kafkaConsumer(host, port, odysseusKeyDeserializer, odysseusDeserializer, group1)

    val keys0    = (0 to 10).toList.map(_ => OdysseusKey(id = randomUUID.toString, custom = customId0))
    val records0 = keys0.map(ProducerRecord(topic, _, Odysseus(words)))
    val keys1    = (0 to 10).toList.map(_ => OdysseusKey(id = randomUUID.toString, custom = customId1))
    val records1 = keys1.map(ProducerRecord(topic, _, Odysseus(words)))
    val produce  = producer.use(p => p.produce(ProducerRecords(records0 ++ records1)).flatten)

    def consume(
      custom: String,
      consumer: Resource[IO, KafkaConsumer[IO, OdysseusKey, Odysseus]],
      msg: Ref[IO, List[OdysseusKey]],
      signal: SignallingRef[IO, Boolean]
    ) =
      consumer
        .evalTap(_.subscribeTo(topic))
        .use(c =>
          c.stream.evalMap { committable =>
            committable.offset.commit.whenA(committable.record.key.custom == custom) &>
            msg.update(committable.record.key +: _).whenA(committable.record.key.custom == custom) *>
            msg.get.flatMap(ks => signal.set(true).whenA(ks.size == keys0.size))
          }.interruptWhen(signal).compile.drain
        )

    val process = for {
      ref0 <- Ref.of[IO, List[OdysseusKey]](List())
      ref1 <- Ref.of[IO, List[OdysseusKey]](List())
      s0   <- SignallingRef[IO, Boolean](false)
      s1   <- SignallingRef[IO, Boolean](false)
      p    <- produce.start
      c00  <- consume(customId0, consumer00, ref0, s0).start
      c01  <- consume(customId0, consumer01, ref0, s0).start
      c10  <- consume(customId1, consumer10, ref1, s1).start
      c11  <- consume(customId1, consumer11, ref1, s1).start
      _    <- p.join
      _    <- c00.join
      _    <- c01.join
      _    <- c10.join
      _    <- c11.join
    } yield (ref0, ref1)

    process.flatMap {
      case (ref0, ref1) =>
        for {
          r0 <- ref0.get
          r1 <- ref1.get
        } yield {
          assertEquals(r0.toSet, keys0.toSet)
          assertEquals(r1.toSet, keys1.toSet)
        }
    }.unsafeToFuture()
  }
}
