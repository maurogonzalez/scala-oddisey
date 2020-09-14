package oddisey.caliban.api

class ExampleApiSpec extends BaseApiSpec {
  test("sample query") {
    val query0 = """
                  |{
                  |  numbers
                  |  getNumber(id: 1)
                  |  user(id: 1)
                  |}
                  |""".stripMargin

    val query = """
                  |{
                  |  user(id: 18)
                  |}
                  |""".stripMargin

    val res = testRequest(query).unsafeRunSync()
    println(res)
  }

}
