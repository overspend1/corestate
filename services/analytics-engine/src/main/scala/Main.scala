package com.corestate.analytics

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import org.apache.spark.sql.SparkSession
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import streaming.BackupAnalyticsEngine
import api.{AnalyticsRoutes, MetricsRoutes, HealthRoutes}
import services.{AggregationService, ReportService}
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
 * CoreState Analytics Engine
 *
 * Real-time analytics service for backup operations using Apache Spark Streaming,
 * Akka HTTP for REST API, and integration with time-series databases.
 *
 * Features:
 * - Real-time backup event processing via Kafka
 * - Time-windowed aggregations and statistics
 * - Anomaly detection using ML models
 * - Historical data analysis with Spark SQL
 * - REST API for querying analytics
 * - Prometheus metrics export
 */
object Main extends App with LazyLogging {

  logger.info("Starting CoreState Analytics Engine v2.0")

  val config = ConfigFactory.load()

  // Initialize Spark Session
  val spark = SparkSession.builder()
    .appName("CoreState Analytics Engine")
    .master(config.getString("spark.master"))
    .config("spark.sql.streaming.checkpointLocation", config.getString("spark.checkpoint.location"))
    .config("spark.sql.shuffle.partitions", config.getInt("spark.shuffle.partitions"))
    .config("spark.sql.streaming.metricsEnabled", "true")
    .config("spark.streaming.stopGracefullyOnShutdown", "true")
    .getOrCreate()

  spark.sparkContext.setLogLevel("WARN")

  logger.info(s"Spark Session initialized: ${spark.version}")

  // Initialize Akka HTTP Server
  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "analytics-engine-system")
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  // Initialize services
  val aggregationService = new AggregationService(spark)
  val reportService = new ReportService(spark)

  // Initialize Prometheus metrics
  DefaultExports.initialize()
  val prometheusPort = config.getInt("prometheus.port")
  val prometheusServer = new HTTPServer(prometheusPort)
  logger.info(s"Prometheus metrics server started on port $prometheusPort")

  // Setup routes
  val healthRoutes = new HealthRoutes(spark)
  val metricsRoutes = new MetricsRoutes(aggregationService)
  val analyticsRoutes = new AnalyticsRoutes(aggregationService, reportService)

  val routes = concat(
    pathPrefix("health")(healthRoutes.routes),
    pathPrefix("metrics")(metricsRoutes.routes),
    pathPrefix("analytics")(analyticsRoutes.routes)
  )

  // Start HTTP server
  val httpHost = config.getString("http.host")
  val httpPort = config.getInt("http.port")

  val bindingFuture: Future[Http.ServerBinding] = Http()
    .newServerAt(httpHost, httpPort)
    .bind(routes)

  bindingFuture.onComplete {
    case Success(binding) =>
      val address = binding.localAddress
      logger.info(s"Analytics Engine REST API online at http://${address.getHostString}:${address.getPort}/")
    case Failure(ex) =>
      logger.error(s"Failed to bind HTTP endpoint, terminating system", ex)
      system.terminate()
  }

  // Start Spark Streaming Analytics Pipeline
  val streamingThread = new Thread(() => {
    try {
      logger.info("Starting Spark Streaming analytics pipeline...")
      BackupAnalyticsEngine.startAnalyticsPipeline(spark)
    } catch {
      case e: Exception =>
        logger.error("Streaming pipeline failed", e)
        system.terminate()
        spark.stop()
    }
  })
  streamingThread.setName("spark-streaming-pipeline")
  streamingThread.start()

  // Graceful shutdown
  sys.addShutdownHook {
    logger.info("Shutting down Analytics Engine...")
    prometheusServer.stop()
    spark.stop()
    system.terminate()
    logger.info("Analytics Engine stopped.")
  }

  logger.info("CoreState Analytics Engine fully initialized and running")
}
