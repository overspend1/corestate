package api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import services.AggregationService
import io.circe.generic.auto._
import io.circe.syntax._
import scala.util.{Success, Failure}

case class BackupMetrics(
  totalBackups: Long,
  successfulBackups: Long,
  failedBackups: Long,
  totalDataSize: Long,
  avgBackupDuration: Double,
  avgBackupSize: Double
)

class MetricsRoutes(aggregationService: AggregationService) {

  val routes: Route = concat(
    path("summary") {
      get {
        parameters("tenantId".optional, "hours".as[Int].optional) { (tenantId, hours) =>
          val hoursBack = hours.getOrElse(24)

          try {
            val metrics = aggregationService.getBackupSummary(tenantId, hoursBack)
            complete(HttpEntity(ContentTypes.`application/json`, metrics.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to retrieve metrics", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("trends") {
      get {
        parameters("tenantId".optional, "days".as[Int].optional) { (tenantId, days) =>
          val daysBack = days.getOrElse(7)

          try {
            val trends = aggregationService.getBackupTrends(tenantId, daysBack)
            complete(HttpEntity(ContentTypes.`application/json`, trends.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to retrieve trends", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("anomalies") {
      get {
        parameters("tenantId".optional, "hours".as[Int].optional) { (tenantId, hours) =>
          val hoursBack = hours.getOrElse(24)

          try {
            val anomalies = aggregationService.getAnomalies(tenantId, hoursBack)
            complete(HttpEntity(ContentTypes.`application/json`, anomalies.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to retrieve anomalies", "message": "${e.getMessage}"}""")
          }
        }
      }
    }
  )
}
