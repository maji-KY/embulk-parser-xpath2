val embulkVersion = "0.8.35"

lazy val commonSettings = Seq(
  organization := "com.github.maji-KY",
  scalaVersion := "2.12.4",
  version := "CANNOT_RELEASE",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-value-discard"
  ),
  resolvers += Resolver.jcenterRepo,
  libraryDependencies ++= Seq(
    "org.embulk" % "embulk-core" % embulkVersion,
    "org.embulk" % "embulk-core" % embulkVersion classifier "tests",
    "junit" % "junit" % "4.+" % "test",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test"
  )
)

lazy val benchmark = (project in file("benchmark"))
  .aggregate(main)
  .settings(commonSettings)
  .dependsOn(main % "compile->test")
  .enablePlugins(JmhPlugin)

lazy val main = (project in file("."))
  .settings(commonSettings)

