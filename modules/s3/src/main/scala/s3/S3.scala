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

import cats.implicits._
import cats.effect._

import java.nio.ByteBuffer

import org.http4s.syntax.all._
import org.http4s.{Service => _, headers => _, _}
import org.http4s.headers._
import org.http4s.Method._
import org.http4s.Header.Raw
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.dsl.Http4sClientDsl

import scala.concurrent.ExecutionContext
import scalaxml._
import scala.xml.Elem

import auth.AwsSigner
import headers._
import model._
import common._
import common.model._

trait S3[F[_]] {

  def headObject(bucket: Bucket, key: Key): F[Either[Error, ObjectSummary]]

  def getObject(bucket: Bucket, key: Key): Resource[F, Either[Error, Object[F]]]

  def putObject(
      bucket: Bucket,
      key: Key,
      content: ObjectContent[F],
      metadata: Map[String, String] = Map.empty
  ): F[Either[Error, ObjectPut]]

}

object S3 {

  def resource[F[_]: ConcurrentEffect](
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None,
      ec: ExecutionContext = ExecutionContext.global
  ): Resource[F, S3[F]] = {
    BlazeClientBuilder[F](ec).resource.map(client =>
      S3.apply(client, credentialsProvider, region, endpoint)
    )
  }

  def apply[F[_]: Sync](
      client: Client[F],
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      endpoint: Option[Uri] = None
  ): S3[F] = new S3[F] with Http4sClientDsl[F] {

    val signer = AwsSigner[F](credentialsProvider, region, Service.S3)
    val signedClient = signer(client)
    val baseEndpoint = endpoint.getOrElse {
      if (region == Region.`us-east-1`)
        uri"https://s3.amazonaws.com"
      else
        Uri.unsafeFromString(s"https://s3-${region.value}.amazonaws.com")
      // TODO use the total version, we may need a S3 builder that returns F[S3[F]]
    }

    def uri(bucket: Bucket, key: Key) = {
      // TODO It only supports path style access ATM
      val bucketEndpoint = baseEndpoint / bucket.name

      key.value.split("/", -1).foldLeft(bucketEndpoint) { (acc, x) => acc / x }
    }

    def getObject(
        bucket: Bucket,
        key: Key
    ): Resource[F, Either[Error, Object[F]]] =
      Resource.liftF(GET(uri(bucket, key))).flatMap { req =>
        signedClient.run(req).evalMap { response =>
          if (response.status.isSuccess) {
            parseObjectSummary(response).value.rethrow // TODO should lack of etag be an error here?
              .map { summary => Object(summary, response.body).asRight[Error] }
          } else
            response.as[Error].map(_.asLeft[Object[F]])
        }
      }

    def headObject(
        bucket: Bucket,
        key: Key
    ): F[Either[Error, ObjectSummary]] = {

      for {
        request <- GET(uri(bucket, key))
        result <- signedClient.fetch(request) {
          case r if r.status.isSuccess =>
            r.as[ObjectSummary].map(_.asRight[Error])
          case r =>
            r.as[Error].map(_.asLeft[ObjectSummary])
        }
      } yield result
    }

    def putObject(
        bucket: Bucket,
        key: Key,
        content: ObjectContent[F],
        metadata: Map[String, String] = Map.empty
    ): F[Either[Error, ObjectPut]] = {

      def initHeaders: F[Headers] =
        Sync[F]
          .fromEither(`Content-Length`.fromLong(content.contentLength))
          .map { contentLength =>
            Headers
              .of(
                contentLength,
                `Content-Type`(content.mediaType, content.charset)
              )
              .put(metadata.map {
                case (k, v) => Raw(s"${`X-Amz-Meta-`}$k".ci, v)
              }.toSeq: _*)
          }

      val extractContent: F[Array[Byte]] =
        if (content.contentLength > ObjectContent.MaxDataLength) {
          Sync[F].raiseError {
            val msg =
              s"The content is too long to be transmitted in a single chunk, max allowed content length: ${ObjectContent.MaxDataLength}"
            new IllegalArgumentException(msg)
          }
        } else {
          content.data.chunks.compile
            .fold(ByteBuffer.allocate(content.contentLength.toInt))((buffer, chunk) =>
              buffer put chunk.toByteBuffer
            )
            .map(_.array())
        }

      for {
        hs <- initHeaders
        contentAsSingleChunk <- extractContent
        request <- PUT(contentAsSingleChunk, uri(bucket, key), hs.toList: _*)
        result <- signedClient.fetch(request) { r =>
          if (r.status.isSuccess) r.as[ObjectPut].map(_.asRight[Error])
          else r.as[Error].map(_.asLeft[ObjectPut])
        }
      } yield result
    }

    implicit def errorEntityDecoder: EntityDecoder[F, Error] =
      EntityDecoder[F, Elem].flatMapR { elem =>
        val code = Option(elem \ "Code")
          .filter(_.length == 1)
          .map(e => Error.Code(e.text))

        val message = Option(elem \ "Message")
          .filter(_.length == 1)
          .map(_.text)

        val requestId = Option(elem \ "RequestId")
          .filter(_.length == 1)
          .map(id => RequestId(id.text))

        (code, message, requestId)
          .mapN { (code, message, requestId) =>
            val bucketName = elem.child
              .find(_.label == "BucketName")
              .map(node => Bucket(node.text))
            val key =
              elem.child.find(_.label == "Key").map(node => Key(node.text))

            Error(
              code = code,
              requestId = requestId,
              message = message,
              key = key,
              bucketName = bucketName
            )
          }
          .fold[DecodeResult[F, Error]](
            DecodeResult.failure(
              InvalidMessageBodyFailure(
                "Code, RequestId and Message XML elements are mandatory"
              )
            )
          )(error => DecodeResult.success(error))
      }

    implicit def objectPutDecoder: EntityDecoder[F, ObjectPut] =
      new EntityDecoder[F, ObjectPut] {

        override def decode(
            msg: Media[F],
            strict: Boolean
        ): DecodeResult[F, ObjectPut] = {
          msg.headers
            .get(ETag)
            .map(t => ObjectPut(Etag(t.tag.tag)))
            // TODO InvalidMessageBodyFailure is not correct here as there is no body
            .fold[DecodeResult[F, ObjectPut]](
              DecodeResult.failure(
                InvalidMessageBodyFailure("The ETag header must be present")
              )
            )(ok => DecodeResult.success(ok))
        }

        override val consumes: Set[MediaRange] =
          MediaRange.standard.values.toSet
      }

    implicit def objectSummaryDecoder: EntityDecoder[F, ObjectSummary] =
      EntityDecoder.decodeBy(MediaRange.`*/*`)(parseObjectSummary)

    def parseObjectSummary(
        response: Media[F]
    ): DecodeResult[F, ObjectSummary] = {

      val eTag: DecodeResult[F, Etag] = response.headers
        .get(ETag)
        .map(t => DecodeResult.success[F, Etag](Etag(t.tag.tag)))
        .getOrElse(
          DecodeResult.failure[F, Etag](
            MalformedMessageBodyFailure(
              "ETag header must be present on the response"
            )
          )
        )

      val mediaType: DecodeResult[F, MediaType] = response.headers
        .get(`Content-Type`)
        .map(_.mediaType)
        .fold(
          DecodeResult.failure[F, MediaType](
            MalformedMessageBodyFailure(
              "The response needs to contain Media-Type header"
            )
          )
        )(DecodeResult.success[F, MediaType])

      val charset: DecodeResult[F, Option[Charset]] = response.headers
        .get(`Content-Type`)
        .flatMap(_.charset)
        .traverse(DecodeResult.success[F, Charset])

      val contentLength = response.headers
        .get(`Content-Length`)
        .map(_.length)
        .fold(
          DecodeResult.failure[F, Long](
            MalformedMessageBodyFailure(
              "The response needs to contain Content-Length header"
            )
          )
        )(DecodeResult.success[F, Long])

      val metadata: Map[String, String] = response.headers.toList.collect {
        case h if h.name.value.toLowerCase.startsWith(`X-Amz-Meta-`) =>
          h.name.value.substring(`X-Amz-Meta-`.length) -> h.value
      }.toMap

      (eTag, mediaType, charset, contentLength).mapN { (eTag, mediaType, charset, contentLength) =>
        ObjectSummary(
          eTag,
          mediaType,
          contentLength,
          charset,
          metadata
        )
      }
    }

  }

}
