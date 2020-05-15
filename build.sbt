lazy val `fs2-kafka` = project
  .in(file("modules/fs2-kafka"))
  .settings(
    commonSettings,
    name := "kafka",
    libraryDependencies ++= Seq(
      Libraries.`cats-retry`,
      Libraries.decline,
      Libraries.`fs2-kafka`,
      "org.apache.kafka" %% "kafka-streams-scala" % "2.5.0",
      Libraries.Test.munit % Test
    )
  )
  .dependsOn(
    grpc % "compile->compile;test->test"
  )

lazy val `zio-kafka` = project
  .in(file("modules/zio-kafka"))
  .settings(
    commonSettings,
    name := "zio-kafka",
    libraryDependencies ++= Seq(
      Libraries.`zio-kafka`,
      Libraries.`zio-streams`,
      Libraries.Test.munit % Test
    )
  )
  .dependsOn(
    grpc % "compile->compile;test->test"
  )

lazy val grpc = project
  .in(file("modules/grpc"))
  .settings(
    commonSettings,
    PB.targets in Compile := Seq(
      scalapb.gen(grpc=false) -> (sourceManaged in Compile).value
    ),
    name := "grpc"
  )

lazy val commonSettings = Seq(
  organization := "oddisey",
  scalaVersion := "2.13.1",
  scalacOptions in Compile ++= Seq(
    "-feature",
    "-unchecked",
    "-deprecation"
  ),
  testFrameworks += new TestFramework("munit.Framework")
)
