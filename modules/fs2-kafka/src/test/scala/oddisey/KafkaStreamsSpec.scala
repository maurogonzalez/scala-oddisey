package oddisey

import java.util.UUID

import scala.util.Try
import scala.concurrent.duration._

import cats.effect.concurrent.Ref
import cats.effect.IO
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import fs2.concurrent.SignallingRef
import fs2.kafka.{ ProducerRecord, ProducerRecords, Serializer }
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.kstream.Printed
import oddisey.grpc.example.Odysseus
import org.apache.kafka.clients.admin.NewTopic

class ProducerSpec extends KafkaStreamsBaseSpec {

  val producer = KafkaClient.kafkaProducer(host, port, keySerializer, odysseusSerializer)
  def record   = ProducerRecord(sourceTopic, UUID.randomUUID().toString, Odysseus(UUID.randomUUID().toString))
  val records  = (1 to 1000).map(_ => record)
  val produce  = producer.use(p => p.produce(ProducerRecords(records.toList)).flatten)

  admin.use(_.createTopic(new NewTopic(sourceTopic, 2, 1.toShort))).attempt.unsafeRunSync()
  produce.unsafeRunSync()
}

class KafkaStreamsSpec extends KafkaStreamsBaseSpec {

  // Serdes Serializar/Deserializer
  def odysseusParser(bs: Array[Byte]): Option[Odysseus] = Try(Odysseus.parseFrom(bs)).toOption

  implicit val streamSerde: Serde[Odysseus] = fromFn(Odysseus.toByteArray(_: Odysseus), odysseusParser)

  // KafkaStreams
  def topology(shoudThrow: Boolean, ref: Ref[IO, List[Odysseus]], count: Ref[IO, Int]) = {
    val builder = new StreamsBuilder
    val kStream = builder.stream[String, Odysseus](sourceTopic).mapValues { t =>
      val io =
        count.get
          .flatMap(c =>
            IO(println(s"Count is $c\nMessage: ${t.message}")) *>
            IO.raiseError(new Exception("Bum!")).whenA(c == 5)
          ) *> count
          .update(_ + 1) *> ref.update(t :: _)

      (IO.shift *> IO.sleep(500.millis) *> io).unsafeRunSync()
    }
//    kStream.print(Printed.toSysOut())
    builder
  }

  val producer = KafkaClient.kafkaProducer(host, port, keySerializer, odysseusSerializer)
  def record   = ProducerRecord(sourceTopic, UUID.randomUUID().toString, Odysseus(UUID.randomUUID().toString))
  val records  = (1 to 10).map(_ => record)
  val produce  = producer.use(p => p.produce(ProducerRecords(records.toList)).flatten)

  test("kafkaStreams spec") {
    def stream(appId: String, shouldThrow: Boolean, ref: Ref[IO, List[Odysseus]], count: Ref[IO, Int]) =
      KafkaStreamsClient.stream(host, port, topology(shouldThrow, ref, count).build(), appId)

    def spec(): IO[Unit] =
      for {
        _        <- admin.use(_.createTopic(new NewTopic(sourceTopic, 2, 1.toShort))).attempt
        count    <- Ref.of[IO, Int](0)
        messages <- Ref.of[IO, List[Odysseus]](List())
        signal   <- SignallingRef[IO, Boolean](false)
        streams = FKafkaStreams(stream(appId1, false, messages, count))
        queue <- streams.serve
        _ <- queue.dequeue.evalMap { err =>
              println(s"Error in Stream Thread: $err")
              println(s"Closing!")
              println(s"Restarting!")
              streams.close *>
              streams.cleanUp *>
              // Not cleaning resources
              spec()
            }.compile.drain.start
        _ <- IO.sleep(100.minutes)
        _ <- fs2.Stream
              .repeatEval(messages.get)
              .evalMap { r1 =>
                signal.set(true).whenA { r1.map(_.message).toSet == records.map(_.value.message).toSet }
              }
              .interruptWhen(signal)
              .interruptAfter(5.seconds)
              .compile
              .drain
        res1 <- messages.get
        _    <- streams.close
      } yield {
        println(s"Count is: ${res1.size}")
        assertEquals(res1.map(_.message).toSet, records.map(_.value.message).toSet)
      }

    spec().unsafeRunSync()

  }

}

trait KafkaStreamsBaseSpec extends KafkaBaseSpec {
  val spec = UUID.randomUUID().toString

  val sourceTopic = s"topic"
  val appId1      = s"app"
  val words       = "But be content with the food and drink aboard our ship ..."

  val admin = KafkaClient.kafkaAdmin[IO](host, port)

  val keySerializer      = Serializer[IO, String]
  val odysseusSerializer = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])
}
