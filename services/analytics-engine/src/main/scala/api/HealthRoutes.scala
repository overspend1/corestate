package api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.spark.sql.SparkSession
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.Json

case class HealthStatus(
  status: String,
  version: String,
  sparkVersion: String,
  sparkMaster: String,
  activeStreams: Int,
  uptime: Long
)

class HealthRoutes(spark: SparkSession) {

  private val startTime = System.currentTimeMillis()

  val routes: Route = concat(
    pathEndOrSingleSlash {
      get {
        val health = HealthStatus(
          status = "healthy",
          version = "2.0.0",
          sparkVersion = spark.version,
          sparkMaster = spark.sparkContext.master,
          activeStreams = spark.streams.active.length,
          uptime = System.currentTimeMillis() - startTime
        )

        complete(HttpEntity(ContentTypes.`application/json`, health.asJson.noSpaces))
      }
    },
    path("liveness") {
      get {
        complete(StatusCodes.OK, Json.obj("status" -> Json.fromString("alive")).noSpaces)
      }
    },
    path("readiness") {
      get {
        val ready = !spark.sparkContext.isStopped && spark.streams.active.nonEmpty
        if (ready) {
          complete(StatusCodes.OK, Json.obj("status" -> Json.fromString("ready")).noSpaces)
        } else {
          complete(StatusCodes.ServiceUnavailable, Json.obj("status" -> Json.fromString("not_ready")).noSpaces)
        }
      }
    }
  )
}
