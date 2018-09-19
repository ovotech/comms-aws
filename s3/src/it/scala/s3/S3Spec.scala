package com.ovoenergy.comms.aws
package s3

import cats.implicits._
import cats.effect.IO
import model._
import common.{IntegrationSpec, Region, CredentialsProvider}
import fs2.Stream.ToEffect
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import fs2._
import org.http4s.client.middleware.{ResponseLogger, RequestLogger}

import scala.concurrent.duration._

class S3Spec extends IntegrationSpec {

  implicit class RichToEffectIO[O](te: ToEffect[IO, O]) {
    def lastOrRethrow: IO[O] =
      te.last
      .map(_.toRight[Throwable](new IllegalStateException("Empty Stream")))
      .rethrow

  }

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), 500.millis)

  "getObject" when {
    "the object with the given bucket and key exists" should {
      "return the object" in withS3 { s3 =>
        Stream.bracket(s3.getObject(Bucket("ovo-comms-test"), Key("more.pdf")))(obj => Stream.emit(obj), obj => obj.dispose)
            .map { obj =>
              obj.eTag shouldBe Etag("9fe029056e0841dde3c1b8a169635f6f")
            }
          .compile
          .lastOrRethrow
          .futureValue
      }
    }

    "the object with the given bucket and key does not exists" should {
      "return the error" in withS3 { s3 =>
        Stream.bracket(s3.getObject(Bucket("ovo-comms-test"), Key("less.pdf")))(obj => Stream.emit(obj), obj => obj.dispose)
          .attempt
          .compile
          .lastOrRethrow
          .futureValue shouldBe a[Left[_,_]]
      }
    }
  }


  def withS3[A](f: S3[IO] => A): A = {
    Http1Client
      .stream[IO]()
      .map { client =>
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger.apply0[IO](logBody = true, logHeaders = true)
        val requestLogger: Client[IO] => Client[IO] = RequestLogger.apply0[IO](logBody = false, logHeaders = true)
        new S3[IO](requestLogger(responseLogger(client)), CredentialsProvider.default[IO], Region.`eu-west-1`)
      }
      .map(f)
      .compile
      .lastOrRethrow
      .futureValue
  }

}
