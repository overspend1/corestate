package services

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import com.typesafe.scalalogging.LazyLogging
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

case class DailyReport(
  date: String,
  tenantId: Option[String],
  totalBackups: Long,
  successfulBackups: Long,
  failedBackups: Long,
  totalDataSize: Long,
  avgBackupDuration: Double,
  topBackupTypes: Seq[BackupTypeStats],
  peakHours: Seq[HourlyStats]
)

case class WeeklyReport(
  weekStart: String,
  weekEnd: String,
  tenantId: Option[String],
  totalBackups: Long,
  successfulBackups: Long,
  failedBackups: Long,
  totalDataSize: Long,
  avgBackupDuration: Double,
  dailyBreakdown: Seq[DailyStats],
  topBackupTypes: Seq[BackupTypeStats]
)

case class MonthlyReport(
  month: String,
  year: Int,
  tenantId: Option[String],
  totalBackups: Long,
  successfulBackups: Long,
  failedBackups: Long,
  totalDataSize: Long,
  avgBackupDuration: Double,
  weeklyBreakdown: Seq[WeeklyStats],
  topBackupTypes: Seq[BackupTypeStats],
  growthRate: Double
)

case class BackupTypeStats(
  backupType: String,
  count: Long,
  totalSize: Long,
  avgDuration: Double
)

case class HourlyStats(
  hour: Int,
  count: Long
)

case class DailyStats(
  date: String,
  count: Long,
  totalSize: Long
)

case class WeeklyStats(
  week: Int,
  count: Long,
  totalSize: Long
)

/**
 * Report Service for generating periodic analytics reports
 */
class ReportService(spark: SparkSession) extends LazyLogging {

  import spark.implicits._

  private val backupEventsPath = "s3a://corestate-data-lake/backup-events"

  /**
   * Generate daily report
   */
  def generateDailyReport(tenantId: Option[String], dateStr: Option[String]): DailyReport = {
    val reportDate = dateStr.map(LocalDate.parse).getOrElse(LocalDate.now().minusDays(1))
    logger.info(s"Generating daily report for date $reportDate, tenant $tenantId")

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)
      .filter($"date" === reportDate.toString)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    // Aggregate main statistics
    val mainStats = df.agg(
      count("*").as("totalBackups"),
      sum(when($"status" === "success", 1).otherwise(0)).as("successfulBackups"),
      sum(when($"status" === "failed", 1).otherwise(0)).as("failedBackups"),
      sum("size").as("totalDataSize"),
      avg("duration").as("avgBackupDuration")
    ).first()

    // Top backup types
    val topTypes = df.groupBy($"backupType")
      .agg(
        count("*").as("count"),
        sum("size").as("totalSize"),
        avg("duration").as("avgDuration")
      )
      .orderBy($"count".desc)
      .limit(10)
      .as[BackupTypeStats]
      .collect()
      .toSeq

    // Peak hours
    val peakHours = df.groupBy(hour($"eventTime").as("hour"))
      .agg(count("*").as("count"))
      .orderBy($"count".desc)
      .limit(5)
      .as[HourlyStats]
      .collect()
      .toSeq

