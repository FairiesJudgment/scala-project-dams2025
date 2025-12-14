val scala3Version = "3.3.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "whisper-jar",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"      % "2.1.1",
      "dev.zio" %% "zio-http" % "3.0.0-RC6",
      "dev.zio" %% "zio-json" % "0.6.2",
      // test dependencies
      "dev.zio" %% "zio-test" % "2.1.1" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.1" % Test
    )
  )