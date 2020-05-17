package oddisey

import java.util.UUID

import cats.effect._
import fs2.kafka._

object KafkaClient {

  def kafkaAdmin[F[_]: ConcurrentEffect](host: String, port: Long)(implicit cs: ContextShift[F]) =
    adminClientResource(AdminClientSettings[F].withBootstrapServers(s"$host:$port"))

  def kafkaProducer[F[_]: ConcurrentEffect, K, V](
    host: String,
    port: Long,
    keySerializer: Serializer[F, K],
    valueSerializer: Serializer[F, V]
  )(implicit cs: ContextShift[F]): Resource[F, KafkaProducer[F, K, V]] =
    producerResource[F].using(producerSettings(host, port, keySerializer, valueSerializer))

  def kafkaConsumer[F[_]: ConcurrentEffect, K, V](
    host: String,
    port: Long,
    keyDeserializer: Deserializer[F, K],
    valueDeserializer: Deserializer[F, V],
    consumerGroup: String
  )(implicit cs: ContextShift[F], timer: Timer[F]): Resource[F, KafkaConsumer[F, K, V]] =
    consumerResource[F].using(consumerSettings(host, port, consumerGroup, keyDeserializer, valueDeserializer))

  private def producerSettings[F[_]: ConcurrentEffect, K, V](
    host: String,
    port: Long,
    keySerializer: Serializer[F, K],
    valueSerializer: Serializer[F, V]
  ) =
    ProducerSettings(
      keySerializer   = keySerializer,
      valueSerializer = valueSerializer
    ).withBootstrapServers(s"$host:$port")
      .withEnableIdempotence(true)

  private def consumerSettings[F[_]: ConcurrentEffect, K, V](
    host: String,
    port: Long,
    consumerGroup: String,
    keyDeserializer: Deserializer[F, K],
    valueDeserializer: Deserializer[F, V]
  ) =
    ConsumerSettings(
      keyDeserializer   = keyDeserializer,
      valueDeserializer = valueDeserializer
    ).withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(s"$host:$port")
      .withGroupId(consumerGroup)
      .withClientId(s"$consumerGroup-${UUID.randomUUID()}")
}
