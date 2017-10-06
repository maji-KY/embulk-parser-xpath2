lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.maji-KY",
      scalaVersion := "2.12.3",
      version := "0.0.1-SNAPSHOT"
    )),
    name := "embulk-parser-xpath2",
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-value-discard"
    )
  )

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Dependencies.value
