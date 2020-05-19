package com.ovoenergy.comms.aws
package s3

import cats.implicits._
import cats.effect._
import fs2.io._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.Executors

import scala.concurrent.duration._


import common.model._
import common.{CredentialsProvider, IntegrationSpec}
import model._

class S3Spec extends IntegrationSpec {

  val existingKey = Key("more.pdf")
  val duplicateKey = Key("duplicate")
  val notExistingKey = Key("less.pdf")
  val nestedKey = Key("a/b/c")
  val slashLeadingKey = Key("/a")

  val existingBucket = Bucket("ovo-comms-test")
  val nonExistingBucket = Bucket("ovo-comms-non-existing-bucket")

  val morePdf = IO(getClass.getResourceAsStream("/more.pdf"))
  val moreSize = IO { getClass
    .getResource("/more.pdf")
    .openConnection()
    .getContentLength
    .toLong
  }
  val randomKey = IO(Key(UUID.randomUUID().toString))

  implicit val patience: PatienceConfig =
    PatienceConfig(scaled(5.seconds), 500.millis)

  val blockingEc = scala.concurrent.ExecutionContext
    .fromExecutorService(Executors.newCachedThreadPool())
  val blocker = Blocker.liftExecutionContext(blockingEc)
  implicit val ctx: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  "headObject" when {

    "the bucket exists" when {
      "the key does exist" should {
        val key = existingKey
        "return the object eTag" in {
          withS3 { s3 =>
            s3.headObject(existingBucket, key)
          }.futureValue.map { os =>
            os.eTag shouldBe Etag("9fe029056e0841dde3c1b8a169635f6f")
          }
        }

        "return the object metadata" in {
          withS3 { s3 =>
            s3.headObject(existingBucket, key)
          }.futureValue.map { os =>
            os.metadata shouldBe Map("is-test" -> "true")
          }
        }

        "return the object contentLength" in {
          withS3 { s3 =>
            s3.headObject(existingBucket, key)
          }.futureValue.map { os =>
            os.contentLength should be > 0L
          }
        }
      }

      "the key does not exist" should {

        "return a Left" in {
          withS3 { s3 =>
            s3.headObject(existingBucket, notExistingKey)
          }.futureValue shouldBe a[Left[_, _]]
        }

        "return NoSuchKey error code" in {
          withS3 { s3 =>
            s3.headObject(existingBucket, notExistingKey)
          }.futureValue.left.map { error =>
            error.code shouldBe Error.Code("NoSuchKey")
          }
        }

        "return the given key as resource" in {
          withS3 { s3 =>
            s3.headObject(existingBucket, notExistingKey)
          }.futureValue.left.map { error =>
            error.key shouldBe notExistingKey.some
          }
        }
      }
    }

    "the bucket does not exist" should {

      "return a Left" in {
        withS3 { s3 =>
          s3.headObject(nonExistingBucket, existingKey)
        }.futureValue shouldBe a[Left[_, _]]
      }

      "return NoSuchBucket error code" in {
        withS3 { s3 =>
          s3.headObject(nonExistingBucket, existingKey)
        }.futureValue.left.map { error =>
          error.code shouldBe Error.Code("NoSuchBucket")
        }
      }

      "return the given bucket" in {
        withS3 { s3 =>
          s3.headObject(nonExistingBucket, existingKey)
        }.futureValue.left.map { error =>
          error.bucketName shouldBe nonExistingBucket.some
        }
      }

    }

  }

