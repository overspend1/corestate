name := "analytics-engine"
version := "2.0.0"
scalaVersion := "2.12.15"

val sparkVersion = "3.4.1"
val akkaVersion = "2.8.5"
val circeVersion = "0.14.6"

libraryDependencies ++= Seq(
  // Spark core
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "org.apache.spark" %% "spark-mllib" % sparkVersion,
  "org.apache.spark" %% "spark-streaming" % sparkVersion,
  
  // Akka for actor-based processing
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.5.3",
  
  // JSON processing
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  
  // Database connectors
  "org.postgresql" % "postgresql" % "42.6.0",
  "com.datastax.spark" %% "spark-cassandra-connector" % "3.4.1",
  "org.elasticsearch" %% "elasticsearch-spark-30" % "8.9.0",
  
  // Monitoring and metrics
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.16.0",
  
  // Configuration
  "com.typesafe" % "config" % "1.4.2",
  
  // Logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.4.11",
  
  // Time series databases
  "org.influxdb" % "influxdb-java" % "2.23",
  
  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
)

// Assembly plugin for fat JAR creation
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "application.conf" => MergeStrategy.concat
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint"
)

// Test options
Test / parallelExecution := false
Test / fork := true