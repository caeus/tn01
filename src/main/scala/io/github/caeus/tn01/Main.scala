package io.github.caeus.tn01

import zio.duration.{Duration, durationInt}
import zio._
import zio.random.Random
import zio.clock.Clock
import zio.stream.ZStream

import java.time.Instant

sealed trait WorkerResult {
}

object WorkerResult {

  final case class Success(elapsed: Long,
                           byteCount: Long) extends WorkerResult

  final case object Timeout extends WorkerResult

}


object Main extends App {

  def randomTextStream: ZStream[Random, Nothing, String] = ZStream.repeatEffect(zio.random.nextString(10))

  def workerResult(startedAt: Instant, maybeCount: Option[Long]): URIO[Clock, WorkerResult] = {
    maybeCount.map {
      count =>
        clock.instant.map {
          finishedAt =>
            WorkerResult.Success(finishedAt.toEpochMilli - startedAt.toEpochMilli, count)
        }
    }.getOrElse(ZIO.succeed(WorkerResult.Timeout))
  }

  def worker(timeout: Duration): URIO[Random with Clock, WorkerResult] = {
    for {
      //When did it start?
      startedAt <- clock.instant
      //It will be None if it timeouted, otherwise it will contain the bytecount
      result <- randomTextStream
        .takeUntil(_.contains("Lpfn"))
        .map(_.getBytes("UTF-8").length: Long)
        .runSum
        .timeout(timeout)
      result <- workerResult(startedAt, result)
    } yield result
  }


  def printResults(results:List[WorkerResult])={

  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    ZIO.collectAllPar(List.fill(10)(worker(timeout =  60.seconds)))
      .map(_.sortBy {
        case WorkerResult.Success(elapsed, _) => elapsed
        case WorkerResult.Timeout => 0L
      })
      .map{
        reports=>
          ZIO.co
      }

    ???
  }
}
