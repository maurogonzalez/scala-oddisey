package oddisey.zio

import munit.FunSuite
import zio._
import zio.kafka.serde.{ Deserializer, Serializer }
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.consumer.{ Consumer, Subscription }
import zio.kafka.producer.Producer
import zio.stream.ZStream

import oddisey.grpc.example.Odysseus

class KafkaClientSpec extends FunSuite {
  val host = "localhost"
  val port = 29092

  val runtime              = Runtime.default
  val odysseusDeserializer = Deserializer((_, _, bs) => RIO(Odysseus.parseFrom(bs)))
  val odysseusSerializer   = Serializer((_, _, bs: Odysseus) => RIO(bs.toByteArray))

  test("producer/consumer") {
    val topic = "OddiseyTopic"
    val group = "OddiseyConsumerGroup"

    val producer = KafkaClient.kafkaProducer(host, port, odysseusSerializer)
    val consumer = KafkaClient.kafkaConsumer(host, port, group, odysseusDeserializer)

    val words  = "But be content with the food and drink aboard our ship ..."
    val record = new ProducerRecord(topic, "first", Odysseus(words))

    val produce =
      Producer
        .produce[Any, String, Odysseus](record)

    def consume(msg: Ref[Seq[Odysseus]]) =
      ZStream
        .fromEffect(Promise.make[Any, Boolean])
        .flatMap(p =>
          Consumer
            .subscribeAnd[Any, String, Odysseus](Subscription.topics(topic))
            .plainStream
            .flattenChunks
            .mapM(committable =>
              (committable.offset.commit &> msg.set(Seq(committable.value))) *> p.complete(ZIO.succeed(true))
            )
            .interruptWhen(p)
        )

    val process = for {
      msg <- Ref.make(Seq.empty[Odysseus])
      p   <- produce.fork
      c   <- consume(msg).runDrain.fork
      _   <- p.join
      _   <- c.join
    } yield msg

    val result = runtime.unsafeRun(process.flatMap(_.get).provideSomeLayer(consumer ++ producer))

    assert(result.nonEmpty, "Message is empty!")
    assertEquals(result.head.message, words)
  }
}
