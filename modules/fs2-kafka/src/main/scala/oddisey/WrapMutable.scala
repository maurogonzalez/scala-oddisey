package oddisey

import cats.effect.IO
import cats.syntax.flatMap._

abstract class WrapMutable[A](private val a: A) {

  final def execute[B](f: A => B): IO[B]      = IO(f(a))
  final def executeM[B](f: A => IO[B]): IO[B] = IO(f(a)).flatten

}
