package oddisey

import java.util.Properties

import scala.concurrent.duration._
import scala.util.control.NonFatal

import cats.effect.{ ContextShift, IO, Timer }
import cats.syntax.apply._
import cats.syntax.flatMap._
import fs2.concurrent.Queue
import org.apache.kafka.streams.{ KafkaStreams, StreamsConfig, Topology }

class FKafkaStreams(streams: KafkaStreams)(implicit cs: ContextShift[IO]) extends WrapMutable[KafkaStreams](streams) {

  val queue = Queue.bounded[IO, Throwable](1024)

  def serve(implicit T: Timer[IO]): IO[Queue[IO, Throwable]] =
    for {
      _ <- cleanUp
      q <- queue.flatTap(q => IO(streams.setUncaughtExceptionHandler(threadHandler(q))))
      _ <- start
      _ <- waitWhileIsUp
    } yield q

  def waitWhileIsUp(implicit T: Timer[IO]): IO[Unit] =
    fs2.Stream
      .repeatEval(IO.sleep(500.millis) *> isUp)
      .evalTap(r => IO(println(s"IsUp: $r")))
      .takeWhile(x => !x)
      .compile
      .drain

  def isUp: IO[Boolean] = status.map { s =>
    println(s"State is: $s")
    s.isRunningOrRebalancing || s == KafkaStreams.State.CREATED
  }

  def cleanUp: IO[Unit]              = execute(_.cleanUp())
  def start: IO[Unit]                = execute(_.start)
  def close: IO[Unit]                = execute(_.close)
  def status: IO[KafkaStreams.State] = execute(_.state())

  def threadHandler(q: Queue[IO, Throwable]) = new Thread.UncaughtExceptionHandler() {

    override def uncaughtException(th: Thread, ex: Throwable): Unit =
      ex match {
        case NonFatal(nonFatal) =>
          q.enqueue1(nonFatal)
            .runAsync {
              case Left(l) => IO(println(s"Error enqueueing: $l"))
              case _       => IO.unit
            }
            .unsafeRunSync()
        case e => println("Uncaught fatal exception: " + ex)
      }
  }
}

object FKafkaStreams {
  def apply(streams: KafkaStreams)(implicit cs: ContextShift[IO]): FKafkaStreams = new FKafkaStreams(streams)
}

object KafkaStreamsClient {

  import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler

  private def propsBuilder(host: String, port: Int, applicationId: String) = {
    val props = new Properties
    props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, s"$host:$port")
    props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, applicationId)
    // Caution: this interval can cause duplication
    props.setProperty(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "100")
    // Requires more than 3 brokers or set some configs for single node in development
    // props.setProperty(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE)
    props
  }

  def stream(host: String, port: Int, topology: Topology, applicationId: String) =
    new KafkaStreams(topology, propsBuilder(host, port, applicationId))

}
