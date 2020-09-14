package oddisey.caliban.api

import cats.effect.IO
import zio.query.{ CompletedRequestMap, DataSource, Request, ZQuery }
import zio.Task

class ExampleApi {

  val exampleQueries = Queries(
    List(1, 2, 3, 4, 5),
    args => IO.pure(args.id),
    args => getUserNameById(args.id)
  )

  case class Queries(
    numbers: List[Int],
    getNumber: Args => IO[Int],
    user: UserArgs => ZQuery[Any, Throwable, String]
  )
  case class Args(id: Int)
  case class UserArgs(id: Int)

  def getUserNameById(id: Int): ZQuery[Any, Throwable, String] =
    ZQuery.fromRequest(GetUserName(id))(UserDataSource)

  case class GetUserName(id: Int) extends Request[Throwable, String]

  lazy val UserDataSource: DataSource[Any, GetUserName] = DataSource.Batched.make("UserDataSource") { requests =>
    val resultMap = CompletedRequestMap.empty
    requests.toList match {
      case request :: Nil =>
        val result: Task[String] = Task(s"Name${request.id}")
        result.either.map(resultMap.insert(request))
      case batch =>
        val result: Task[List[(Int, String)]] = Task(batch.map(req => req.id -> s"Name${req.id}"))
        result.fold(
          err => requests.foldLeft(resultMap) { case (map, req) => map.insert(req)(Left(err)) },
          _.foldLeft(resultMap) { case (map, (id, name)) => map.insert(GetUserName(id))(Right(name)) }
        )
    }
  }

}
