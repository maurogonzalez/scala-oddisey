package oddisey.caliban.api

import caliban.RootResolver
import caliban.GraphQL.graphQL
import caliban.interop.cats.implicits._

class Api extends ExampleApi {
  // RootResolver(Queries, Mutations, Subscription)
  val api = graphQL(RootResolver(exampleQueries))
}
