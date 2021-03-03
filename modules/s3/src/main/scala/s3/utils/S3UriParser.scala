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
package utils

import model._
import cats._
import cats.implicits._
import java.net.URLEncoder
import java.net.URI
import java.util.regex.Pattern
import java.net.URLDecoder

object S3UriParser {

  def getBucketAndKey[F[_]](
      uri: String
  )(implicit ME: MonadError[F, Throwable]): F[(Bucket, Key)] = {
    for {
      uri <- parseS3Uri[F](uri)
      bucketAndKey <- if ("s3".equalsIgnoreCase(uri.getScheme)) {
        (bucketFromS3SchemeUri[F](uri), keyFromS3SchemeUri[F](uri)).tupled
      } else {
        (bucketFromHttpUri[F](uri), keyFromHttpUri[F](uri)).tupled
      }
    } yield bucketAndKey
  }

  def getBucket[F[_]](
      uri: String
  )(implicit ME: MonadError[F, Throwable]): F[Bucket] =
    for {
      uri <- parseS3Uri[F](uri)
      bucket <- if ("s3".equalsIgnoreCase(uri.getScheme)) {
        bucketFromS3SchemeUri[F](uri)
      } else {
        bucketFromHttpUri[F](uri)
      }
    } yield bucket

  def getKey[F[_]](
      uri: String
  )(implicit ME: MonadError[F, Throwable]): F[Key] =
    for {
      uri <- parseS3Uri[F](uri)
      key <- if ("s3".equalsIgnoreCase(uri.getScheme)) {
        keyFromS3SchemeUri[F](uri)
      } else {
        keyFromHttpUri[F](uri)
      }
    } yield key

  private def parseS3Uri[F[_]](uri: String)(implicit ME: MonadError[F, Throwable]): F[URI] =
    ME.catchNonFatal {
      URI.create(
        URLEncoder
          .encode(uri, "UTF-8")
          .replace("%3A", ":")
          .replace("%2F", "/")
          .replace("+", "%20")
      )
    }

  private def bucketFromS3SchemeUri[F[_]](
      uri: URI
  )(implicit ME: MonadError[F, Throwable]): F[Bucket] =
    uri.getAuthority
      .ensureNotNull[F]("S3 Uri does not have an authority section")
      .map(Bucket(_))

  private def keyFromS3SchemeUri[F[_]](
      uri: URI
  )(implicit ME: MonadError[F, Throwable]): F[Key] =
    uri.getPath
      .ensureNotNull[F]("S3 Uri does not have a key")
      .map { keyWithLeadingSlash =>
        Key(keyWithLeadingSlash.substring(1))
      }

  private def bucketFromHttpUri[F[_]](
      uri: URI
  )(implicit ME: MonadError[F, Throwable]): F[Bucket] =
    for {
      tuple <- getHttpUriPathAndMaybePrefix[F](uri)
      (uriPath, maybePrefix) = tuple
      bucket <- maybePrefix
        .fold {
          s3BucketFromPath[F](uriPath)
        } { prefix =>
          ME.pure(prefix.substring(0, prefix.length - 1))
        }
    } yield Bucket(bucket)

  private def keyFromHttpUri[F[_]](
      uri: URI
  )(implicit ME: MonadError[F, Throwable]): F[Key] =
    for {
      tuple <- getHttpUriPathAndMaybePrefix[F](uri)
      (uriPath, maybePrefix) = tuple
      key <- maybePrefix.fold {
        s3KeyFromPath[F](uriPath)
      } { _ =>
        if (uriPath.isEmpty || uriPath == "/")
          ME.raiseError(new Exception("URI does not have a path section"))
        else ME.pure(uriPath.substring(1))
      }
    } yield Key(key)

  private def getHttpUriPathAndMaybePrefix[F[_]](
      uri: URI
  )(implicit ME: MonadError[F, Throwable]): F[(String, Option[String])] = {
    val endpointPattern = Pattern.compile("^(.+\\.)?s3[.-]([a-z0-9-]+)\\.")
    for {
      host <- uri.getHost.ensureNotNull[F]("URI does not have a host section")
      matcher = endpointPattern.matcher(host)
      _ <- ME.unit.ensure(new Exception("Cannot parse S3 Uri host section"))(_ => matcher.find())
      uriPath <- uri.getPath.ensureNotNull[F]("URI does not have a path section")
      maybePrefix = Option(matcher.group(1))
        .filter(!_.isEmpty)
    } yield (uriPath, maybePrefix)
  }

  private def s3BucketFromPath[F[_]](path: String)(
      implicit ME: MonadError[F, Throwable]
  ): F[String] =
    if ("" == path || "/" == path) {
      ME.raiseError(new Exception("S3 path is empty"))
    } else {
      val index = path.indexOf('/', 1)
      ME.catchNonFatal {
        if (index == -1) { // https://s3.amazonaws.com/bucket
          URLDecoder.decode(path.substring(1), "UTF-8")
        } else if (index == (path.length - 1)) { // https://s3.amazonaws.com/bucket/
          URLDecoder.decode(path.substring(1, index), "UTF-8")
        } else { // https://s3.amazonaws.com/bucket/key
          URLDecoder.decode(path.substring(1, index), "UTF-8")
        }
      }
    }

  private def s3KeyFromPath[F[_]](
      path: String
  )(implicit ME: MonadError[F, Throwable]): F[String] = {
    if ("" == path || "/" == path) {
      ME.raiseError(new Exception("S3 path is empty"))
    } else {
      val index = path.indexOf('/', 1)
      if (index == -1 || index == (path.length - 1)) { // https://s3.amazonaws.com/bucket
        ME.raiseError(new Exception("S3 path does not have a key"))
      } else { // https://s3.amazonaws.com/bucket/key
        ME.catchNonFatal(URLDecoder.decode(path.substring(index + 1), "UTF-8"))
      }
    }
  }

  private implicit class MonadErrorHelpers[T](t: T) {
    def ensureNotNull[F[_]](error: => Throwable)(implicit ME: MonadError[F, Throwable]): F[T] =
      ME.pure(t).ensure(error)(_ != null)
    def ensureNotNull[F[_]](error: String)(implicit ME: MonadError[F, Throwable]): F[T] =
      t.ensureNotNull[F](new Exception(error))
  }

}
