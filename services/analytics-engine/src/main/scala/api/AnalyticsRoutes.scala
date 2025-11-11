package api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import services.{AggregationService, ReportService}
import io.circe.generic.auto._
import io.circe.syntax._

class AnalyticsRoutes(
  aggregationService: AggregationService,
  reportService: ReportService
) {

  val routes: Route = concat(
    path("report" / "daily") {
      get {
        parameters("tenantId".optional, "date".optional) { (tenantId, date) =>
          try {
            val report = reportService.generateDailyReport(tenantId, date)
            complete(HttpEntity(ContentTypes.`application/json`, report.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to generate daily report", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("report" / "weekly") {
      get {
        parameters("tenantId".optional, "weekStart".optional) { (tenantId, weekStart) =>
          try {
            val report = reportService.generateWeeklyReport(tenantId, weekStart)
            complete(HttpEntity(ContentTypes.`application/json`, report.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to generate weekly report", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("report" / "monthly") {
      get {
        parameters("tenantId".optional, "month".optional) { (tenantId, month) =>
          try {
            val report = reportService.generateMonthlyReport(tenantId, month)
            complete(HttpEntity(ContentTypes.`application/json`, report.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to generate monthly report", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("query") {
      post {
        entity(as[String]) { queryJson =>
          try {
            val result = aggregationService.executeCustomQuery(queryJson)
            complete(HttpEntity(ContentTypes.`application/json`, result.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.BadRequest,
                s"""{"error": "Invalid query", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("storage" / "usage") {
      get {
        parameters("tenantId".optional, "groupBy".optional) { (tenantId, groupBy) =>
          try {
            val usage = aggregationService.getStorageUsage(tenantId, groupBy.getOrElse("day"))
            complete(HttpEntity(ContentTypes.`application/json`, usage.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to retrieve storage usage", "message": "${e.getMessage}"}""")
          }
        }
      }
    },
    path("performance" / "stats") {
      get {
        parameters("tenantId".optional, "metric".optional) { (tenantId, metric) =>
          try {
            val stats = aggregationService.getPerformanceStats(tenantId, metric.getOrElse("all"))
            complete(HttpEntity(ContentTypes.`application/json`, stats.asJson.noSpaces))
          } catch {
            case e: Exception =>
              complete(StatusCodes.InternalServerError,
                s"""{"error": "Failed to retrieve performance stats", "message": "${e.getMessage}"}""")
          }
        }
      }
    }
  )
}
