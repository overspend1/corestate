package streaming

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}
import org.apache.spark.sql.types._
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.clustering.KMeansModel
import com.typesafe.scalalogging.LazyLogging
import models.AnomalyDetector

/**
 * Backup Analytics Engine
 *
 * Real-time stream processing for backup events using Spark Structured Streaming.
 * Processes events from Kafka, computes windowed aggregations, detects anomalies,
 * and writes results to various sinks (Parquet data lake, InfluxDB, Elasticsearch).
 */
object BackupAnalyticsEngine extends LazyLogging {

  // Schema for backup events from Kafka
  val backupEventSchema: StructType = StructType(Array(
    StructField("eventId", StringType, nullable = false),
    StructField("eventTime", TimestampType, nullable = false),
    StructField("tenantId", StringType, nullable = false),
    StructField("backupType", StringType, nullable = false),
    StructField("status", StringType, nullable = false),
    StructField("size", LongType, nullable = false),
    StructField("compressedSize", LongType, nullable = false),
    StructField("duration", DoubleType, nullable = false),
    StructField("fileCount", IntegerType, nullable = false),
    StructField("chunkCount", IntegerType, nullable = false),
    StructField("deduplicationRatio", DoubleType, nullable = false),
    StructField("compressionRatio", DoubleType, nullable = false),
    StructField("throughput", DoubleType, nullable = false),
    StructField("errorMessage", StringType, nullable = true)
  ))

  def startAnalyticsPipeline(spark: SparkSession): Unit = {
    logger.info("Starting Backup Analytics Pipeline...")

    import spark.implicits._

    // Read backup events from Kafka
    val backupEvents = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka-cluster:9092"))
      .option("subscribe", "backup-events")
      .option("startingOffsets", "latest")
      .option("maxOffsetsPerTrigger", "10000")
      .option("failOnDataLoss", "false")
      .load()

    logger.info("Connected to Kafka stream: backup-events")

    // Parse JSON events
    val parsedEvents = backupEvents
      .selectExpr("CAST(value AS STRING) as json_string")
      .select(from_json($"json_string", backupEventSchema).as("data"))
      .select("data.*")
      .withColumn("date", to_date($"eventTime"))
      .withColumn("hour", hour($"eventTime"))

    // 1. Real-time Statistics - 5-minute sliding windows
    val realtimeStats = parsedEvents
      .withWatermark("eventTime", "10 minutes")
      .groupBy(
        window($"eventTime", "5 minutes", "1 minute"),
        $"tenantId",
        $"backupType"
      )
      .agg(
        count("*").as("backup_count"),
        sum(when($"status" === "success", 1).otherwise(0)).as("successful_count"),
        sum(when($"status" === "failed", 1).otherwise(0)).as("failed_count"),
        avg("size").as("avg_size"),
        sum("size").as("total_size"),
        avg("duration").as("avg_duration"),
        avg("throughput").as("avg_throughput"),
        avg("compressionRatio").as("avg_compression_ratio"),
        avg("deduplicationRatio").as("avg_dedup_ratio"),
        min("duration").as("min_duration"),
        max("duration").as("max_duration"),
        stddev("duration").as("stddev_duration")
      )
      .withColumn("success_rate", $"successful_count" / $"backup_count" * 100)

    // 2. Anomaly Detection Stream
    val anomalyDetector = new AnomalyDetector()

    // Create feature vector for anomaly detection
    val featuresDF = parsedEvents
      .select(
        $"eventId",
        $"eventTime",
        $"tenantId",
        $"backupType",
        $"size",
        $"duration",
        $"throughput",
        $"compressionRatio",
        $"deduplicationRatio",
        $"fileCount"
      )

    val assembler = new VectorAssembler()
      .setInputCols(Array("size", "duration", "throughput", "compressionRatio", "deduplicationRatio", "fileCount"))
      .setOutputCol("features")

    val featureVector = assembler.transform(featuresDF)

    // Detect anomalies
    val anomalies = featureVector
      .withColumn("anomaly_score", anomalyDetector.detectAnomalyUDF($"features", $"tenantId"))
      .filter($"anomaly_score" > 0.8) // High anomaly threshold
      .select(
        $"eventId".as("id"),
        $"eventTime".as("timestamp"),
        $"tenantId",
        $"backupType",
        $"anomaly_score".as("anomalyScore"),
        lit("Statistical outlier detected").as("reason")
      )
      .withColumn("date", to_date($"timestamp"))

    // 3. Write real-time stats to console (for debugging)
    val consoleQuery = realtimeStats
      .writeStream
      .outputMode("append")
      .format("console")
      .option("truncate", false)
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .queryName("console-stats")
      .start()

    logger.info("Console statistics stream started")

    // 4. Write stats to Parquet data lake
    val parquetQuery = realtimeStats
      .writeStream
      .outputMode("append")
      .format("parquet")
      .option("path", "s3a://corestate-data-lake/realtime-stats")
      .option("checkpointLocation", "s3a://corestate-data-lake/checkpoints/realtime-stats")
      .partitionBy("tenantId", "backupType")
      .trigger(Trigger.ProcessingTime("1 minute"))
      .queryName("parquet-stats")
      .start()

    logger.info("Parquet data lake stream started")

    // 5. Write raw events to Parquet data lake
    val eventsQuery = parsedEvents
      .writeStream
      .outputMode("append")
      .format("parquet")
      .option("path", "s3a://corestate-data-lake/backup-events")
      .option("checkpointLocation", "s3a://corestate-data-lake/checkpoints/backup-events")
      .partitionBy("date", "tenantId")
      .trigger(Trigger.ProcessingTime("2 minutes"))
      .queryName("parquet-events")
      .start()

    logger.info("Backup events data lake stream started")

    // 6. Write anomalies to Parquet
    val anomaliesQuery = anomalies
      .writeStream
      .outputMode("append")
      .format("parquet")
      .option("path", "s3a://corestate-data-lake/anomalies")
      .option("checkpointLocation", "s3a://corestate-data-lake/checkpoints/anomalies")
      .partitionBy("date", "tenantId")
      .trigger(Trigger.ProcessingTime("1 minute"))
      .queryName("parquet-anomalies")
      .start()

    logger.info("Anomaly detection stream started")

    // 7. Write stats to InfluxDB for time-series monitoring
    val influxQuery = realtimeStats
      .writeStream
      .outputMode("append")
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        logger.debug(s"Processing batch $batchId for InfluxDB")
        writeToInfluxDB(batchDF)
      }
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .queryName("influx-stats")
      .start()

