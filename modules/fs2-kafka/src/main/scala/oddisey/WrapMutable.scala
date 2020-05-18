package oddisey

import cats.effect.Sync
import cats.syntax.flatMap._

abstract class WrapMutable[F[_], A](private val a: A)(implicit F: Sync[F]) {

  final def execute[B](f: A => B): F[B]     = F.delay(f(a))
  final def executeM[B](f: A => F[B]): F[B] = F.delay(f(a)).flatten

}
