package services

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import com.typesafe.scalalogging.LazyLogging
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

case class BackupSummary(
  totalBackups: Long,
  successfulBackups: Long,
  failedBackups: Long,
  totalDataSize: Long,
  avgBackupDuration: Double,
  avgBackupSize: Double,
  timestamp: String
)

case class BackupTrend(
  date: String,
  backupCount: Long,
  totalSize: Long,
  avgDuration: Double,
  successRate: Double
)

case class Anomaly(
  id: String,
  timestamp: String,
  tenantId: String,
  backupType: String,
  anomalyScore: Double,
  reason: String,
  details: Map[String, Any]
)

case class StorageUsage(
  tenantId: Option[String],
  date: String,
  totalSize: Long,
  backupCount: Long,
  avgBackupSize: Long
)

case class PerformanceStats(
  metric: String,
  value: Double,
  unit: String,
  timestamp: String
)

/**
 * Aggregation Service for backup analytics
 *
 * Provides methods for computing aggregated statistics, trends,
 * and insights from backup event data stored in the data lake.
 */
class AggregationService(spark: SparkSession) extends LazyLogging {

  import spark.implicits._

  private val backupEventsPath = "s3a://corestate-data-lake/backup-events"
  private val anomaliesPath = "s3a://corestate-data-lake/anomalies"

  /**
   * Get backup summary for a given time window
   */
  def getBackupSummary(tenantId: Option[String], hoursBack: Int): BackupSummary = {
    logger.info(s"Computing backup summary for tenant $tenantId, last $hoursBack hours")

    val cutoffTime = LocalDateTime.now().minusHours(hoursBack)

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)
      .filter($"eventTime" >= cutoffTime.toString)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    val summary = df.agg(
      count("*").as("totalBackups"),
      sum(when($"status" === "success", 1).otherwise(0)).as("successfulBackups"),
      sum(when($"status" === "failed", 1).otherwise(0)).as("failedBackups"),
      sum("size").as("totalDataSize"),
      avg("duration").as("avgBackupDuration"),
      avg("size").as("avgBackupSize")
    ).first()

    BackupSummary(
      totalBackups = summary.getAs[Long]("totalBackups"),
      successfulBackups = summary.getAs[Long]("successfulBackups"),
      failedBackups = summary.getAs[Long]("failedBackups"),
      totalDataSize = summary.getAs[Long]("totalDataSize"),
      avgBackupDuration = summary.getAs[Double]("avgBackupDuration"),
      avgBackupSize = summary.getAs[Double]("avgBackupSize"),
      timestamp = LocalDateTime.now().toString
    )
  }

  /**
   * Get backup trends over time
   */
  def getBackupTrends(tenantId: Option[String], daysBack: Int): Seq[BackupTrend] = {
    logger.info(s"Computing backup trends for tenant $tenantId, last $daysBack days")

    val cutoffDate = LocalDate.now().minusDays(daysBack)

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)
      .filter($"date" >= cutoffDate.toString)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    df.groupBy($"date")
      .agg(
        count("*").as("backupCount"),
        sum("size").as("totalSize"),
        avg("duration").as("avgDuration"),
        (sum(when($"status" === "success", 1).otherwise(0)) / count("*") * 100).as("successRate")
      )
      .orderBy($"date")
      .as[BackupTrend]
      .collect()
      .toSeq
  }

  /**
   * Get detected anomalies
   */
  def getAnomalies(tenantId: Option[String], hoursBack: Int): Seq[Anomaly] = {
    logger.info(s"Retrieving anomalies for tenant $tenantId, last $hoursBack hours")

    val cutoffTime = LocalDateTime.now().minusHours(hoursBack)

    var df = spark.read
      .format("parquet")
      .load(anomaliesPath)
      .filter($"timestamp" >= cutoffTime.toString)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    df.orderBy($"timestamp".desc)
      .limit(100)
      .collect()
      .map(row => Anomaly(
        id = row.getAs[String]("id"),
        timestamp = row.getAs[String]("timestamp"),
        tenantId = row.getAs[String]("tenantId"),
        backupType = row.getAs[String]("backupType"),
        anomalyScore = row.getAs[Double]("anomalyScore"),
        reason = row.getAs[String]("reason"),
        details = Map("raw" -> row.toString())
      ))
      .toSeq
  }

  /**
   * Execute custom SQL query on backup data
   */
  def executeCustomQuery(queryJson: String): Map[String, Any] = {
    logger.info(s"Executing custom query: $queryJson")

    // Parse query JSON and execute
    // For simplicity, returning a placeholder
    Map(
      "status" -> "success",
      "message" -> "Custom query execution not yet fully implemented",
      "query" -> queryJson
    )
  }

  /**
   * Get storage usage statistics
   */
  def getStorageUsage(tenantId: Option[String], groupBy: String): Seq[StorageUsage] = {
    logger.info(s"Computing storage usage for tenant $tenantId, grouped by $groupBy")

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    val grouped = groupBy match {
      case "day" => df.groupBy($"date", $"tenantId")
      case "week" => df.groupBy(weekofyear($"date").as("week"), $"tenantId")
      case "month" => df.groupBy(month($"date").as("month"), $"tenantId")
      case _ => df.groupBy($"date", $"tenantId")
    }

    grouped
      .agg(
        sum("size").as("totalSize"),
        count("*").as("backupCount"),
        avg("size").as("avgBackupSize")
      )
      .collect()
      .map(row => StorageUsage(
        tenantId = Some(row.getAs[String]("tenantId")),
        date = row.schema.fieldNames(0),
        totalSize = row.getAs[Long]("totalSize"),
        backupCount = row.getAs[Long]("backupCount"),
        avgBackupSize = row.getAs[Long]("avgBackupSize")
      ))
      .toSeq
  }

  /**
   * Get performance statistics
   */
  def getPerformanceStats(tenantId: Option[String], metric: String): Seq[PerformanceStats] = {
    logger.info(s"Computing performance stats for tenant $tenantId, metric: $metric")

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    metric match {
      case "throughput" =>
        val avgThroughput = df.agg(avg($"size" / $"duration")).first().getDouble(0)
        Seq(PerformanceStats("throughput", avgThroughput, "bytes/sec", LocalDateTime.now().toString))

      case "compression_ratio" =>
        val avgRatio = df.agg(avg($"compressedSize" / $"size")).first().getDouble(0)
        Seq(PerformanceStats("compression_ratio", avgRatio, "ratio", LocalDateTime.now().toString))

      case "all" =>
        Seq(
          PerformanceStats("avg_duration", df.agg(avg("duration")).first().getDouble(0), "seconds", LocalDateTime.now().toString),
          PerformanceStats("avg_size", df.agg(avg("size")).first().getDouble(0), "bytes", LocalDateTime.now().toString)
        )

      case _ =>
        Seq.empty
    }
  }
}
