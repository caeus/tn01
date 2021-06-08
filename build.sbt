import com.typesafe.sbt.packager.chmod

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"
val zioVersion = "1.0.9"
lazy val root = (project in file("."))
  .settings(
    name := "tn01",
    maintainer := "camilo.a.navas@gmail.com",
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt"             % "4.0.1",
      "dev.zio"          %% "zio"               % zioVersion,
      "dev.zio"          %% "zio-streams"       % zioVersion,
      "dev.zio"          %% "zio-test"          % zioVersion % "test",
      "dev.zio"          %% "zio-test-sbt"      % zioVersion % "test",
      "dev.zio"          %% "zio-test-magnolia" % zioVersion % "test" // option)
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.0"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-language:higherKinds",
      "-language:postfixOps",
      "-feature",
      "-Xfatal-warnings"
    ),
    stage := {
      val scriptName = executableScriptName.value
      val result = stage.value
      val appF   = result / "bin" / "app"
      IO.write(
        appF,
        s"""#!/usr/bin/env bash
          |BASEDIR=$$(dirname "$$0")
          |./$$BASEDIR/$scriptName -- $$@
          |""".stripMargin
      )
      appF.setExecutable(true)
      result
    }
  )
  .enablePlugins(JavaAppPackaging)
