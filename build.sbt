name := "BitcoinMixer"

version := "1.0"

scalaVersion := "2.11.8"

lazy val root = (project in file(".")).
  enablePlugins(play.sbt.PlayScala)

libraryDependencies ++= Seq(
  "org.scalaj" %% "scalaj-http" % "1.1.4",
  "com.typesafe.play" %% "play-json" % "2.4.3"
)

fork in run := true
