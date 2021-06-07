package io.github.caeus.tn01

import zio.UIO
import zio.stream.UStream

sealed trait WorkerResult {
}

object WorkerResult {

  final case class Success(elapsed: Long,
                           byteCount: Long) extends WorkerResult

  final case object Timeout extends WorkerResult

}

object Main {

  def randomTextStream:UStream[String] =  ???
  def worker: UIO[WorkerResult] = {
    ???
  }

}
