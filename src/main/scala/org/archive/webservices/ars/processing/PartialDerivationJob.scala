package org.archive.webservices.ars.processing
import org.archive.webservices.ars.model.{ArsCloudJobCategories, ArsCloudJobCategory}

abstract class PartialDerivationJob(parent: ChainedJob) extends DerivationJob {
  override lazy val id: String = parent.id + "_" + super.id
  def name: String = id
  def category: ArsCloudJobCategory = ArsCloudJobCategories.None
  def description: String = id
  override def templateName: Option[String] = None
  override def enqueue(
      conf: DerivationJobConf,
      get: DerivationJobInstance => Unit = _ => {}): Option[DerivationJobInstance] =
    super.enqueue(conf, get)
}
