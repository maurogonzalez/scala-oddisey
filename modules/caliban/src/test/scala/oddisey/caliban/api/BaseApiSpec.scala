package oddisey.caliban.api

import scala.concurrent.ExecutionContext

import caliban.interop.cats.implicits._
import cats.effect.IO
import io.circe.Json
import org.http4s.{ headers, MediaType, Method, Uri }
import org.http4s.client.dsl.io._
import org.http4s.circe._
import zio.Runtime

import oddisey.caliban.http.ApiHttpServer

trait BaseApiSpec extends munit.FunSuite {
  val api                   = new Api
  implicit val runtime      = Runtime.default
  implicit val contextShift = IO.contextShift(ExecutionContext.global)
  implicit val timer        = IO.timer(ExecutionContext.global)

  val interpreter = api.api.interpreterAsync[IO].unsafeRunSync()
  val router      = ApiHttpServer.routes(interpreter)

  def testRequest(body: String) = {
    val payload = Json.obj(
      "query"     -> Json.fromString(body),
      "variables" -> Json.Null
    )

    val req = Method.POST(payload, Uri.unsafeFromString("/api/graphql")).unsafeRunSync()
    router.run(req).attempt.flatMap {
      case Left(e) => IO.raiseError[Json](new Exception(e.getMessage))
      case Right(v) if v.contentType.contains(headers.`Content-Type`(MediaType.application.json)) =>
        IO(println(v)) *> v.as[Json]
      case Right(v) => IO.raiseError[Json](new Exception(v.status.reason))
    }
  }
}
