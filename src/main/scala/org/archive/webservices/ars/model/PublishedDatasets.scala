package org.archive.webservices.ars.model

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.apache.hadoop.fs.Path
import org.archive.webservices.ars.processing.{DerivationJobConf, DerivationJobInstance, JobManager, ProcessingState}
import org.archive.webservices.sparkling.io.{HdfsIO, IOUtil}

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import java.time.Instant
import scala.collection.immutable.{ListMap, Map}
import scala.io.Source
import scala.util.Try

object PublishedDatasets {
  val MetadataFields: Set[String] = Set("title")
  val ArkMintUrl: String = "https://ark.archive.org/mint"

  private var sync = Set.empty[String]

  def jobFile(instance: DerivationJobInstance): String = {
    instance.conf.outputPath + "/" + instance.job.id + "/published.json"
  }

  def collectionFile(outPath: String): String = outPath + "/published.json"

  def itemName(collection: ArchCollection, instance: DerivationJobInstance): String = {
    val sample = if (instance.conf.isSample) "-sample" else ""
    collection.sourceId + "_" + instance.job.id + sample + "_" + instance.info.startTime
      .getOrElse(Instant.now)
      .toString
      .replaceAll("[:.]", "-")
  }

  def syncCollectionFile[R](f: String)(action: => R): R = {
    while (sync.contains(f) || synchronized {
             sync.contains(f) || {
               sync += f
               false
             }
           }) {}
    try {
      action
    } finally {
      synchronized(sync -= f)
    }
  }

  case class ItemInfo(
      item: String,
      collection: String,
      source: String,
      job: String,
      sample: Boolean,
      time: Instant,
      complete: Boolean,
      ark: Option[String]) {
    def toJson(includeItem: Boolean): Json = {
      (if (includeItem) ListMap("item" -> item.asJson) else ListMap.empty) ++ Map(
        "collection" -> collection.asJson,
        "source" -> source.asJson,
        "job" -> job.asJson,
        "sample" -> sample.asJson,
        "time" -> time.toString.asJson,
        "complete" -> complete.asJson) ++ ark.map("ark" -> _.asJson).toMap
    }.asJson
  }

  def newItemInfo(
      itemName: String,
      collection: ArchCollection,
      instance: DerivationJobInstance): ItemInfo = {
    ItemInfo(
      itemName,
      collection.id,
      collection.sourceId,
      instance.job.id,
      instance.conf.isSample,
      Instant.now,
      complete = false,
      ark(itemName))
  }

  def appendCollectionFile(outPath: String, info: ItemInfo): Unit = {
    HdfsIO.fs.mkdirs(new Path(outPath))
    val f = collectionFile(outPath)
    syncCollectionFile(f) {
      val in = if (HdfsIO.exists(f)) {
        parse(HdfsIO.lines(f).mkString).toSeq
          .map(_.hcursor)
          .flatMap { cursor =>
            cursor.keys.toSeq.flatten.flatMap(key => cursor.downField(key).focus.map(key -> _))
          }
          .toMap
      } else Map.empty[String, Json]
      HdfsIO.writeLines(
        f,
        Seq(in.updated(info.item, info.toJson(includeItem = false)).asJson.spaces4),
        overwrite = true)
    }
  }

  def collectionItems(collection: ArchCollection): Set[String] = {
    val collectionFilePath = collectionFile(DerivationJobConf.jobOutPath(collection))
    parse(HdfsIO.lines(collectionFilePath).mkString).toSeq
      .map(_.hcursor)
      .flatMap { cursor =>
        cursor.keys.toSeq.flatten
      }
      .toSet
  }

  def collectionInfoJson(collection: ArchCollection): String = {
    val collectionFilePath = collectionFile(DerivationJobConf.jobOutPath(collection))
    HdfsIO.lines(collectionFilePath).mkString("\n")
  }

