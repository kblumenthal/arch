package org.archive.webservices.ars.processing

import scala.concurrent.Future

abstract class ChainedJob extends DerivationJob {
  private var _children: Seq[PartialDerivationJob] = Seq.empty
  def children: Seq[PartialDerivationJob] = _children

  def addJob(child: ChainedJob => PartialDerivationJob): Unit = _children :+= child(this)

  def run(conf: DerivationJobConf): Future[Boolean] = throw new UnsupportedOperationException("Cannot run a chained job: " + id)

  override def history(conf: DerivationJobConf): DerivationJobInstance = {
    val instance = super.history(conf)
    for (finalJob <- _children.lastOption.map(_.history(conf))) {
      if (finalJob.state > ProcessingState.Queued) {
        instance.state = finalJob.state
      } else {
        val firstJob = _children.head.history(conf)
        instance.state = if (firstJob.state > ProcessingState.Running) ProcessingState.Running else firstJob.state
      }
    }
    instance
  }

  override def templateVariables(conf: DerivationJobConf): Seq[(String, Any)] = children.flatMap(_.templateVariables(conf))

  private def onChildComplete(instance: DerivationJobInstance, idx: Int, success: Boolean): Unit = {
    if (success) {
      if (idx + 1 < _children.size) {
        val nextChild = _children(idx + 1)
        val enqueued = nextChild.enqueue(instance.conf, child => child.onStateChanged {
          if (child.state > ProcessingState.Running) onChildComplete(instance, idx + 1, child.state == ProcessingState.Finished)
        })
        if (enqueued.isEmpty) JobManager.unregister(instance)
      } else {
        instance.state = ProcessingState.Finished
      }
    } else {
      instance.state = ProcessingState.Failed
      JobManager.unregister(instance)
    }
  }

  override def enqueue(conf: DerivationJobConf, get: DerivationJobInstance => Unit = _ => {}): Option[DerivationJobInstance] = {
    if (_children.nonEmpty) {
      super.enqueue(conf, get).filter { instance =>
        JobManager.register(instance) && {
          val enqueued = _children.head.enqueue(conf, child => child.onStateChanged {
            if (child.state > ProcessingState.Running) onChildComplete(instance, 0, child.state == ProcessingState.Finished)
            else instance.state = child.state
          })
          if (enqueued.isDefined) true else {
            JobManager.unregister(instance)
            false
          }
        }
      }
    } else None
  }
}