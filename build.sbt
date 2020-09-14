lazy val `fs2-kafka` = project
  .in(file("modules/fs2-kafka"))
  .settings(
    commonSettings,
    name := "kafka",
    libraryDependencies ++= Seq(
      Libraries.`cats-retry`,
      Libraries.decline,
      Libraries.log4cats,
      Libraries.logback,
      Libraries.`fs2-kafka`,
      Libraries.redis4cats,
      Libraries.`redis4cats-logs`,
      "org.apache.kafka"   %% "kafka-streams-scala" % "2.5.0",
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
      scalapb.gen(grpc = false) -> (sourceManaged in Compile).value
    ),
    PB.protocOptions in Compile := Seq(
      "--descriptor_set_out=" +
        (baseDirectory in Compile).value.getParentFile / "grpc" / "src" / "main" / "resources" /"out.desc"
    ),
    name := "grpc"
  )

lazy val caliban = project
  .in(file("modules/caliban"))
  .settings(
    commonSettings,
    name := "caliban",
    libraryDependencies ++= Seq(
      "com.github.ghostdogpr" %% "caliban" % "0.9.1",
       "com.github.ghostdogpr" %% "caliban-http4s"     % "0.9.1",
      "com.github.ghostdogpr" %% "caliban-cats"     % "0.9.1",
      Libraries.`cats-retry`,
      Libraries.decline,
      Libraries.log4cats,
      Libraries.logback,
      Libraries.`fs2-kafka`,
      Libraries.redis4cats,
      Libraries.`redis4cats-logs`,
      "org.http4s"          %% "http4s-blaze-client"   % Libraries.Version.http4s  % Test,
      Libraries.Test.munit % Test
    )
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