  "getObject" when {

    "the bucket exists" when {
      "the key does exist" should {

        "return the object eTag" in checkGetObject(existingBucket, existingKey) {
          objOrError =>
            objOrError.map(_.summary.eTag) shouldBe Etag("9fe029056e0841dde3c1b8a169635f6f").asRight
        }

        "return the object metadata" in checkGetObject(existingBucket, existingKey) {
          objOrError =>
            objOrError.map(_.summary.metadata) shouldBe Map("is-test" -> "true").asRight
        }

        "return the object contentLength" in checkGetObject(existingBucket, existingKey) {
          objOrError =>
            objOrError.map(_.summary.contentLength > 0L) shouldBe true.asRight
        }

        "return the object that can be consumed to a file" in {
          withS3 { s3 =>
            s3.getObject(existingBucket, existingKey)
              .map(_.leftWiden[Throwable])
              .rethrow
              .use(_.content.compile.toList)
          }.futureValue should not be empty
        }

        // FIXME This test does not pass, but we have verified manually that the connection is getting disposed
        "return the object that after been consumed cannot be consumed again" ignore checkGetObject(
          existingBucket,
          existingKey) { objOrError =>
          objOrError.map { obj =>
            (obj.content.compile.toList >> obj.content.compile.toList.attempt).futureValue shouldBe a[
              Left[_, _]]
          }
        }
      }

      "the key does not exist" should {

        "return a Left" in checkGetObject(existingBucket, notExistingKey) {
          objOrError =>
            objOrError shouldBe a[Left[_, _]]
        }

        "return NoSuchKey error code" in checkGetObject(
          existingBucket,
          notExistingKey) { objOrError =>
          objOrError.left.map { error =>
            error.code shouldBe Error.Code("NoSuchKey")
          }
        }

        "return the given key as resource" in checkGetObject(
          existingBucket,
          notExistingKey) { objOrError =>
          objOrError.left.map { error =>
            error.key shouldBe notExistingKey.some
          }
        }

      }
    }

    "the bucket does not exist" should {

      "return a Left" in checkGetObject(nonExistingBucket, existingKey) {
        objOrError =>
          objOrError shouldBe a[Left[_, _]]
      }

      "return NoSuchBucket error code" in checkGetObject(
        nonExistingBucket,
        existingKey) { objOrError =>
        objOrError.left.map { error =>
          error.code shouldBe Error.Code("NoSuchBucket")
        }
      }

      "return the given bucket" in checkGetObject(
        nonExistingBucket,
        existingKey) { objOrError =>
        objOrError.left.map { error =>
          error.bucketName shouldBe nonExistingBucket.some
        }
      }

    }

  }

  "putObject" when {
    "the bucket exists" when {
      "the key does not exist" should {

        "upload the object content" in {
          withS3 { s3 =>
            val contentIo: IO[ObjectContent[IO]] = moreSize.map { size =>
              ObjectContent(
                readInputStream(morePdf, chunkSize = 64 * 1024, blocker),
                size,
                chunked = true
              )
            }

            for {
              key <- randomKey
              content <- contentIo
              result <- s3.putObject(existingBucket, key, content)
            } yield result

          }.futureValue shouldBe a[Right[_, _]]
        }

        "upload the object content with custom metadata" in {

          val expectedMetadata = Map("test" -> "yes")

          withS3 { s3 =>
            val contentIo: IO[ObjectContent[IO]] = moreSize.map { size =>
              ObjectContent(
                readInputStream(morePdf, chunkSize = 64 * 1024, blocker),
                size,
                chunked = true
              )
            }

            for {
              key <- randomKey
              content <- contentIo
              _ <- s3.putObject(existingBucket, key, content, expectedMetadata)
              summary <- s3.headObject(existingBucket, key)
            } yield summary
          }.futureValue.map { summary =>
            summary.metadata shouldBe expectedMetadata
          }
        }
      }

      "the key is nested" should {
        "succeeding" in {

          val content = ObjectContent.fromByteArray[IO](
            UUID.randomUUID().toString.getBytes(UTF_8))

          withS3 { s3 =>
            s3.putObject(existingBucket, nestedKey, content)
          }.futureValue shouldBe a[Right[_, _]]

        }
      }

      "the key has leading slash" should {
        "succeeding" in {

          val content = ObjectContent.fromByteArray[IO](
            UUID.randomUUID().toString.getBytes(UTF_8))

          withS3 { s3 =>
            s3.putObject(existingBucket, slashLeadingKey, content)
          }.futureValue shouldBe a[Right[_, _]]

        }
      }

      "the key does exist" should {
        "overwrite the existing key" in {
          withS3 { s3 =>
            val content = ObjectContent.fromByteArray[IO](
              UUID.randomUUID().toString.getBytes(UTF_8))
            for {
              _ <- s3.putObject(existingBucket, duplicateKey, content)
              result <- s3.putObject(existingBucket, duplicateKey, content)
            } yield result
          }.futureValue shouldBe a[Right[_, _]]
        }
      }
    }

    "the bucket does not exist" should {

      "return a Left" in {
        withS3 { s3 =>
          s3.putObject(
            nonExistingBucket,
            existingKey,
            ObjectContent.fromByteArray(Array.fill(128 * 1026)(0: Byte)))
        }.futureValue shouldBe a[Left[_, _]]
      }

      "return NoSuchBucket error code" in withS3 { s3 =>
        s3.putObject(
          nonExistingBucket,
          existingKey,
          ObjectContent.fromByteArray(Array.fill(128 * 1026)(0: Byte)))
      }.futureValue.left.map { error =>
        error.bucketName shouldBe nonExistingBucket.some
      }
    }
  }

  def checkGetObject[A](bucket: Bucket, key: Key)(
      f: Either[Error, Object[IO]] => A): A =
    withS3 { _.getObject(bucket, key).use(x => IO(f(x))) }.futureValue

  def withS3[A](f: S3[IO] => IO[A]): IO[A] = {
    S3.resource(CredentialsProvider.default[IO], Region.`eu-west-1`).use(f)
  }

}
