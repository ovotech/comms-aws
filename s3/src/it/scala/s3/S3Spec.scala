package com.ovoenergy.comms.aws
package s3

import model._
import common.{IntegrationSpec, CredentialsProvider}
import common.model._

import java.nio.file.Files
import java.util.UUID

import cats.implicits._
import cats.effect.IO

import fs2._
import fs2.io._
import fs2.Stream.ToEffect

import org.http4s.client._
import blaze.Http1Client
import middleware.{ResponseLogger, RequestLogger}

import scala.concurrent.duration._

class S3Spec extends IntegrationSpec {

  val existingKey = Key("more.pdf")
  val notExistingKey = Key("less.pdf")

  val existingBucket = Bucket("ovo-comms-test")
  val nonExistingBucket = Bucket("ovo-comms-non-existing-bucket")

  val morePdf = IO(ClassLoader.getSystemClassLoader.getResourceAsStream("more.pdf"))
  val moreSize = IO(ClassLoader.getSystemClassLoader.getResource("more.pdf").openConnection().getContentLength)
  val randomKey = IO(Key(UUID.randomUUID().toString))

  implicit class RichToEffectIO[O](te: ToEffect[IO, O]) {
    def lastOrRethrow: IO[O] =
      te.last
        .map(_.toRight[Throwable](new IllegalStateException("Empty Stream")))
        .rethrow

  }

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), 500.millis)

  "headObject" when {

    "the bucked exists" when {
      "the key does exist" should {
        val key = existingKey
        "return the object eTag" in withS3 { s3 =>
          s3.headObject(existingBucket, key).futureValue.right.map { os =>
            os.eTag shouldBe Etag("9fe029056e0841dde3c1b8a169635f6f")
          }
        }

        "return the object metadata" in withS3 { s3 =>
          s3.headObject(existingBucket, key).futureValue.right.map { os =>
            os.metadata shouldBe Map("is-test"->"true")
          }
        }
      }

      "the key does not exist" should {

        "return a Left" in withS3 { s3 =>
          s3.headObject(existingBucket, notExistingKey).futureValue shouldBe a[Left[_,_]]
        }

        "return NoSuchKey error code" in withS3 { s3 =>
          s3.headObject(existingBucket, notExistingKey).futureValue.left.map { error =>
            error.code shouldBe Error.Code("NoSuchKey")
          }
        }

        "return the given key as resource" in withS3 { s3 =>
          s3.headObject(existingBucket, notExistingKey).futureValue.left.map { error =>
            error.key shouldBe notExistingKey.some
          }
        }

      }
    }

    "the bucked does not exist" should {

      "return a Left" in withS3 { s3 =>
        s3.headObject(nonExistingBucket, existingKey).futureValue shouldBe a[Left[_,_]]
      }


      "return NoSuchBucket error code" in withS3 { s3 =>
        s3.headObject(nonExistingBucket, existingKey).futureValue.left.map { error =>
          error.code shouldBe Error.Code("NoSuchBucket")
        }
      }

      "return the given bucket" in withS3 { s3 =>
        s3.headObject(nonExistingBucket, existingKey).futureValue.left.map { error =>
          error.bucketName shouldBe nonExistingBucket.some
        }
      }

    }

  }

  "getObject" when {

    "the bucked exists" when {
      "the key does exist" should {
        val key = existingKey
        "return the object eTag" in withS3 { s3 =>
          Stream.bracket(s3.getObject(existingBucket, key))(
            objOrError => objOrError
              .fold(e => Stream.raiseError[Object[IO]](new RuntimeException(e.message)),
                ok => Stream.emit(ok)
              ),
            obj => obj.fold(_ => IO.unit, _.dispose))
            .map { obj =>
              obj.summary.eTag shouldBe Etag("9fe029056e0841dde3c1b8a169635f6f")
            }.compile.lastOrRethrow.futureValue
        }

        "return the object metadata" in withS3 { s3 =>
          Stream.bracket(s3.getObject(existingBucket, key))(
            objOrError => objOrError
              .fold(e => Stream.raiseError[Object[IO]](new RuntimeException(e.message)),
                ok => Stream.emit(ok)
              ),
            obj => obj.fold(_ => IO.unit, _.dispose))
            .map { obj =>
              obj.summary.metadata shouldBe Map("is-test"->"true")
            }.compile.lastOrRethrow.futureValue
        }

        "return the object that can be consumed to a file" in withS3 { s3 =>

          Stream.bracket(s3.getObject(existingBucket, key))(
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

          Stream.bracket(s3.getObject(existingBucket, key))(
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
            s3.getObject(existingBucket, notExistingKey))(
            objOrError => Stream.emit(objOrError),
            objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
          ).map { objOrError =>
            objOrError shouldBe a[Left[_,_]]
          }.compile.lastOrRethrow.futureValue
        }

        "return NoSuchKey error code" in withS3 { s3 =>
          Stream.bracket(
            s3.getObject(existingBucket, notExistingKey))(
            objOrError => Stream.emit(objOrError),
            objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
          ).map { objOrError =>
            objOrError.left.map { error =>
              error.code shouldBe Error.Code("NoSuchKey")
            }
          }.compile.lastOrRethrow.futureValue
        }

        "return the given key as resource" in withS3 { s3 =>
          Stream.bracket(
            s3.getObject(existingBucket, notExistingKey))(
            objOrError => Stream.emit(objOrError),
            objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
          ).map { objOrError =>
            objOrError.left.map { error =>
              error.key shouldBe notExistingKey.some
            }
          }.compile.lastOrRethrow.futureValue
        }

      }
    }

    "the bucked does not exist" should {

      "return a Left" in withS3 { s3 =>
        Stream.bracket(
          s3.getObject(nonExistingBucket, notExistingKey))(
          objOrError => Stream.emit(objOrError),
          objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
        ).map { objOrError =>
          objOrError shouldBe a[Left[_, _]]
        }.compile.lastOrRethrow.futureValue
      }


      "return NoSuchBucket error code" in withS3 { s3 =>
        Stream.bracket(
          s3.getObject(nonExistingBucket, notExistingKey))(
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
          s3.getObject(nonExistingBucket, notExistingKey))(
          objOrError => Stream.emit(objOrError),
          objOrError => objOrError.fold(_ => IO.unit, obj => obj.dispose)
        ).map { objOrError =>
          objOrError.left.map { error =>
            error.bucketName shouldBe nonExistingBucket.some
          }

        }.compile.lastOrRethrow.futureValue
      }

    }

  }

  "putObject" when {
    "the bucked exists" should {
      "upload the object content" in withS3 { s3 =>

        val contentIo: IO[ObjectContent[IO]] = moreSize.map { size =>
          ObjectContent(
            readInputStream(morePdf, chunkSize = 64 * 1024),
            size,
            chunked = true
          )
        }

        (for {
          key <- randomKey
          content <- contentIo
          result <- s3.putObject(existingBucket, key, content)
        } yield result).futureValue shouldBe a[Right[_,_]]

      }

      "upload the object content with custom metadata" in withS3 { s3 =>

        val expectedMetadata = Map("test"->"yes")

        val contentIo: IO[ObjectContent[IO]] = moreSize.map { size =>
          ObjectContent(
            readInputStream(morePdf, chunkSize = 64 * 1024),
            size,
            chunked = true
          )
        }

        (for {
          key <- randomKey
          content <- contentIo
          _ <- s3.putObject(existingBucket, key, content, expectedMetadata)
          summary <- s3.headObject(existingBucket, key)
        } yield summary).futureValue.right.map { summary =>
          summary.metadata shouldBe expectedMetadata

        }
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
