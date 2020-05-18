package oddisey

import java.util.UUID

import scala.util.Try
import scala.concurrent.duration._

import cats.effect.concurrent.Ref
import cats.effect.{ IO, Resource }
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import fs2.concurrent.SignallingRef
import fs2.kafka.{ ProducerRecord, ProducerRecords, Serializer }
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.clients.admin.NewTopic

import oddisey.grpc.example.Odysseus

class KafkaStreamsSpec extends KafkaStreamsBaseSpec {

  def odysseusParser(bs: Array[Byte]) = Try(Odysseus.parseFrom(bs)).toOption
  implicit val streamSerde            = fromFn(Odysseus.toByteArray(_: Odysseus), odysseusParser)

  def topology(logger: Logger[IO], ref: Ref[IO, List[Odysseus]], count: Ref[IO, Int], exceptionCnt: Ref[IO, Int]) = {
    val builder = new StreamsBuilder
    val kStream = builder.stream[String, Odysseus](sourceTopic).map {
      case (key, value) =>
        val io =
          exceptionCnt.get
            .flatMap(c =>
              count.get.flatMap(c => logger.info(s"Count: $c")) *>
              ref.get.flatMap(c => logger.info(s"Duplicated Messages: ${c.size - c.toSet.size}")) *>
              logger.info(s"Exception count: $c") *>
              logger.info(s"To be processed: ${value.message}") *>
              (IO.sleep(500.millis) *> IO.raiseError(new Exception("Bum!"))).whenA(c == 5)
            ) *> ref.update(value :: _) *>
          exceptionCnt.update(_ + 1) *> count.update(_ + 1)

        "" -> (IO.shift *> io *> logger.info(s"Processed: ${value.message}")).unsafeRunSync()
    }
//    kStream.print(Printed.toSysOut())
    builder
  }

  val producer = KafkaClient.kafkaProducer(host, port, keySerializer, odysseusSerializer)
  def record   = ProducerRecord(sourceTopic, UUID.randomUUID().toString, Odysseus(UUID.randomUUID().toString))
  val records  = (1 to 10).map(_ => record)
  val produce  = producer.use(p => p.produce(ProducerRecords(records.toList)).flatten)

  test("kafkaStreams spec") {
    def stream(
      appId: String,
      logger: Logger[IO],
      ref: Ref[IO, List[Odysseus]],
      count: Ref[IO, Int],
      exceptionCnt: Ref[IO, Int]
    ) =
      KafkaStreamsClient.stream(host, port, topology(logger, ref, count, exceptionCnt).build(), appId)

    def prepare =
      for {
        _        <- admin.use(_.createTopic(new NewTopic(sourceTopic, 2, 1.toShort))).attempt
        _        <- produce
        logger   <- Slf4jLogger.create[IO]
        count    <- Ref.of[IO, Int](0)
        messages <- Ref.of[IO, List[Odysseus]](List())
        signal   <- SignallingRef[IO, Boolean](false)
      } yield (logger, count, messages, signal)

    def streamHandler(
      logger: Logger[IO],
      count: Ref[IO, Int],
      messages: Ref[IO, List[Odysseus]]
    ) =
      for {
        exceptionCnt <- Ref.of[IO, Int](0)
        streams      <- IO.pure(FKafkaStreams(stream(appId1, logger, messages, count, exceptionCnt)))
      } yield streams

    def spec(
      logger: Logger[IO],
      count: Ref[IO, Int],
      messages: Ref[IO, List[Odysseus]]
    ): IO[List[Odysseus]] =
      for {
        signal <- SignallingRef[IO, Boolean](false)
        results <- fs2.Stream
                    .repeatEval(messages.get)
                    .evalMap(r1 => signal.set(true).whenA(r1.size >= records.size))
                    .interruptWhen(signal)
                    .interruptAfter(25.seconds)
                    .compile
                    .drain
                    .start
        _ <- fs2.Stream
              .repeatEval(
                Resource
                  .make(streamHandler(logger, count, messages))(s => logger.info(s"Closing") *> s.close)
                  .use(s =>
                    logger.info("StartingStream") *> s.serve.handleErrorWith(err => logger.error(s"Kabum: $err"))
                  )
              )
              .interruptWhen(signal)
              .compile
              .drain

        _    <- results.join
        res1 <- messages.get
      } yield res1

    prepare.flatMap {
      case (l, c, m, s) =>
        spec(l, c, m).flatMap { res =>
          l.info("done").map(_ => assertEquals(res.map(_.message).toSet, records.map(_.value.message).toSet))
        }
    }.unsafeRunSync()

  }
}

trait KafkaStreamsBaseSpec extends KafkaBaseSpec {
  val spec = UUID.randomUUID().toString

  val sourceTopic = s"${UUID.randomUUID()}"
  val appId1      = s"${UUID.randomUUID()}"
  val words       = "But be content with the food and drink aboard our ship ..."

  val admin = KafkaClient.kafkaAdmin[IO](host, port)

  val keySerializer      = Serializer[IO, String]
  val odysseusSerializer = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])
}
