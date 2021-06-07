package io.github.caeus.tn01

import zio.{UIO, URIO}
import zio.random.Random
import zio.stream.ZStream

import java.time.Instant

sealed trait WorkerResult {
}

object WorkerResult {

  final case class Success(elapsed: Long,
                           byteCount: Long) extends WorkerResult

  final case object Timeout extends WorkerResult

}

final case class ElemReport(byteCount: Long, valid: Boolean)

object Main {

  def randomTextStream: ZStream[Random, Nothing, String] = ZStream.repeatEffect(zio.random.nextString(10))

  def worker: URIO[Random, WorkerResult] = {
    zio.clock.instant.flatMap { startedAt =>
      randomTextStream
        .map {
          text =>
            ElemReport(text.getBytes.length, text.contains("Lpfn"))
        }
        .takeWhile()

    }

    ???
  }

}
