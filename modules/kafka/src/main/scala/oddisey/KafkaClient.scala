package oddisey

import cats.effect._
import fs2.kafka._

object KafkaClient {

  def kafkaProducer[F[_]: ConcurrentEffect, V](
    host: String,
    port: Long,
    serializer: Serializer[F, V]
  )(implicit cs: ContextShift[F]): Resource[F, KafkaProducer[F, String, V]] =
    producerResource[F].using(producerSettings(host, port, serializer))

  def kafkaConsumer[F[_]:ConcurrentEffect, V](
    host: String,
    port: Long,
    deserializer: Deserializer[F, V],
    consumerGroup: String
  )(implicit cs: ContextShift[F], timer: Timer[F]): Resource[F, KafkaConsumer[F, String, V]] =
    consumerResource[F].using(consumerSettings(host, port, consumerGroup, deserializer))

  private def producerSettings[F[_]:ConcurrentEffect, V](host: String, port: Long, serializer: Serializer[F, V]) =
    ProducerSettings(
      keySerializer   = Serializer[F, String],
      valueSerializer = serializer
    ).withBootstrapServers(s"$host:$port")

  private def consumerSettings[F[_]:ConcurrentEffect, V](
    host: String,
    port: Long,
    consumerGroup: String,
    deserializer: Deserializer[F, V]
  ) =
    ConsumerSettings(
      keyDeserializer   = Deserializer[F, String],
      valueDeserializer = deserializer
    ).withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"$host:$port")
      .withGroupId(consumerGroup)
}
