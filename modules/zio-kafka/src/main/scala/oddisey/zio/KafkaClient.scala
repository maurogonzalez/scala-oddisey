package oddisey.zio

import izumi.reflect.Tags.Tag
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer._
import zio.kafka.producer.{ Producer, ProducerSettings }
import zio.kafka.serde.{ Deserializer, Serde, Serializer }

object KafkaClient {
  FiberRef.make(0, math.max)

  def kafkaProducer[V: Tag](
    host: String,
    port: Long,
    serializer: Serializer[Any, V]
  ): ZLayer[Any, Throwable, Producer[Any, String, V]] = Producer.make(
    producerSettings(host, port),
    Serde.string,
    serializer
  )

  def kafkaConsumer[V: Tag](
    host: String,
    port: Long,
    consumerGroup: String,
    deserializer: Deserializer[Any, V]
  ): ZLayer[Clock with Blocking, Throwable, Consumer[Any, String, V]] = Consumer.make(
    consumerSettings(host, port, consumerGroup),
    Serde.string,
    deserializer
  )

  private def producerSettings[R, V](host: String, port: Long) =
    ProducerSettings(List(s"$host:$port"))

  private def consumerSettings[R, V](
    host: String,
    port: Long,
    consumerGroup: String
  ) =
    ConsumerSettings(List(s"$host:$port"))
      .withGroupId(consumerGroup)
      .withOffsetRetrieval(Consumer.OffsetRetrieval.Auto(Consumer.AutoOffsetStrategy.Earliest))
}
