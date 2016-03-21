name := "BitcoinMixer"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"

  Seq(
    "org.scalaj" %% "scalaj-http" % "1.1.4",
    "com.typesafe.play" %% "play-json" % "2.4.3",
    "io.spray"            %%  "spray-can"     % sprayV,
    "io.spray"            %%  "spray-routing" % sprayV,
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test"
  )
}

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

herokuAppName in Compile := "BitcoinMixer"

fork in run := true
