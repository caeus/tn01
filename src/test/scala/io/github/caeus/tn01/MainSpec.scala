package io.github.caeus.tn01

import zio.stream.{ZSink, ZStream}
import zio.test._

object MainSpec extends DefaultRunnableSpec {

  override def spec = testM("takeWhile") {
    //I do this test because documentation is not clear the difference between takeWhile and takeUntil
    assertM(ZStream.repeat(false).takeWhile(identity)
      .run(ZSink.collectAll[Boolean]))(Assertion.hasSize(Assertion.equalTo(1)))
  }
}
