name := "veranstaltungsplaner"

organization := "io.github.fachschaftsrat"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += "Bintray richard-w" at "https://dl.bintray.com/richard-w/maven"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-mailer" % "3.0.0-RC1",
  "xyz.wiedenhoeft" %% "play-authenticator" % "0.1.1",
  "com.typesafe.play" %% "play-mailer" % "3.0.0-RC1",
  "org.webjars" %% "webjars-play" % "2.4.0",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars" % "angularjs" % "1.3.15"
)

routesGenerator := InjectedRoutesGenerator

lazy val root = (project in file(".")).enablePlugins(PlayScala)

