lazy val kafka = project
  .in(file("modules/kafka"))
  .settings(
    commonSettings,
    name := "kafka",
    libraryDependencies ++= Seq(
      Libraries.`cats-retry`,
      Libraries.decline,
      Libraries.`fs2-kafka`,
      Libraries.Test.munit % Test
    )
  )
  .dependsOn(
    grpc % "compile->compile;test->test"
  )

lazy val grpc = project
  .in(file("modules/grpc"))
  .enablePlugins(Fs2Grpc)
  .settings(
    commonSettings,
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