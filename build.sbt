val embulkVersion = "0.9.12"

lazy val commonSettings = Seq(
  organization := "com.github.maji-KY",
  scalaVersion := "2.12.8",
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
    "com.ximpleware" % "vtd-xml" % "2.13.4",
    "org.embulk" % "embulk-core" % embulkVersion,
    "org.embulk" % "embulk-core" % embulkVersion classifier "tests",
    "junit" % "junit" % "4.12" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
)

lazy val benchmark = (project in file("benchmark"))
  .aggregate(main)
  .settings(commonSettings)
  .dependsOn(main % "compile->test")
  .enablePlugins(JmhPlugin)

lazy val main = (project in file("."))
  .settings(commonSettings)

