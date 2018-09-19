package com.ovoenergy.comms.aws
package s3

import auth.AwsSigner
import common._
import org.http4s.{Uri, client, EntityDecoder, Method}
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

class S3[F[_]: Sync](client: Client[F], credentialsProvider: CredentialsProvider[F], region: Region) extends Http4sClientDsl[F] {

  private val signer = AwsSigner[F](credentialsProvider, region, Service.S3)
  private val signedClient = signer(client)

  private val endpoint = if(region == Region.`us-east-1`) {
    Uri.uri("https://s3.amazonaws.com")
  } else {
    // TODO use the total version, we may need a S3 builder that returns F[S3[F]]
    Uri.unsafeFromString(s"https://s3-${region.value}.amazonaws.com")
  }

  // TODO It only supports path style access so far
  private def uri(bucket: Bucket, key: Key) = {
    endpoint / bucket.name / key.value
  }

  private implicit val errorEntityDecoder: EntityDecoder[F, Error] = EntityDecoder[F, Elem].map { elem =>

    // FIXME Handle errors: All of this could be empty and returning ""
    val code = Error.Code((elem \ "Code").text)
    val message = (elem \ "Message").text
    val requestId = RequestId((elem \  "RequestId").text)
    val key = elem.child.find(_.label == "Key").map(node => Key(node.text))

    Error(code, requestId, message, key)
  }

  def getObject(bucket: Bucket, key: Key): F[Either[Error, Object[F]]] = {

    def parseObject(dr: DisposableResponse[F]): F[Object[F]] = {
      val response = dr.response

      response
        .headers
        .get(ETag)
        .fold(Sync[F].raiseError[ETag](new IllegalArgumentException("ETag header must be present on the response")))(_.pure[F])
        .map { etagHeader =>
          val eTag = Etag(etagHeader.tag.tag)
          Object(eTag, response.body.onFinalize(dr.dispose), dr.dispose)
        }
    }

    for {
      request <- GET(uri(bucket, key))
      response <- signedClient.open.apply(request)
      result <- if(response.response.status.isSuccess) {
        parseObject(response).map(_.asRight[Error])
      } else {
        response(_.as[Error]).map(_.asLeft[Object[F]])
      }
    } yield result

  }

}
