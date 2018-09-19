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
package auth

import AwsSigner._
import common._
import headers.{`X-Amz-Content-SHA256`, `X-Amz-Security-Token`, `X-Amz-Date`}

import scala.util.matching.Regex
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{ZoneOffset, Clock, Instant}

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter
import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import fs2.hash._
import org.http4s.{Request, HttpDate}
import org.http4s.Header.Raw
import org.http4s.client.{Client, DisposableResponse}
import org.http4s.headers.{Date, Authorization, Host}
import org.http4s.syntax.all._

object AwsSigner {

  val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd")

  val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

  val DoubleSlashRegex: Regex = "/{2,}".r
  val MultipleSpaceRegex: Regex = "\\s+".r
  val EncodedSlashRegex: Regex = "%2F".r

  def encodeHex(bytes: Array[Byte]): String =
    DatatypeConverter.printHexBinary(bytes).toLowerCase

  def uriEncode(str: String): String =
    URLEncoder.encode(str, StandardCharsets.UTF_8.name)

  def signWithKey(
      key: SecretKeySpec,
      bytes: Array[Byte],
      algorithm: String): Array[Byte] = {
    val mac = Mac.getInstance(algorithm)
    mac.init(key)
    mac.doFinal(bytes)
  }

  def key(
      formattedDate: String,
      credentials: Credentials,
      region: Region,
      service: Service,
      algorithm: String): SecretKeySpec = {

    def wrapSignature(
        signature: SecretKeySpec,
        bytes: Array[Byte]): SecretKeySpec =
      new SecretKeySpec(signWithKey(signature, bytes, algorithm), algorithm)

    val rawKey = new SecretKeySpec(
      s"AWS4${credentials.secretAccessKey.value}".getBytes,
      algorithm)

    val dateKey: SecretKeySpec =
      wrapSignature(rawKey, formattedDate.getBytes)

    val dateRegionKey: SecretKeySpec =
      wrapSignature(dateKey, region.value.getBytes)

    val dateRegionServiceKey: SecretKeySpec =
      wrapSignature(dateRegionKey, service.value.getBytes)

    wrapSignature(dateRegionServiceKey, "aws4_request".getBytes)
  }

  def hashBody[F[_]: Sync](request: Request[F]): F[String] = {
    request.body
      .through(sha256)
      .fold(Vector.empty[Byte])(_ :+ _)
      .map(xs => encodeHex(xs.toArray))
      .compile
      .last
      .map(x => x.toRight[Throwable](new IllegalStateException))
      .rethrow

  }

  def extractXAmzDateOrDate[F[_]](request: Request[F]): Option[Instant] = {
    request.headers
      .get(`X-Amz-Date`)
      .map(_.date)
      .orElse(request.headers.get(Date).map(_.date))
      .map(_.toInstant)
  }

  def fixRequest[F[_]](
      request: Request[F],
      credentials: Credentials,
      fallbackRequestDateTime: Instant)(implicit F: Sync[F]): F[Request[F]] = {

    val requestDateTime: Instant =
      extractXAmzDateOrDate(request).getOrElse(fallbackRequestDateTime)

    def addHostHeader(r: Request[F]): F[Request[F]] =
      if (r.headers.get(Host).isEmpty) {
        val uri = r.uri
        uri.host.fold[F[Request[F]]](F.raiseError(new IllegalArgumentException(
          "The request URI must be absolute or the request must have the Host header")))(
          host => r.putHeaders(Host(host.value, r.uri.port)).pure[F])
      } else {
        r.pure[F]
      }

    def addXAmzDateHeader(r: Request[F]): F[Request[F]] =
      (if (r.headers.get(Date).isEmpty && r.headers.get(`X-Amz-Date`).isEmpty) {
         r.putHeaders(`X-Amz-Date`(HttpDate.unsafeFromInstant(requestDateTime)))
       } else {
         r
       }).pure[F]

    def addXAmzSecurityTokenHeader(r: Request[F]): F[Request[F]] =
      credentials.sessionToken
        .fold(r)(sessionToken =>
          r.putHeaders(`X-Amz-Security-Token`(sessionToken)))
        .pure[F]

    def addHashedBody(r: Request[F]): F[Request[F]] =
      if (r.headers.get(`X-Amz-Content-SHA256`).isEmpty) {

        // TODO Add chunking support for S3
        def unChunk(request: Request[F]): F[Request[F]] =
          if (r.isChunked) {
            val bodyAsBytes: F[List[Byte]] = request.body.compile.toList
            bodyAsBytes.map(bs => r.withBodyStream(fs2.Stream.emits(bs)))
          } else {
            r.pure[F]
          }

        for {
          unChunked <- unChunk(r)
          hashedBody <- hashBody(unChunked)
        } yield unChunked.putHeaders(`X-Amz-Content-SHA256`(hashedBody))
      } else {
        r.pure[F]
      }

    for {
      requestWithHost <- addHostHeader(request)
      requestWithDate <- addXAmzDateHeader(requestWithHost)
      requestWithSessionToken <- addXAmzSecurityTokenHeader(requestWithDate)
      requestWithHashedBody <- addHashedBody(requestWithSessionToken)
    } yield requestWithHashedBody
  }

