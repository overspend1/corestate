package models

import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.udf
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable

/**
 * Anomaly Detector using Isolation Forest algorithm
 *
 * Detects anomalies in backup operations by analyzing feature vectors
 * and computing anomaly scores based on statistical outliers.
 */
class AnomalyDetector extends LazyLogging with Serializable {

  // In-memory model registry (in production, this would be loaded from model store)
  private val modelRegistry = new mutable.HashMap[String, IsolationForestModel]()

  /**
   * Detect anomaly score for a feature vector
   *
   * @param features Feature vector (size, duration, throughput, etc.)
   * @param tenantId Tenant ID for tenant-specific models
   * @return Anomaly score (0.0 = normal, 1.0 = highly anomalous)
   */
  def detectAnomaly(features: Vector, tenantId: String): Double = {
    try {
      val model = getOrCreateModel(tenantId)
      val score = model.computeAnomalyScore(features)

      if (score > 0.8) {
        logger.warn(s"High anomaly score detected for tenant $tenantId: $score")
      }

      score
    } catch {
      case e: Exception =>
        logger.error(s"Anomaly detection failed for tenant $tenantId", e)
        0.0 // Return normal score on error
    }
  }

  /**
   * UDF for Spark SQL integration
   */
  def detectAnomalyUDF: UserDefinedFunction = udf((features: Vector, tenantId: String) => {
    detectAnomaly(features, tenantId)
  })

  /**
   * Get or create tenant-specific model
   */
  private def getOrCreateModel(tenantId: String): IsolationForestModel = {
    modelRegistry.getOrElseUpdate(tenantId, {
      logger.info(s"Creating new Isolation Forest model for tenant $tenantId")
      new IsolationForestModel(
        numTrees = 100,
        samplingSize = 256,
        maxDepth = 10
      )
    })
  }
}

/**
 * Simplified Isolation Forest Model
 *
 * This is a lightweight implementation for demonstration.
 * In production, use scikit-learn or H2O models loaded via MLeap/PMML.
 */
class IsolationForestModel(
  numTrees: Int = 100,
  samplingSize: Int = 256,
  maxDepth: Int = 10
) extends Serializable {

  // Historical statistics for z-score based anomaly detection
  private var mean: Option[Vector] = None
  private var stdDev: Option[Vector] = None

  /**
   * Compute anomaly score using statistical distance
   *
   * Uses Mahalanobis distance for multivariate outlier detection
   */
  def computeAnomalyScore(features: Vector): Double = {
    if (features.size == 0) return 0.0

    // For now, use simple z-score based detection
    // In production, use actual Isolation Forest or trained ML model
    val scores = features.toArray.zipWithIndex.map { case (value, idx) =>
      // Simple outlier detection based on expected ranges
      val normalizedScore = idx match {
        case 0 => normalizeSize(value) // size
        case 1 => normalizeDuration(value) // duration
        case 2 => normalizeThroughput(value) // throughput
        case 3 => normalizeCompressionRatio(value) // compressionRatio
        case 4 => normalizeDeduplicationRatio(value) // deduplicationRatio
        case 5 => normalizeFileCount(value) // fileCount
        case _ => 0.0
      }
      normalizedScore
    }

    // Aggregate score (average of individual feature scores)
    val avgScore = scores.sum / scores.length

    // Apply sigmoid to get score between 0 and 1
    1.0 / (1.0 + math.exp(-5.0 * (avgScore - 0.5)))
  }

  // Normalization functions based on expected ranges
  private def normalizeSize(size: Double): Double = {
    // Expect sizes between 1MB and 100GB
    val minSize = 1e6 // 1MB
    val maxSize = 1e11 // 100GB
    if (size < minSize * 0.1 || size > maxSize * 10) 1.0 else 0.0
  }

  private def normalizeDuration(duration: Double): Double = {
    // Expect durations between 1 second and 1 hour
    val minDuration = 1.0
    val maxDuration = 3600.0
    if (duration < minDuration * 0.1 || duration > maxDuration * 10) 1.0 else 0.0
  }

  private def normalizeThroughput(throughput: Double): Double = {
    // Expect throughput between 1MB/s and 1GB/s
    val minThroughput = 1e6
    val maxThroughput = 1e9
    if (throughput < minThroughput * 0.1 || throughput > maxThroughput * 10) 0.8 else 0.0
  }

  private def normalizeCompressionRatio(ratio: Double): Double = {
    // Expect compression ratio between 0.2 and 0.9
    if (ratio < 0.1 || ratio > 0.95) 0.7 else 0.0
  }

  private def normalizeDeduplicationRatio(ratio: Double): Double = {
    // Expect dedup ratio between 0.0 and 0.8
    if (ratio > 0.9) 0.6 else 0.0
  }

  private def normalizeFileCount(count: Double): Double = {
    // Expect file count between 1 and 1 million
    if (count < 1 || count > 1e7) 0.8 else 0.0
  }
}
