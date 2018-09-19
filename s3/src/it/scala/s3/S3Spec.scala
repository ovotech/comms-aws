package com.ovoenergy.comms.aws
package s3

import java.nio.file.Files

import cats.implicits._
import cats.effect.IO
import model._
import common.{IntegrationSpec, Region, CredentialsProvider}
import fs2.Stream.ToEffect
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import fs2._
import org.http4s.client.middleware.{ResponseLogger, RequestLogger}
import fs2.io._

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

    "the bucked exists" when {
      val bucket = Bucket("ovo-comms-test")
      "the key does exist" should {
        val key = Key("more.pdf")
        "return the object eTag" in withS3 { s3 =>
          Stream.bracket(s3.getObject(bucket, key))(
            objOrError => objOrError
              .fold(e => Stream.raiseError[Object[IO]](new RuntimeException(e.message)),
                ok => Stream.emit(ok)
              ),
            obj => obj.fold(_ => IO.unit, _.dispose))
            .map { obj =>
              obj.eTag shouldBe Etag("9fe029056e0841dde3c1b8a169635f6f")
            }.compile.lastOrRethrow.futureValue
        }

        "return the object that can be consumed to a file" in withS3 { s3 =>

          Stream.bracket(s3.getObject(bucket, key))(
            objOrError => objOrError
              .fold(e => Stream.raiseError[Object[IO]](new RuntimeException(e.message)),
                ok => Stream.emit(ok)
              ),
            obj => obj.fold(_ => IO.unit, _.dispose))
            .map { obj =>
              val out = IO(Files.createTempFile("comms-aws", key.value))
                .flatMap { path =>
                IO(Files.newOutputStream(path))
              }
              obj.content.to(writeOutputStream[IO](out)).compile.lastOrRethrow.attempt.futureValue shouldBe a[Right[_,_]]
            }.compile.lastOrRethrow.futureValue
        }

        "return the object that after disposed cannot be consumed" in withS3 { s3 =>

          Stream.bracket(s3.getObject(bucket, key))(
            objOrError => objOrError
              .fold(e => Stream.raiseError[Object[IO]](new RuntimeException(e.message)),
                ok => Stream.emit(ok)
              ),
            obj => obj.fold(_ => IO.unit, _.dispose))
            .map { obj =>
              (obj.dispose >> obj.content.compile.toList.attempt).futureValue shouldBe a[Left[_,_]]
            }.compile.lastOrRethrow.futureValue
        }
      }

      "the key does not exist" should {
        "return a Left" in withS3 { s3 =>
          Stream.bracket(
            s3.getObject(Bucket("ovo-comms-test"), Key("less.pdf")))(
            objOrError => Stream.emit(objOrError),
            objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
          ).map { objOrError =>
            objOrError shouldBe a[Left[_,_]]
          }.compile.lastOrRethrow.futureValue
        }

        "return NoSuchKey error code" in withS3 { s3 =>
          Stream.bracket(
            s3.getObject(Bucket("ovo-comms-test"), Key("less.pdf")))(
            objOrError => Stream.emit(objOrError),
            objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
          ).map { objOrError =>
            objOrError.left.map { error =>
              error.code shouldBe Error.Code("NoSuchKey")
            }
          }.compile.lastOrRethrow.futureValue
        }

        "return the given key as resource" in withS3 { s3 =>
          val key = Key("less.pdf")
          Stream.bracket(
            s3.getObject(Bucket("ovo-comms-test"), key))(
            objOrError => Stream.emit(objOrError),
            objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
          ).map { objOrError =>
            objOrError.left.map { error =>
              error.key shouldBe key.some
            }
          }.compile.lastOrRethrow.futureValue
        }

      }
    }

    "the bucked does not exist" should {
      val bucket = Bucket("ovo-comms-non-existing-bucket")

      "return a Left" in withS3 { s3 =>
        Stream.bracket(
          s3.getObject(bucket, Key("less.pdf")))(
          objOrError => Stream.emit(objOrError),
          objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
        ).map { objOrError =>
          objOrError shouldBe a[Left[_, _]]
        }.compile.lastOrRethrow.futureValue
      }


      "return NoSuchBucket error code" in withS3 { s3 =>
        Stream.bracket(
          s3.getObject(bucket, Key("less.pdf")))(
          objOrError => Stream.emit(objOrError),
          objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
        ).map { objOrError =>
          objOrError.left.map { error =>
            error.code shouldBe Error.Code("NoSuchBucket")
          }

        }.compile.lastOrRethrow.futureValue
      }

      "return the given bucket" in withS3 { s3 =>
        Stream.bracket(
          s3.getObject(bucket, Key("less.pdf")))(
          objOrError => Stream.emit(objOrError),
          objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
        ).map { objOrError =>
          objOrError.left.map { error =>
            error.bucketName shouldBe bucket.some
          }

        }.compile.lastOrRethrow.futureValue
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
