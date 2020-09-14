package oddisey.caliban

import cats.effect.{ IO, IOApp }
import zio._

import oddisey.caliban.http.ApiHttpServer

object Main extends IOApp {
  implicit val runtime = Runtime.default

  val server = new ApiHttpServer[ZEnv]

  override def run(args: List[String]): IO[cats.effect.ExitCode] =
    server.server.use(_ => IO.never.map(_ => cats.effect.ExitCode.Success))
}
