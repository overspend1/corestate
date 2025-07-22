name := "analytics-engine"
version := "2.0.0"
scalaVersion := "2.12.15"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % "3.3.1",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.3.1",
  "org.apache.spark" %% "spark-mllib" % "3.3.1"
  // Add other connectors like InfluxDB, Delta Lake, etc. as needed
)