package org.archive.webservices.ars.io

import java.time.Instant

import org.apache.hadoop.fs.Path
import org.archive.helge.sparkling._
import org.archive.helge.sparkling.io.HdfsIO._
import org.archive.webservices.ars.model.ArsCloudConf

import scala.util.Try

object CollectionCache {
  val CacheClearThresholdBytes: Long = 1.tb

  private var inUse = Set.empty[String]
  private var lastUse = Map.empty[String, Long]

  def cache[R](collectionId: String)(action: String => R): R = {
    synchronized {
      inUse += collectionId
      clearCache()
    }
    val path = cachePath(collectionId)
    fs.mkdirs(new Path(path))
    val r = action(path)
    synchronized {
      inUse -= collectionId
      lastUse = lastUse.updated(collectionId, Instant.now.toEpochMilli)
    }
    r
  }

  def cachePath(collectionId: String): String =
    ArsCloudConf.collectionCachePath + "/" + collectionId

  def clearCache(): Unit = synchronized {
    var length = Try(fs.getContentSummary(new Path(ArsCloudConf.collectionCachePath)).getLength)
      .getOrElse(0L)
    if (length > CacheClearThresholdBytes) {
      for (dir <- fs.listStatus(new Path(ArsCloudConf.collectionCachePath))
           if dir.isDirectory) {
        val path = dir.getPath
        val collectionId = path.getName
        if (!inUse.contains(collectionId) && !lastUse.contains(collectionId)) {
          val pathLength = fs.getContentSummary(path).getLength
          if (fs.delete(path, true)) length -= pathLength
        }
      }

      val toDelete =
        lastUse.toSeq
          .filter { case (c, _) => !inUse.contains(c) }
          .sortBy(_._2)
          .map(_._1)
          .toIterator
      while (length > CacheClearThresholdBytes && toDelete.hasNext) {
        val path = new Path(cachePath(toDelete.next))
        val pathLength = fs.getContentSummary(path).getLength
        if (fs.delete(path, true)) length -= pathLength
      }
    }
  }
}