package oddisey

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import cats.effect.{ ContextShift, IO, Timer }
import munit.FunSuite

trait KafkaBaseSpec extends FunSuite {
  val host = "localhost"
  val port = 9092

  val ec: ExecutionContext                      = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val ioTimer: Timer[IO]               = IO.timer(ec)
}
