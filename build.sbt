name := """cardsite"""
organization := "ca.geofroy.nick"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.5"

libraryDependencies += guice
libraryDependencies += ws
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "com.github.jwt-scala" %% "jwt-play-json" % "7.1.3"
libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % "7.1.3"

libraryDependencies ++= Seq(
  "com.auth0" % "jwks-rsa" % "0.17.1",
)

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.11.0"

libraryDependencies ++= Seq(
  jdbc,
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.2.3"
)

javaOptions += "-Djdk.tls.client.protocols=TLSv1.2"
// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "ca.geofroy.nick.binders._"
