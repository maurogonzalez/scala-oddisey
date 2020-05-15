package oddisey

import java.util.Date
import scala.util.Try
import scala.concurrent.duration._

import cats.effect.{IO, Resource}
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import fs2.kafka.{ProducerRecord, ProducerRecords, Serializer}
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.Serdes._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.kstream.Printed
import oddisey.grpc.example.Odysseus

class KafkaStreamsSpec extends KafkaBaseSpec {
  val sourceTopic = "kafka-streams-1"
  val sinktopic   = "kafka-streams-2"
  val appId       = "kafka-streams-application-2"
  val words       = "But be content with the food and drink aboard our ship ..."

  // Serdes
  def odysseusParser(bs: Array[Byte]): Option[Odysseus] = Try(Odysseus.parseFrom(bs)).toOption

  val keySerializer                         = Serializer[IO, String]
  val odysseusSerializer                    = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])
  implicit val streamSerde: Serde[Odysseus] = fromFn(Odysseus.toByteArray(_: Odysseus), odysseusParser)

  // KafkaStreams
  def kStreamio(shoudThrow: Boolean) = IO {
    val builder = new StreamsBuilder
    val kStream = builder.stream[String, Odysseus](sourceTopic).mapValues { t =>
      if (shoudThrow) throw new Exception("Bum!")
      t
    }
    kStream.to(sinktopic)
    kStream.print(Printed.toSysOut())
    builder
  }

  // Kafka Producer
  val producer = KafkaClient.kafkaProducer(host, port, keySerializer, odysseusSerializer)
  val record   = ProducerRecord(sourceTopic, java.time.LocalDateTime.now().toString, Odysseus(words))
  val produce  = producer.use(p => p.produce(ProducerRecords.one(record)).flatten)

  test("kafkaStreams") {
    def stream(shouldThrow: Boolean = false) = Resource.make(
      kStreamio(shouldThrow).map(b => KafkaStreamsClient.stream(host, port, b.build(), appId))
    )(s => IO(s.close()))


    (for {
     _  <- produce
     s1 <- stream(true).use(s => IO(s.start()) *> IO.sleep(10.seconds)).start
     s2 <- stream().use(s => IO(s.start()) *> IO.sleep(10.seconds)).start
     _ <- s1.join
     _ <- s2.join
    } yield ())
    .unsafeRunSync()

  }

}
