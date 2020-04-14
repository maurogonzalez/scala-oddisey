import sbt._

object Libraries {
  val `cats-core`   = "org.typelevel"     %% "cats-core"      % Version.cats
  val `cats-effect` = "org.typelevel"     %% "cats-effect"    % Version.catsEffect
  val `cats-retry`  = "com.github.cb372"  %% "cats-retry"     % Version.catsRetry
  val decline       = "com.monovore"      %% "decline-effect" % Version.decline
  val `fs2-kafka`   = "com.github.fd4s"   %% "fs2-kafka"      % Version.fs2Kafka
  val log4cats      = "io.chrisdavenport" %% "log4cats-slf4j" % Version.log4cats
  val logback       = "ch.qos.logback"    % "logback-classic" % Version.logback
  val `zio-kafka`   = "dev.zio"           %% "zio-kafka"      % Version.zioKafka
  val `zio-streams` = "dev.zio"           %% "zio-streams"    % Version.zioStreams


  object Test {
    val munit = "org.scalameta" %% "munit" % Version.munit
  }

  object Compiler {
    val `better-monadic-for` = "com.olegpy"    %% "better-monadic-for" % Version.betterMonadicFor
    val `kind-projector`     = "org.typelevel" %% "kind-projector"     % Version.kindProjector
  }

  object Version {
    val betterMonadicFor = "0.3.1"
    val kindProjector    = "0.10.3"
    val cats             = "2.1.0"
    val catsEffect       = "2.1.2"
    val catsRetry        = "1.1.0"
    val decline          = "1.0.0"
    val fs2Kafka         = "1.0.0"
    val grpc             = "1.26.0"
    val http4s           = "0.21.0-M6"
    val log4cats         = "1.0.1"
    val logback          = "1.2.3"
    val munit            = "0.4.3"
    val zioKafka         = "0.7.0"
    val zioStreams       = "1.0.0-RC18-2"
  }
}