  def signRequest[F[_]: Sync](
      request: Request[F],
      credentials: Credentials,
      region: Region,
      service: Service): F[Request[F]] = {

    // FIXME Algorithm could be customized depending on the service
    val algorithm: String = "AWS4-HMAC-SHA256"
    val signingAlgorithm: String = "HmacSHA256"
    val digestAlgorithm: String = "SHA-256"

    val hashedPayloadF: F[String] = {
      val headerValue = request.headers
        .get(`X-Amz-Content-SHA256`)
        .map(_.hashedContent)

      headerValue.fold(hashBody(request))(_.pure[F])
    }

    val requestDateTimeF = extractXAmzDateOrDate(request)
      .fold(
        Sync[F].raiseError[Instant](new IllegalArgumentException(
          "The given request does not have Date or X-Amz-Date header")))(
        _.pure[F])

    (hashedPayloadF, requestDateTimeF).mapN {
      (hashedPayload, requestDateTime) =>
        val formattedDateTime = requestDateTime
          .atOffset(ZoneOffset.UTC)
          .format(AwsSigner.dateTimeFormatter)

        val formattedDate =
          requestDateTime
            .atOffset(ZoneOffset.UTC)
            .format(AwsSigner.dateFormatter)

        val scope =
          s"$formattedDate/${region.value}/${service.value}/aws4_request"

        val (canonicalHeaders, signedHeaders) = {

          val grouped = request.headers.groupBy(_.name)
          val combined = grouped.mapValues(_.map(h =>
            MultipleSpaceRegex.replaceAllIn(h.value, " ").trim).mkString(","))

          val canonical = combined.toSeq
            .sortBy(_._1)
            .map { case (k, v) => s"${k.value.toLowerCase}:$v\n" }
            .mkString("")

          val signed: String =
            request.headers
              .map(_.name.value.toLowerCase)
              .toSeq
              .distinct
              .sorted
              .mkString(";")

          canonical -> signed
        }

        val canonicalRequest = {

          val method = request.method.name.toUpperCase

          val canonicalUri = {
            val absolutePath =
              if (request.uri.path.startsWith("/")) request.uri.path
              else "/" ++ request.uri.path

            // you do not normalize URI paths for requests to Amazon S3
            val normalizedPath = if (service != Service.S3) {
              DoubleSlashRegex.replaceAllIn(absolutePath, "/")
            } else {
              absolutePath
            }

            /* FIXME: This should split the path segments and encode each rather than
             *        decode the slashes afterward
             */
            EncodedSlashRegex.replaceAllIn(uriEncode(normalizedPath), "/")
          }

          val canonicalQueryString: String =
            request.uri.query
              .sortBy(_._1)
              .map {
                case (a, b) => s"${uriEncode(a)}=${uriEncode(b.getOrElse(""))}"
              }
              .mkString("&")

          s"$method\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\n$hashedPayload"
        }

        val stringToSign = {
          val digest = MessageDigest.getInstance(digestAlgorithm)
          val hashedRequest =
            encodeHex(digest.digest(canonicalRequest.getBytes))

          s"$algorithm\n$formattedDateTime\n$scope\n$hashedRequest"
        }

        val signingKey: SecretKeySpec =
          key(formattedDate, credentials, region, service, signingAlgorithm)

        val signature: String = encodeHex(
          signWithKey(signingKey, stringToSign.getBytes, signingAlgorithm))

        val authorizationHeader = {

          Authorization

          val authorizationHeaderValue =
            s"$algorithm Credential=${credentials.accessKeyId.value}/$scope, SignedHeaders=$signedHeaders, Signature=$signature"

          Raw("Authorization".ci, authorizationHeaderValue)
        }

        request.putHeaders(authorizationHeader)

    }

  }

  def apply[F[_]](
      credentialsProvider: CredentialsProvider[F],
      region: Region,
      service: Service): AwsSigner[F] =
    new AwsSigner[F](credentialsProvider, region, service)

}

class AwsSigner[F[_]](
    credentialsProvider: CredentialsProvider[F],
    region: Region,
    service: Service) {

  def apply(client: Client[F])(implicit F: Sync[F]): Client[F] = {

    val sign: Kleisli[F, Request[F], DisposableResponse[F]] = Kleisli {
      request =>
        for {
          credentials <- credentialsProvider.get
          now <- Sync[F].delay(
            Instant.now(Clock.systemUTC()).truncatedTo(ChronoUnit.SECONDS))
          fixed <- fixRequest(request, credentials, now)
          signed <- signRequest(fixed, credentials, region, service)
          result <- client.open(signed)
        } yield result
    }

    client.copy(open = sign)
  }

}
