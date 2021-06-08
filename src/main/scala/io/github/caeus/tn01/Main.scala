package io.github.caeus.tn01

import scopt.OParser
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration.{Duration, durationInt}
import zio.stream.{UStream, ZStream}

import java.time.Instant

final case class Config(
                         timeout: Int,
                         wordsSize: Int,
                         workers: Int
                       )

sealed trait WorkerResult

object WorkerResult {

  final case class Success(elapsed: Long,
                           byteCount: Long) extends WorkerResult

  final case object Timeout extends WorkerResult

}


object Main extends App {

  def wordsProducer(wordSize: Int): UStream[String] = ZStream.repeatEffect(
    ZIO.effectTotal(scala.util.Random.alphanumeric.take(wordSize).mkString)
  )

  def workerResult(startedAt: Instant, maybeCount: Option[Long]): URIO[Clock, WorkerResult] = {
    maybeCount.map {
      count =>
        clock.instant.map {
          finishedAt =>
            WorkerResult.Success(finishedAt.toEpochMilli - startedAt.toEpochMilli, count)
        }
    }.getOrElse(ZIO.succeed(WorkerResult.Timeout))
  }

  def worker(timeout: Duration, words: UStream[String]): URIO[Clock, WorkerResult] = {
    for {
      //When did it start?
      startedAt <- clock.instant
      //It will be None if it timeouted, otherwise it will contain the bytecount
      result <- words
        .takeUntil(_.contains("Lpfn"))
        .map(_.getBytes("UTF-8").length: Long)
        .runSum
        .timeout(timeout)
      result <- workerResult(startedAt, result)
    } yield result
  }


  def printResults(results: List[WorkerResult]): RIO[Console, Unit] = {
    ZIO.collectAll_(
      results.sortBy {
        case WorkerResult.Success(elapsed, _) => -elapsed
        case _ => 0
      }.map {
        case WorkerResult.Success(elapsed, count) =>
          console.putStrLn(s"$elapsed $count SUCCESS")
        case _ => console.putStrLn("TIMEOUT")
      }
    )
  }

  def printSummary(results: List[WorkerResult]): RIO[Console, Unit] = {
    val speeds = results.collect {
      case WorkerResult.Success(elapsed, byteCount) =>
        byteCount.toDouble / elapsed.toDouble
    }
    val averageSpeed = speeds.sum / speeds.size.toDouble
    console.putStrLn(s"Average throughput: $averageSpeed byte/ms")
  }


  def parseArgs(args: List[String]): Task[Config] = {
    ZIO {
      val builder = OParser.builder[Config]
      val parser = {
        import builder._
        OParser.sequence(
          programName("tn01"),
          head("tn01", "1.0"),
          help('h', "help"),
          opt[Int]('t', "timeout")
            .text("Seconds given to each worker to find 'Lpfn' (default 60)")
            .action((timeout, config) => config.copy(timeout = timeout)),
          opt[Int]("wordsize")
            .text("Size of the randomly produced words (default 10)")
            .action((wordsSize, config) => config.copy(wordsSize = wordsSize)),
          opt[Int]('w', "workers")
            .text("Number of workers")
            .action((workers, config) => config.copy(workers = workers))
        )
      }
      OParser.parse(parser, args, Config(timeout = 60, wordsSize = 10, workers = 10))
    }.someOrFail(new IllegalArgumentException())
  }

  def run(config: Config): RIO[Console with Clock, Unit] = {
    for {
      results <- ZIO.collectAllPar(List.fill(config.workers)(worker(timeout = 30.seconds, wordsProducer(config.wordsSize))))
      _ <- printResults(results)
      _ <- printSummary(results)
    } yield ()
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    (for {
      config <- parseArgs(args)
      _ <- run(config)
    } yield ()).exitCode
  }
}
