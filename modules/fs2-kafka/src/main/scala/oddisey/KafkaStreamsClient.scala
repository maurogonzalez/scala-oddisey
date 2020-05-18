package oddisey

import java.util.Properties

import scala.concurrent.duration._
import scala.util.control.NonFatal
import cats.effect.{ ContextShift, IO, Timer }
import cats.effect.concurrent.MVar
import cats.syntax.apply._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.{ KafkaStreams, StreamsConfig, Topology }

class FKafkaStreams(streams: KafkaStreams)(implicit T: Timer[IO], CS: ContextShift[IO])
    extends WrapMutable[IO, KafkaStreams](streams) {

  def serve: IO[Unit] =
    for {
      logger <- Slf4jLogger.create[IO]
      _      <- cleanUp
      q      <- MVar[IO].empty[Throwable]
      _      <- IO.ioEffect.catchNonFatal(streams.setUncaughtExceptionHandler(threadHandler(logger, q)))
      _      <- start
      _      <- waitWhileIsUp(logger)
      _      <- handler(q)
    } yield ()

  private def handler(q: MVar[IO, Throwable])(implicit T: Timer[IO]): IO[Unit] = q.tryTake.flatMap {
    case Some(err) => IO.raiseError(err)
    case None      => IO.sleep(100.millis) *> handler(q)
  }

  def waitWhileIsUp(logger: Logger[IO])(implicit T: Timer[IO]): IO[Unit] =
    fs2.Stream
      .repeatEval(IO.sleep(500.millis) *> isUp)
      .interruptAfter(5.seconds)
      .takeWhile(x => !x)
      .evalTap(_ => logger.debug(s"Stream is UP!"))
      .compile
      .drain

  def isUp: IO[Boolean] = status.map { s =>
    s.isRunningOrRebalancing || s == KafkaStreams.State.CREATED
  }

  def cleanUp: IO[Unit]              = execute(_.cleanUp())
  def start: IO[Unit]                = execute(_.start)
  def close: IO[Unit]                = execute(_.close)
  def status: IO[KafkaStreams.State] = execute(_.state())

  def threadHandler(logger: Logger[IO], q: MVar[IO, Throwable]) = new Thread.UncaughtExceptionHandler() {

    override def uncaughtException(th: Thread, ex: Throwable): Unit =
      ex match {
        case NonFatal(nonFatal) =>
          q.put(nonFatal)
            .runAsync {
              case Left(err) => logger.error(err)("Thread.UncaughtExceptionHandler")
              case _         => IO.unit
            }
            .unsafeRunSync()
        case e =>
          sys.error(e.getMessage)
          sys.exit()
      }
  }
}

object FKafkaStreams {

  def apply(streams: KafkaStreams)(implicit T: Timer[IO], CS: ContextShift[IO]): FKafkaStreams =
    new FKafkaStreams(streams)
}

object KafkaStreamsClient {

  import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler

  private def propsBuilder(host: String, port: Int, applicationId: String) = {
    val props = new Properties
    props.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, s"$host:$port")
    props.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, applicationId)
    // Exception Handlers
    props.setProperty(
      StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
      classOf[LogAndContinueExceptionHandler].getName
    )
    // Caution: this interval can cause duplication
    props.setProperty(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "100")
    props.setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
    // Requires more than 3 brokers or set some configs for single node in development
//     props.setProperty(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE)
    props
  }

  def stream(host: String, port: Int, topology: Topology, applicationId: String) =
    new KafkaStreams(topology, propsBuilder(host, port, applicationId))

}
