package oddisey

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.effect.concurrent.MVar
import cats.syntax.applicative._
import cats.syntax.parallel._
import cats.syntax.flatMap._
import fs2.concurrent.SignallingRef
import fs2.kafka._
import munit.FunSuite

import oddisey.grpc.example.Odysseus

class KafkaClientSpec extends FunSuite {
  val host = "localhost"
  val port = 29092

  val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val ioTimer: Timer[IO] = IO.timer(ec)

  val odysseusDeserializer = Deserializer.lift(bs => Odysseus.parseFrom(bs).pure[IO])
  val odysseusSerializer    = Serializer.lift[IO, Odysseus](_.toByteArray.pure[IO])

  test("producer/consumer") {
    val topic = "OddiseyTopic"
    val group = "OddiseyConsumerGroup"

    val producer = KafkaClient.kafkaProducer(host, port, odysseusSerializer)
    val consumer = KafkaClient.kafkaConsumer(host, port, odysseusDeserializer, group)

    val words = "But be content with the food and drink aboard our ship ..."
    val record = ProducerRecord(topic, "first", Odysseus(words))

    val produce = producer.use(p => p.produce(ProducerRecords.one(record)).flatten)
    def consume(msg: MVar[IO, Odysseus], signal: SignallingRef[IO, Boolean]) =
      consumer
        .evalTap(_.subscribeTo(topic))
        .use(c =>
          c.stream.evalMap(committable =>
            committable.offset.commit &>
              msg.put(committable.record.value) &>
              signal.set(true)
            )
            .interruptWhen(signal)
            .compile
            .drain
        )

    val process = for {
      msg <- MVar.empty[IO, Odysseus]
      s   <- SignallingRef[IO, Boolean](false)
      p   <- produce.start
      c   <- consume(msg, s).start
      _   <- p.join
      _   <- c.join
    } yield msg

    process
      .flatMap(_.tryTake)
      .map { msg =>
        assert(msg.nonEmpty, "Message is empty!")
        assertEquals(msg.get.message, words)
      }.unsafeToFuture()
  }

}
