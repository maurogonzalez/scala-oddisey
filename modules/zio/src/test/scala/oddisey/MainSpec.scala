package oddisey

//import scalaz._
//import syntax.traverse._
//import syntax.functor._
//import syntax.all._
//
//import std.option._, std.list._
import zio._

class MainSpec {
  val a = ZIO.effect("String")
  val b = ZIO.effect("String")

  a &> b &> UIO.apply("")
  val q = ZIO.collectAll(List(a, b, UIO.apply("")))
  q
}
