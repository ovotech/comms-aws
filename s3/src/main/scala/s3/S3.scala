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

import auth.AwsSigner
import common._
import org.http4s.{
  DecodeResult,
  Uri,
  InvalidMessageBodyFailure,
  client,
  Method,
  EntityDecoder
}
import client.{Client, DisposableResponse}
import client.dsl.Http4sClientDsl
import Method._
import cats.effect.Sync
import cats.implicits._
import com.ovoenergy.comms.aws.common.model.RequestId
import model.{Key, Etag, Bucket, _}
import org.http4s.headers.ETag
import org.http4s.scalaxml._

import scala.xml.Elem

class S3[F[_]: Sync](
    client: Client[F],
    credentialsProvider: CredentialsProvider[F],
    region: Region)
    extends Http4sClientDsl[F] {

  private val signer = AwsSigner[F](credentialsProvider, region, Service.S3)
  private val signedClient = signer(client)

  private val endpoint = if (region == Region.`us-east-1`) {
    Uri.uri("https://s3.amazonaws.com")
  } else {
    // TODO use the total version, we may need a S3 builder that returns F[S3[F]]
    Uri.unsafeFromString(s"https://s3-${region.value}.amazonaws.com")
  }

  // TODO It only supports path style access so far
  private def uri(bucket: Bucket, key: Key) = {
    endpoint / bucket.name / key.value
  }

  private implicit val errorEntityDecoder: EntityDecoder[F, Error] =
    EntityDecoder[F, Elem].flatMapR { elem =>
      val code = Option(elem \ "Code")
        .filter(_.length == 1)
        .map(_.text)
        .map(Error.Code.apply)

      val message = Option(elem \ "Message")
        .filter(_.length == 1)
        .map(_.text)

      val requestId = Option(elem \ "RequestId")
        .filter(_.length == 1)
        .map(_.text)
        .map(RequestId.apply)

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
          DecodeResult.failure(InvalidMessageBodyFailure(
            "Code, RequestId and Message XML elements are mandatory"))
        )(error => DecodeResult.success(error))
    }

  /**
    * Return an S3 [[Object]] in the given bucket and key. If the object does not exist, it will return an [[Error]].
    *
    * BEWARE: that once the returned effect is resolved and the result is a [[Object]] you will need either to run the
    * body or resolving te [[Object.dispose]] effect to release the HTTP response.
    */
  def getObject(bucket: Bucket, key: Key): F[Either[Error, Object[F]]] = {

    def parseObject(dr: DisposableResponse[F]): F[Object[F]] = {
      val response = dr.response

      response.headers
        .get(ETag)
        .fold(Sync[F].raiseError[ETag](new IllegalArgumentException(
          "ETag header must be present on the response")))(_.pure[F])
        .map { etagHeader =>
          val eTag = Etag(etagHeader.tag.tag)
          Object(eTag, response.body.onFinalize(dr.dispose), dr.dispose)
        }
    }

    for {
      request <- GET(uri(bucket, key))
      response <- signedClient.open.apply(request)
      result <- if (response.response.status.isSuccess) {
        parseObject(response).map(_.asRight[Error])
      } else {
        response(_.as[Error]).map(_.asLeft[Object[F]])
      }
    } yield result

  }

}
