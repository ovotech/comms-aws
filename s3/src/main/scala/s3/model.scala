/*
 * Copyright 2018 OVO Energy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ovoenergy.comms.aws
package s3

import common.model._

import cats.implicits._
import cats.effect.Async

import java.nio.file.{StandardOpenOption, Files, Path}
import java.util.concurrent.{Executors, ThreadFactory}

import fs2.{Stream, Chunk}, fs2.io._
import org.http4s.{MediaType, Charset}

import scala.concurrent.ExecutionContext

object model {

  case class ObjectSummary(eTag: Etag, metadata: Map[String, String])

  /**
    * The S3 Object. Running the content stream or calling dispose will dispose
    * the underling connection
    *
    * @param eTag     The S3 ETag
    * @param content  The Stream[F, Byte] on the object content.
    * @param metadata The metadata stored in the S3 object.
    * @param dispose  This will cause the underlying HTTP response to be closed
    * @tparam F The effect
    */
  case class Object[F[_]](
      summary: ObjectSummary,
      content: Stream[F, Byte],
      dispose: F[Unit])

  case class Error(
      code: Error.Code,
      requestId: RequestId,
      message: String,
      key: Option[Key] = None,
      bucketName: Option[Bucket])

  object Error {

    case class Code(value: String)

  }

  sealed trait StorageClass {

    import StorageClass._

    override def toString: String = this match {
      case Standard => "STANDARD"
      case StandardIa => "STANDARD_IA"
      case OnezoneIa => "ONEZONE_IA"
      case ReducedRedundancy => "REDUCED_REDUNDANCY"
    }
  }

  object StorageClass {

    case object Standard extends StorageClass

    case object StandardIa extends StorageClass

    case object OnezoneIa extends StorageClass

    case object ReducedRedundancy extends StorageClass

    def values: IndexedSeq[StorageClass] = Vector(
      Standard,
      StandardIa,
      OnezoneIa,
      ReducedRedundancy
    )

    def fromString(string: String): Option[StorageClass] = string match {
      case "STANDARD" => Standard.some
      case "STANDARD_IA" => StandardIa.some
      case "ONEZONE_IA" => OnezoneIa.some
      case "REDUCED_REDUNDANCY" => ReducedRedundancy.some
      case _ => none[StorageClass]
    }

    def unsafeFromString(string: String): StorageClass =
      fromString(string)
        .getOrElse(
          throw new IllegalArgumentException(
            s"string is not a valid storage class, valid values are: $values"))

  }

  case class Bucket(name: String)

  case class Key(value: String)

  case class Etag(value: String)

  case class ObjectPutted(etag: Etag)

  // TODO Use something like Byte/KiloByte/Mb/Gb for the length
  case class ObjectContent[F[_]](
      data: Stream[F, Byte],
      contentLength: Long,
      chunked: Boolean,
      mediaType: MediaType = MediaType.`application/octet-stream`,
      charset: Option[Charset] = None)

  object ObjectContent {

    val MaxDataLength: Long = Int.MaxValue.toLong
    val ChunkSize: Int = 64 * 1024

    private val DefaultBlockingEc: ExecutionContext =
      ExecutionContext.fromExecutorService(
        Executors.newFixedThreadPool(16, new ThreadFactory {
          override def newThread(r: Runnable): Thread = {
            val t = Executors.defaultThreadFactory().newThread(r)
            t.setDaemon(true)
            t
          }
        }))

    def fromByteArray[F[_]](
        data: Array[Byte],
        mediaType: MediaType = MediaType.`application/octet-stream`,
        charset: Option[Charset] = None): ObjectContent[F] =
      ObjectContent[F](
        data = Stream.chunk(Chunk.boxed[Byte](data)).covary[F],
        contentLength = data.length.toLong,
        mediaType = mediaType,
        charset = charset,
        chunked = false
      )

    def fromPath[F[_]](
        path: Path,
        blockingEc: ExecutionContext = DefaultBlockingEc)(
        implicit F: Async[F],
        ec: ExecutionContext): F[ObjectContent[F]] =
      for {
        _ <- Async.shift[F](blockingEc)
        contentLength <- F.delay(Files.size(path))
        _ <- if (contentLength > MaxDataLength) {
          F.raiseError[Long](
            new IllegalArgumentException(
              "The file must be smaller than MaxDataLength bytes"))
        } else {
          contentLength.pure[F]
        }
        _ <- Async.shift[F](ec)
      } yield
        ObjectContent(
          readInputStream[F](
            F.delay(Files.newInputStream(path, StandardOpenOption.READ)),
            ChunkSize),
          contentLength,
          chunked = contentLength > ChunkSize)

  }

}