    logger.info("InfluxDB time-series stream started")

    // 8. Write anomalies to alerting system
    val alertQuery = anomalies
      .writeStream
      .outputMode("append")
      .foreachBatch { (batchDF: DataFrame, batchId: Long) =>
        logger.info(s"Processing anomaly batch $batchId")
        batchDF.collect().foreach { row =>
          sendAnomalyAlert(row)
        }
      }
      .trigger(Trigger.ProcessingTime("15 seconds"))
      .queryName("alert-stream")
      .start()

    logger.info("Anomaly alerting stream started")

    logger.info("All streaming queries started successfully")

    // Monitor stream health
    val monitorThread = new Thread(() => {
      while (true) {
        try {
          Thread.sleep(60000) // Check every minute
          val activeQueries = spark.streams.active
          logger.info(s"Active streaming queries: ${activeQueries.length}")
          activeQueries.foreach { query =>
            val status = query.status
            logger.info(s"Query ${query.name}: ${status.message}, IsActive: ${query.isActive}")
          }
        } catch {
          case _: InterruptedException => return
          case e: Exception => logger.error("Stream monitor error", e)
        }
      }
    })
    monitorThread.setName("stream-monitor")
    monitorThread.setDaemon(true)
    monitorThread.start()

    // Wait for all queries
    spark.streams.awaitAnyTermination()
  }

  /**
   * Write batch to InfluxDB for real-time monitoring
   */
  private def writeToInfluxDB(df: DataFrame): Unit = {
    try {
      // Convert DataFrame to InfluxDB line protocol and write
      // This is a simplified version - real implementation would use InfluxDB client
      df.collect().foreach { row =>
        val measurement = "backup_stats"
        val tags = s"tenant_id=${row.getAs[String]("tenantId")},backup_type=${row.getAs[String]("backupType")}"
        val fields = s"backup_count=${row.getAs[Long]("backup_count")}," +
          s"avg_size=${row.getAs[Double]("avg_size")}," +
          s"avg_duration=${row.getAs[Double]("avg_duration")}," +
          s"success_rate=${row.getAs[Double]("success_rate")}"

        logger.debug(s"InfluxDB: $measurement,$tags $fields")
      }
    } catch {
      case e: Exception =>
        logger.error("Failed to write to InfluxDB", e)
    }
  }

  /**
   * Send anomaly alert (webhook, email, Slack, etc.)
   */
  private def sendAnomalyAlert(row: org.apache.spark.sql.Row): Unit = {
    try {
      val eventId = row.getAs[String]("id")
      val timestamp = row.getAs[java.sql.Timestamp]("timestamp")
      val tenantId = row.getAs[String]("tenantId")
      val backupType = row.getAs[String]("backupType")
      val anomalyScore = row.getAs[Double]("anomalyScore")

      val alertMessage = s"Anomaly Detected!\n" +
        s"Event ID: $eventId\n" +
        s"Timestamp: $timestamp\n" +
        s"Tenant: $tenantId\n" +
        s"Backup Type: $backupType\n" +
        s"Anomaly Score: ${anomalyScore * 100}%"

      logger.warn(alertMessage)

      // TODO: Integrate with actual alerting systems:
      // - Webhook to notification service
      // - Email via SMTP
      // - Slack/Teams integration
      // - PagerDuty for critical alerts
    } catch {
      case e: Exception =>
        logger.error("Failed to send anomaly alert", e)
    }
  }
}
