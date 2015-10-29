name := "veranstaltungsplaner"

organization := "io.github.fachschaftsrat"

version := "0.3-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-mailer" % "3.0.0-RC1",
  "xyz.wiedenhoeft" %% "play-authenticator" % "0.4-SNAPSHOT",
  "com.typesafe.play" %% "play-mailer" % "3.0.0-RC1",
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars" % "angularjs" % "1.3.15"
)

routesGenerator := InjectedRoutesGenerator

lazy val root = (project in file(".")).enablePlugins(PlayScala)

