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
import cats.syntax.parallel._
import fs2.concurrent.SignallingRef
import fs2.kafka.{ ProducerRecord, ProducerRecords, Serializer }
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.kstream.Printed
import oddisey.grpc.example.Odysseus
import org.apache.kafka.clients.admin.NewTopic

class KafkaStreamsSpec extends KafkaBaseSpec {
  val spec = UUID.randomUUID().toString

  val sourceTopic = s"kafka-streams-source-test-par4546"
  val appId1      = s"kafka-streams-application-fixed-par4546"
  val words       = "But be content with the food and drink aboard our ship ..."

  // Serdes Serializar/Deserializer
  def odysseusParser(bs: Array[Byte]): Option[Odysseus] = Try(Odysseus.parseFrom(bs)).toOption

  val keySerializer                         = Serializer[IO, String]
  val odysseusSerializer                    = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])
  implicit val streamSerde: Serde[Odysseus] = fromFn(Odysseus.toByteArray(_: Odysseus), odysseusParser)

  // KafkaStreams
  def topology(shoudThrow: Boolean, ref: Ref[IO, List[Odysseus]]) = {
    val builder = new StreamsBuilder
    val kStream = builder.stream[String, Odysseus](sourceTopic).mapValues { t =>
      val io =
        if (shoudThrow) IO.raiseError(new Exception("Bum!"))
        else ref.update(t :: _)

      println(s"Received in $spec")
      (IO.shift *> IO.sleep(5.seconds) *> io).unsafeRunSync()
    }
//    kStream.to(sinktopic)
    kStream.print(Printed.toSysOut())
    builder
  }

  // Kafka Producer
  val producer = KafkaClient.kafkaProducer(host, port, keySerializer, odysseusSerializer)
  def record   = ProducerRecord(sourceTopic, UUID.randomUUID().toString, Odysseus(UUID.randomUUID().toString))
  val records  = (1 to 10).map(_ => record)
  val produce  = producer.use(p => p.produce(ProducerRecords(records.toList)).flatten)

  val admin = KafkaClient.kafkaAdmin[IO](host, port)

  test("kafkaStreams spec") {
    def stream(appId: String, shouldThrow: Boolean, ref: Ref[IO, List[Odysseus]]) =
      new FKafkaStreams(KafkaStreamsClient.stream(host, port, topology(shouldThrow, ref).build(), appId))

    val spec = for {
      res      <- admin.use(_.createTopic(new NewTopic(sourceTopic, 2, 1.toShort))).attempt
      _ = println(res)
      ref1   <- Ref.of[IO, List[Odysseus]](List())
      signal <- SignallingRef[IO, Boolean](false)
      stream1 = stream(appId1, false, ref1)
      _ <- IO.sleep(10.seconds)
      s1 <- stream1.serve.start
      _ <- fs2.Stream
            .repeatEval(ref1.get)
            .evalMap { r1 =>
              signal.set(true).whenA { r1.map(_.message).toSet == records.map(_.value.message).toSet }
            }
            .interruptWhen(signal)
            .interruptAfter(5.seconds)
            .compile
            .drain
      res1 <- ref1.get
      _ <- (stream1.close *> s1.cancel)
    } yield {
      println(s"Count is: ${res1.size}")
      assertEquals(res1.map(_.message).toSet, records.map(_.value.message).toSet)

    }

//    produce.unsafeRunSync()
    spec.unsafeRunSync()

  }

}
