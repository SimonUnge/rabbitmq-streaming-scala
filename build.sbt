name := "rabbitmq-streaming-scala"

version := "0.1.0"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % "1.0.2",
  "ch.qos.logback" % "logback-classic" % "1.4.14",
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)
