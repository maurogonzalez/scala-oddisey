package oddisey.caliban.http

import scala.concurrent.ExecutionContext

import caliban.{ CalibanError, GraphQLInterpreter, Http4sAdapter }
import caliban.interop.cats.implicits._
import cats.effect.{ ContextShift, IO, Resource, Timer }
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import zio._

import oddisey.caliban.api.Api

class ApiHttpServer[R](implicit cs: ContextShift[IO], t: Timer[IO], runtime: Runtime[R]) extends Api {

  def server =
    for {
      interpreter <- Resource.liftF(api.interpreterAsync[IO])
      server <- BlazeServerBuilder[IO](ExecutionContext.global)
                 .bindHttp(8080, "localhost")
                 .withHttpApp(ApiHttpServer.routes(interpreter))
                 .resource
    } yield server
}

object ApiHttpServer {

  def routes[R](interpreter: GraphQLInterpreter[Any, CalibanError])(implicit runtime: Runtime[R]) =
    Router[IO](
      "/api/graphql" -> CORS(Http4sAdapter.makeHttpServiceF[IO, CalibanError](interpreter))
    ).orNotFound
}
