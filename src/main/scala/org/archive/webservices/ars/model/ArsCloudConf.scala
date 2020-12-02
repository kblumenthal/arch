package org.archive.webservices.ars.model

import _root_.io.circe.{Json, JsonObject, parser}
import org.archive.webservices.ars.ArsCloud

import scala.io.Source
import scala.util.Try

object ArsCloudConf {
  val ConfFile = "config/config.json"

  private val cursor = Try {
    val source = Source.fromFile(ConfFile, "utf-8")
    try {
      parser.parse(source.mkString).right.get.hcursor
    } finally {
      source.close()
    }
  }.getOrElse(Json.fromJsonObject(JsonObject.empty).hcursor)

  lazy val aitCollectionPath: String = cursor.get[String]("aitCollectionPath").toOption.getOrElse("data/in")
  lazy val aitCollectionWarcDir: String = cursor.get[String]("aitCollectionWarcDir").toOption.getOrElse("arcs")
  lazy val jobOutPath: String = cursor.get[String]("jobOutPath").toOption.getOrElse("data/out")
  lazy val sparkMaster: String = cursor.get[String]("sparkMaster").toOption.getOrElse("local[*]")
  lazy val baseUrl: String = cursor.get[String]("baseUrl").toOption.getOrElse("http://127.0.0.1:" + ArsCloud.Port)
  lazy val loginUrl: String = cursor.get[String]("loginUrl").toOption.getOrElse("http://127.0.0.1:" + ArsCloud.Port + "/ait/login?next=")
}
