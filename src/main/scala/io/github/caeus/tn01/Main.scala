package io.github.caeus.tn01

import scopt.OParser
import zio._
import zio.clock.Clock
import zio.random.Random
import zio.console.Console
import zio.duration.{Duration, durationInt}
import zio.stream.{UStream, ZStream}

import java.time.Instant

final case class Config(
  maxTime: Int,
  minWordSize: Int,
  maxWordSize: Int,
  workers: Int,
  target: String
)

sealed trait WorkerResult

object WorkerResult {

  final case class Success(elapsed: Long, byteCount: Long) extends WorkerResult

  final case object Timeout extends WorkerResult

}

object Main extends App {

  def wordsProducer(minWordSize: Int, maxWordSize: Int): ZStream[Random, Nothing, String] = ZStream.repeatEffect(
    for {
      toTake <- random.nextIntBetween(minWordSize, maxWordSize + 1)
      word   <- ZIO.effectTotal(scala.util.Random.alphanumeric.take(toTake).mkString)
    } yield word
  )

  def workerResult(startedAt: Instant, maybeCount: Option[Long]): URIO[Clock, WorkerResult] =
    maybeCount.map { count =>
      clock.instant.map { finishedAt =>
        WorkerResult.Success(finishedAt.toEpochMilli - startedAt.toEpochMilli, count)
      }
    }.getOrElse(ZIO.succeed(WorkerResult.Timeout))

  def worker(timeout: Duration, words: UStream[String], target: String): URIO[Clock, WorkerResult] =
    for {
      //When did it start?
      startedAt <- clock.instant
      //It will be None if it timeouted, otherwise it will contain the bytecount
      result <- words
                  .takeUntil(_.contains(target))
                  .map(_.getBytes("UTF-8").length: Long)
                  .runSum
                  .timeout(timeout)
      result <- workerResult(startedAt, result)
    } yield result

  def printResults(results: List[WorkerResult]): RIO[Console, Unit] =
    ZIO.collectAll_(
      results.sortBy {
        case WorkerResult.Success(elapsed, _) => -elapsed
        case _                                => 0
      }.map {
        case WorkerResult.Success(elapsed, count) =>
          console.putStrLn(s"$elapsed $count SUCCESS")
        case _ => console.putStrLn("TIMEOUT")
      }
    )

  def printSummary(results: List[WorkerResult]): RIO[Console, Unit] = {
    val speeds = results.collect { case WorkerResult.Success(elapsed, byteCount) =>
      byteCount.toDouble / elapsed.toDouble
    }
    val averageSpeed = speeds.sum / speeds.size.toDouble
    console.putStrLn(s"Average throughput: $averageSpeed byte/ms")
  }

  def parseArgs(args: List[String]): Task[Config] =
    ZIO {
      val builder = OParser.builder[Config]
      val parser = {
        import builder._
        OParser.sequence(
          programName("tn01"),
          head("tn01", "1.0"),
          help('h', "help"),
          opt[Int]("max-time")
            .text("Seconds given to each worker to find 'Lpfn' (default 60)")
            .action((timeout, config) => config.copy(maxTime = timeout)),
          opt[String]("target")
            .text("Word to be searched for (default 'Lpfn')")
            .action((target, config) => config.copy(target = target)),
          opt[Int]("max-word-size")
            .text("Size of the randomly produced words (min 4, default 10)")
            .action((wordsSize, config) => config.copy(maxWordSize = wordsSize))
            .validate(min => if (min < 4) Left("max word size must be 4 or more") else Right(())),
          opt[Int]("min-word-size")
            .text("Size of the randomly produced words (min 4, default 4)")
            .action((wordsSize, config) => config.copy(maxWordSize = wordsSize))
            .validate(min => if (min < 4) Left("min word size must be 4 or more") else Right(())),
          opt[Int]("num-workers")
            .text("Number of workers")
            .action((workers, config) => config.copy(workers = workers))
        )
      }
      OParser.parse(
        parser,
        args,
        Config(maxTime = 60, minWordSize = 4, maxWordSize = 10, workers = 10, target = "Lpfn")
      )
    }.someOrFail(new IllegalArgumentException())

  def run(config: Config): RIO[ZEnv, Unit] =
    for {
      env <- ZIO.environment[ZEnv]
      results <- ZIO.collectAllPar(
                   List.fill(config.workers)(
                     worker(
                       timeout = config.maxTime.seconds,
                       words = wordsProducer(config.minWordSize, config.maxWordSize).provide(env),
                       target = config.target
                     )
                   )
                 )
      _ <- printResults(results)
      _ <- printSummary(results)
    } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    (for {
      config <- parseArgs(args)
      _      <- run(config)
    } yield ()).exitCode
}
