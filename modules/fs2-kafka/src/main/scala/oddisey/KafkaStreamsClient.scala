package oddisey

import java.util.Properties
import scala.concurrent.duration._

import cats.effect.{IO, Timer}
import cats.syntax.apply._
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}


class FKafkaStreams(streams: KafkaStreams)
  extends WrapMutable[KafkaStreams](streams) {

  def serve(implicit T: Timer[IO]): IO[Unit] =
    (cleanUp *> start *> waitWhileIsUp).map(_ => println("Running")).handleErrorWith(_ => close)

  def waitWhileIsUp(implicit T: Timer[IO]): IO[Unit] =
      fs2.Stream.repeatEval(IO.sleep(500.millis) *> isUp).evalTap(r => IO(println(s"IsUp: $r" )))
        .takeWhile(x => !x).compile.drain

  def isUp: IO[Boolean] = status.map(s => s.isRunningOrRebalancing || s == KafkaStreams.State.CREATED)

  def cleanUp: IO[Unit]              = execute(_.cleanUp())
  def start: IO[Unit]                = execute(_.start)
  def close: IO[Unit]                = execute(_.close)
  def status: IO[KafkaStreams.State] = execute(_.state())

}
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