    DailyReport(
      date = reportDate.toString,
      tenantId = tenantId,
      totalBackups = mainStats.getAs[Long]("totalBackups"),
      successfulBackups = mainStats.getAs[Long]("successfulBackups"),
      failedBackups = mainStats.getAs[Long]("failedBackups"),
      totalDataSize = mainStats.getAs[Long]("totalDataSize"),
      avgBackupDuration = mainStats.getAs[Double]("avgBackupDuration"),
      topBackupTypes = topTypes,
      peakHours = peakHours
    )
  }

  /**
   * Generate weekly report
   */
  def generateWeeklyReport(tenantId: Option[String], weekStartStr: Option[String]): WeeklyReport = {
    val weekStart = weekStartStr.map(LocalDate.parse).getOrElse(LocalDate.now().minusWeeks(1))
    val weekEnd = weekStart.plusDays(6)
    logger.info(s"Generating weekly report for $weekStart to $weekEnd, tenant $tenantId")

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)
      .filter($"date" >= weekStart.toString && $"date" <= weekEnd.toString)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    // Main statistics
    val mainStats = df.agg(
      count("*").as("totalBackups"),
      sum(when($"status" === "success", 1).otherwise(0)).as("successfulBackups"),
      sum(when($"status" === "failed", 1).otherwise(0)).as("failedBackups"),
      sum("size").as("totalDataSize"),
      avg("duration").as("avgBackupDuration")
    ).first()

    // Daily breakdown
    val dailyBreakdown = df.groupBy($"date")
      .agg(
        count("*").as("count"),
        sum("size").as("totalSize")
      )
      .orderBy($"date")
      .as[DailyStats]
      .collect()
      .toSeq

    // Top backup types
    val topTypes = df.groupBy($"backupType")
      .agg(
        count("*").as("count"),
        sum("size").as("totalSize"),
        avg("duration").as("avgDuration")
      )
      .orderBy($"count".desc)
      .limit(10)
      .as[BackupTypeStats]
      .collect()
      .toSeq

    WeeklyReport(
      weekStart = weekStart.toString,
      weekEnd = weekEnd.toString,
      tenantId = tenantId,
      totalBackups = mainStats.getAs[Long]("totalBackups"),
      successfulBackups = mainStats.getAs[Long]("successfulBackups"),
      failedBackups = mainStats.getAs[Long]("failedBackups"),
      totalDataSize = mainStats.getAs[Long]("totalDataSize"),
      avgBackupDuration = mainStats.getAs[Double]("avgBackupDuration"),
      dailyBreakdown = dailyBreakdown,
      topBackupTypes = topTypes
    )
  }

  /**
   * Generate monthly report
   */
  def generateMonthlyReport(tenantId: Option[String], monthStr: Option[String]): MonthlyReport = {
    val reportDate = monthStr.map(s => LocalDate.parse(s + "-01")).getOrElse(LocalDate.now().minusMonths(1).withDayOfMonth(1))
    val monthStart = reportDate.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1).minusDays(1)
    logger.info(s"Generating monthly report for $monthStart to $monthEnd, tenant $tenantId")

    var df = spark.read
      .format("parquet")
      .load(backupEventsPath)
      .filter($"date" >= monthStart.toString && $"date" <= monthEnd.toString)

    tenantId.foreach(id => df = df.filter($"tenantId" === id))

    // Main statistics
    val mainStats = df.agg(
      count("*").as("totalBackups"),
      sum(when($"status" === "success", 1).otherwise(0)).as("successfulBackups"),
      sum(when($"status" === "failed", 1).otherwise(0)).as("failedBackups"),
      sum("size").as("totalDataSize"),
      avg("duration").as("avgBackupDuration")
    ).first()

    // Weekly breakdown
    val weeklyBreakdown = df.groupBy(weekofyear($"date").as("week"))
      .agg(
        count("*").as("count"),
        sum("size").as("totalSize")
      )
      .orderBy($"week")
      .as[WeeklyStats]
      .collect()
      .toSeq

    // Top backup types
    val topTypes = df.groupBy($"backupType")
      .agg(
        count("*").as("count"),
        sum("size").as("totalSize"),
        avg("duration").as("avgDuration")
      )
      .orderBy($"count".desc)
      .limit(10)
      .as[BackupTypeStats]
      .collect()
      .toSeq

    // Calculate growth rate (compare with previous month)
    val prevMonthStart = monthStart.minusMonths(1)
    val prevMonthEnd = prevMonthStart.plusMonths(1).minusDays(1)

    var prevDf = spark.read
      .format("parquet")
      .load(backupEventsPath)
      .filter($"date" >= prevMonthStart.toString && $"date" <= prevMonthEnd.toString)

    tenantId.foreach(id => prevDf = prevDf.filter($"tenantId" === id))

    val prevCount = prevDf.count()
    val currentCount = mainStats.getAs[Long]("totalBackups")
    val growthRate = if (prevCount > 0) {
      ((currentCount - prevCount).toDouble / prevCount) * 100.0
    } else {
      0.0
    }

    MonthlyReport(
      month = monthStart.getMonth.toString,
      year = monthStart.getYear,
      tenantId = tenantId,
      totalBackups = currentCount,
      successfulBackups = mainStats.getAs[Long]("successfulBackups"),
      failedBackups = mainStats.getAs[Long]("failedBackups"),
      totalDataSize = mainStats.getAs[Long]("totalDataSize"),
      avgBackupDuration = mainStats.getAs[Double]("avgBackupDuration"),
      weeklyBreakdown = weeklyBreakdown,
      topBackupTypes = topTypes,
      growthRate = growthRate
    )
  }
}
