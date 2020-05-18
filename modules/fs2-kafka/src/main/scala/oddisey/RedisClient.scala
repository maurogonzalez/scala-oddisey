package oddisey

import cats.effect.{ ContextShift, IO, Resource }
import dev.profunktor.redis4cats.connection.{ RedisURI, RedisClient => RClient }
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import dev.profunktor.redis4cats.Redis
import io.chrisdavenport.log4cats.Logger

object RedisClient {

  def redisStandalone[K, V](host: String, password: Option[String])(
    implicit
    codec: RedisCodec[K, V],
    CS: ContextShift[IO],
    L: Logger[IO]
  ) =
    for {
      uri    <- Resource.liftF(RedisURI.make[IO](s"redis://$host"))
      client <- RClient[IO](uri)
      redis  <- Redis[IO].fromClient(client, codec)
    } yield redis

  val stringCodec: RedisCodec[String, String] = RedisCodec.Utf8
}
