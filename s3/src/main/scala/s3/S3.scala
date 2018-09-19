package com.ovoenergy.comms.aws
package s3

import auth.AwsSigner
import common._
import org.http4s.{Uri, client, Method}
import client.{Client, DisposableResponse}
import client.dsl.Http4sClientDsl
import Method._
import cats.effect.Sync
import cats.implicits._
import model.{Key, Etag, Bucket, _}
import org.http4s.headers.ETag

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

  def getObject(bucket: Bucket, key: Key): F[Object[F]] = {

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
      obj <- parseObject(response)
    } yield obj

  }

}
