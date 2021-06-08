ThisBuild / scalaVersion := "2.13.5"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"
val zioVersion = "1.0.9"
lazy val root = (project in file("."))
  .settings(
    name := "tn01",
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
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
