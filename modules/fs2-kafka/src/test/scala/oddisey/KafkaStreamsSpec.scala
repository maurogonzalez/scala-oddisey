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
  val sourceTopic = s"kafka-streams-source-test"
  val sinktopic   = s"kafka-streams-sink-${UUID.randomUUID()}"
  val appId1      = s"kafka-streams-application-fixed"
  val appId2      = s"kafka-streams-application-${UUID.randomUUID()}"
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

      (IO.shift *> io).unsafeRunSync()
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

  test("kafkaStreams") {
    def stream(appId: String, shouldThrow: Boolean, ref: Ref[IO, List[Odysseus]]) =
      new FKafkaStreams(KafkaStreamsClient.stream(host, port, topology(shouldThrow, ref).build(), appId))

    val spec = for {
      _      <- admin.use(_.createTopic(new NewTopic(sourceTopic, 2, 1.toShort)))
      ref1   <- Ref.of[IO, List[Odysseus]](List())
      ref2   <- Ref.of[IO, List[Odysseus]](List())
      signal <- SignallingRef[IO, Boolean](false)
      stream1 = stream(appId1, false, ref1)
      stream2 = stream(appId2, false, ref2)
      _  <- produce
      s1 <- stream1.serve.start
      s2 <- stream2.serve.start
      _ <- fs2.Stream
            .repeatEval(ref1.get.flatMap(r1 => ref2.get.map(r1 -> _)))
            .evalMap {
              case (r1, r2) =>
                signal.set(true).whenA {
                  r1.map(_.message).toSet == records.map(_.value.message).toSet &&
                  r2.map(_.message).toSet == records.map(_.value.message).toSet
                }
            }
            .interruptWhen(signal)
            .interruptAfter(5.seconds)
            .compile
            .drain
      res1 <- ref1.get
      res2 <- ref2.get
      - <- IO.sleep(30.seconds)
      _ <- (stream1.close *> s1.cancel) &> (stream2.close *> s2.cancel)
    } yield {
      assertEquals(res1.map(_.message).toSet, records.map(_.value.message).toSet)
      assertEquals(res2.map(_.message).toSet, records.map(_.value.message).toSet)
    }

    spec.unsafeRunSync()

  }

}
