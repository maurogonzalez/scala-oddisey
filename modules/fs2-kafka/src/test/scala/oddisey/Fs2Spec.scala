package oddisey

import scala.concurrent.duration._

import cats.effect.IO
import fs2.concurrent.{ Queue, SignallingRef }
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger

class Fs2Spec extends KafkaBaseSpec {

  val logger = Slf4jLogger.create[IO]

  test("fs2 Queue") {
    val queue: IO[Queue[IO, String]] = Queue.bounded[IO, String](1)

    def printQ(q: Queue[IO, String], l: Logger[IO]): fs2.Stream[IO, Unit] = q.dequeue.evalMap(msg =>
      ioTimer.sleep(1.second) *> l.info(s"Message is: $msg")
    )

    val app = for {
      l <- logger
      q <- queue
      s <- SignallingRef[IO, Boolean](false)
      p <- printQ(q, l).interruptWhen(s).compile.drain.start
      _ <- q.enqueue1("first")
      _ <- fs2.Stream.emit("zero")
        .covary[IO]
        .through(q.enqueue)
        .compile
        .drain
      _ <- ioTimer.sleep(2500.millis) *> s.set(true)
      _ <- p.join
      _ <- l.info("End consuming")
    } yield ()

    app.unsafeRunSync()
  }

}