  def request(
      path: String,
      s3: Boolean = false,
      update: Boolean = false,
      metadata: Map[String, Seq[String]] = Map.empty,
      put: Option[(InputStream, Long)] = None): Option[String] = ArchConf.iaAuthHeader.flatMap {
    iaAuthHeader =>
      val url = (if (s3) "http://s3.us.archive.org/" else "https://archive.org/") + path
        .stripPrefix("/")
      val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
      try {
        connection.setRequestProperty("Authorization", iaAuthHeader)
        connection.setDoInput(true)
        if (s3) {
          for ((key, value) <- metadata) {
            if (value.size == 1)
              connection.setRequestProperty("x-archive-meta-" + key, value.head)
            else {
              for ((v, i) <- value.zipWithIndex) {
                val metaIdx = (if (i < 9) "0" else "") + (i + 1)
                connection.setRequestProperty(s"x-archive-meta$metaIdx-" + key, v)
              }
            }
          }
          if (update) connection.setRequestProperty("x-archive-ignore-preexisting-bucket", "1")
        }
        if (s3 || put.isDefined) {
          connection.setRequestMethod("PUT")
          for ((in, length) <- put) {
            connection.setDoOutput(true)
            connection.setFixedLengthStreamingMode(length)
            val out = connection.getOutputStream
            try {
              IOUtil.copy(in, out, length)
            } finally {
              Try(out.close())
            }
          }
        }
        connection.connect()
        if (connection.getResponseCode / 100 == 2) Some {
          val source = Source.fromInputStream(connection.getInputStream, "utf-8")
          try source.mkString
          finally source.close()
        } else None
      } finally {
        Try(connection.disconnect())
      }
  }

  def ark(itemName: String): Option[String] = ArchConf.arkMintBearer.flatMap { arkMintBearer =>
    val connection =
      new URL("https://ark.archive.org/mint").openConnection.asInstanceOf[HttpURLConnection]
    try {
      connection.setRequestMethod("POST")
      connection.setRequestProperty("Authorization", "Bearer " + arkMintBearer)
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setDoInput(true)
      connection.setDoOutput(true)
      val out = IOUtil.print(connection.getOutputStream, closing = true)
      try {
        out.println(
          Map(
            "naan" -> 13960.asJson,
            "shoulder" -> "/a3".asJson,
            "url" -> s"https://archive.org/details/$itemName".asJson,
            "metadata" -> Map.empty[String, String].asJson,
            "maintenance_commitment" -> Map.empty[String, String].asJson).asJson.noSpaces)
      } finally {
        out.close()
      }
      connection.connect()
      if (connection.getResponseCode / 100 == 2) {
        val source = Source.fromInputStream(connection.getInputStream, "utf-8")
        try {
          parse(source.mkString).toOption.flatMap(_.hcursor.get[String]("ark").toOption)
        } finally source.close()
      } else None
    } finally {
      Try(connection.disconnect())
    }
  }

  def dataset(
      jobId: String,
      collection: ArchCollection,
      sample: Boolean): Option[DerivationJobInstance] = {
    DerivationJobConf
      .collection(collection, sample = sample)
      .flatMap {
        JobManager.getInstanceOrGlobal(
          jobId,
          _,
          DerivationJobConf.collection(collection, sample = sample, global = true))
      }
      .filter(_.state == ProcessingState.Finished)
  }

  def publish(
      jobId: String,
      collection: ArchCollection,
      sample: Boolean,
      metadata: Map[String, Seq[String]]): Option[ItemInfo] = {
    for (instance <- dataset(jobId, collection, sample)) yield {
      val jobFilePath = jobFile(instance)
      if (HdfsIO.fs.createNewFile(new Path(jobFilePath))) Some {
        val item = itemName(collection, instance)
        val itemInfo = newItemInfo(item, collection, instance)
        if (!createItem(item, datasetMetadata(instance, itemInfo) ++ metadata)) {
          throw new RuntimeException(s"Creating new Petabox item $item failed.")
        }
        appendCollectionFile(DerivationJobConf.jobOutPath(collection), itemInfo)
        HdfsIO.writeLines(
          jobFilePath,
          Seq(itemInfo.toJson(includeItem = true).spaces4),
          overwrite = true)
        itemInfo
      } else jobItem(jobFilePath)
    }
  }.flatten

  def datasetMetadata(
      instance: DerivationJobInstance,
      info: ItemInfo): Map[String, Seq[String]] = {
    Map("collection" -> Seq(ArchConf.pboxCollection)) ++ info.ark.map("Identifier-ark" -> Seq(_))
  }

