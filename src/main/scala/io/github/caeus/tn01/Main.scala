package io.github.caeus.tn01

import scopt.OEffect.Terminate
import scopt.{DefaultOEffectSetup, OEffectSetup, OParser}
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
final case class ExpectedEnd(msg: Option[String]) extends Throwable(msg.orNull, null, true, false)

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
    if (speeds.isEmpty)
      console.putStrLn("Not even one worker finished successfully")
    else {
      val averageSpeed = speeds.sum / speeds.size.toDouble
      console.putStrLn(s"Average throughput: $averageSpeed byte/ms")
    }
  }

  def parseArgs(args: List[String]): Task[Config] =
    ZIO {
      val builder = OParser.builder[Option[Config]]
      val parser = {
        import builder._
        OParser.sequence(
          programName("tn01"),
          head("tn01", "1.0"),
          help('h', "help")
            .action((_, _) => None),
          opt[Int]("max-time")
            .text("Seconds given to each worker to find target word (default 60)")
            .action((timeout, config) => config.map(_.copy(maxTime = timeout))),
          opt[String]("target")
            .text("Word to be searched for (default 'Lpfn')")
            .action((target, config) => config.map(_.copy(target = target))),
          opt[Int]("max-word-size")
            .text("Size of the randomly produced words (min 4, default 10)")
            .action((wordsSize, config) => config.map(_.copy(maxWordSize = wordsSize)))
            .validate(min => if (min < 4) Left("max word size must be 4 or more") else Right(())),
          opt[Int]("min-word-size")
            .text("Size of the randomly produced words (min 4, default 4)")
            .action((wordsSize, config) => config.map(_.copy(maxWordSize = wordsSize)))
            .validate(min => if (min < 4) Left("min word size must be 4 or more") else Right(())),
          opt[Int]("num-workers")
            .text("Number of workers")
            .action((workers, config) => config.map(_.copy(workers = workers)))
        )
      }
      val maybeConfig = OParser
        .parse(
          parser,
          args,
          Some(Config(maxTime = 60, minWordSize = 4, maxWordSize = 10, workers = 10, target = "Lpfn")),
          esetup = new DefaultOEffectSetup {
            override def terminate(exitState: Either[String, Unit]): Unit = exitState match {
              case Left(value: String) => throw ExpectedEnd(Some(value))
              case Right(_)            => throw ExpectedEnd(None)
            }
          }
        )
        .flatten
      maybeConfig
    }.someOrFail(ExpectedEnd(None))

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
    } yield ()).foldM(
      {
        case ExpectedEnd(Some(msg)) => console.putStrLn(msg).ignore as ExitCode.success
        case ExpectedEnd(None)      => ZIO.unit as ExitCode.success
        case err                    => UIO(err.printStackTrace()) as ExitCode.failure
      },
      _ => ZIO.succeed(ExitCode.success)
    )
}
