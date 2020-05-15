package oddisey

import java.util.Properties

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.KStream
import org.apache.kafka.streams.{ KafkaStreams, StreamsConfig, Topology }

object KafkaStreamsClient {

  private def propsBuilder(host: String, port: Int, applicationId: String) = {
    val props = new Properties
    props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, s"$host:$port")
    props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, applicationId)
    props
  }

  def stream(host: String, port: Int, topology: Topology, applicationId: String) =
    new KafkaStreams(topology, propsBuilder(host, port, applicationId))

}