  def jobItem(dataset: DerivationJobInstance): Option[ItemInfo] = {
    jobItem(PublishedDatasets.jobFile(dataset))
  }

  def jobItem(jobFile: String): Option[ItemInfo] = {
    if (HdfsIO.exists(jobFile)) {
      parse(HdfsIO.lines(jobFile).mkString).toOption.map(_.hcursor).flatMap { cursor =>
        for {
          item <- cursor.get[String]("item").toOption
          collectionId <- cursor.get[String]("collection").toOption
          sourceId <- cursor.get[String]("source").toOption
          jobId <- cursor.get[String]("job").toOption
          isSample <- cursor.get[Boolean]("sample").toOption
          time <- cursor.get[String]("time").toOption.map(Instant.parse)
          complete <- cursor.get[Boolean]("complete").toOption
        } yield
          ItemInfo(
            item,
            collectionId,
            sourceId,
            jobId,
            isSample,
            time,
            complete,
            cursor.get[String]("ark").toOption)
      }
    } else None
  }

  def complete(
      collection: ArchCollection,
      instance: DerivationJobInstance,
      itemName: String): Boolean = {
    val jobFilePath = jobFile(instance)
    jobItem(jobFilePath).filter(_.item == itemName) match {
      case Some(itemInfo) =>
        HdfsIO.writeLines(
          jobFilePath,
          Seq(itemInfo.copy(complete = true).toJson(includeItem = true).spaces4),
          overwrite = true)
        val collectionFilePath = collectionFile(DerivationJobConf.jobOutPath(collection))
        syncCollectionFile(collectionFilePath) {
          val in = parse(HdfsIO.lines(collectionFilePath).mkString).toOption
            .flatMap(_.as[Map[String, Json]].toOption)
            .getOrElse(Map.empty)
          val out =
            in.updated(itemName, itemInfo.copy(complete = true).toJson(includeItem = false))
          HdfsIO.writeLines(collectionFilePath, Seq(out.asJson.spaces4), overwrite = true)
        }
        true
      case None => false
    }
  }

  def createItem(name: String, metadata: Map[String, Seq[String]]): Boolean = {
    request(name, s3 = true, metadata = metadata).isDefined
  }

  def updateItem(name: String, metadata: Map[String, Seq[String]]): Boolean = {
    this
      .metadata(name, all = true)
      .flatMap { existingMetadata =>
        request(name, s3 = true, update = true, metadata = existingMetadata ++ metadata)
      }
      .isDefined
  }

  def files(itemName: String): Set[String] = {
    request(s"metadata/$itemName/files")
      .flatMap(parse(_).toOption.map(_.hcursor))
      .toSeq
      .flatMap { cursor =>
        cursor.downField("result").values.toSeq.flatten.map(_.hcursor).flatMap {
          _.get[String]("name").toOption
        }
      }
      .toSet
  }

  def metadata(itemName: String, all: Boolean = false): Option[Map[String, Seq[String]]] = {
    request(s"metadata/$itemName/metadata")
      .flatMap(parse(_).toOption)
      .flatMap(_.hcursor.downField("result").focus)
      .map { json =>
        val metadata = parseJsonMetadata(json)
        if (all) metadata else metadata.filterKeys(MetadataFields.contains)
      }
  }

  def upload(itemName: String, filename: String, hdfsPath: String): Boolean = {
    val fileSize = HdfsIO.length(hdfsPath)
    HdfsIO.access(hdfsPath) { in =>
      request(itemName + "/" + filename, s3 = true, put = Some((in, fileSize))).isDefined
    }
  }

  def validateMetadata(metadata: Map[String, Seq[String]]): Option[String] = {
    metadata.keys.find(!MetadataFields.contains(_)).map { field =>
      s"Setting metadata field $field is not permitted."
    }
  }

  def parseJsonMetadata(json: Json): Map[String, Seq[String]] = {
    val cursor = json.hcursor
    cursor.keys.toSet.flatten.map { key =>
      val field = cursor.downField(key)
      val values = field.values.toSeq.flatten.flatMap(_.asString)
      key -> (if (values.isEmpty) field.focus.toSeq.flatMap(_.asString) else values)
    }.toMap
  }
}