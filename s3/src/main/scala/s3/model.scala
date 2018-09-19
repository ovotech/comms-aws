package com.ovoenergy.comms.aws
package s3

import common.model._

import cats.syntax.option._
import fs2.Stream
import org.http4s.Uri

object model {

  /**
    * The S3 Object. Running the content stream or calling dispose will dispose
    * the underling connection
    *
    * @param eTag The S3 ETag
    * @param content The Stream[F, Byte] on the object content.
    * @param dispose This will cause the underlying HTTP response to be closed
    * @tparam F The effect
    */
  case class Object[F[_]](eTag: Etag, content: Stream[F, Byte], dispose: F[Unit])

  case class Error(code: Error.Code, requestId: RequestId, resource: Uri, message: String )

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
        .getOrElse(throw new IllegalArgumentException(s"string is not a valid storage class, valid values are: $values"))

  }

  case class Bucket(name: String)

  case class Key(value: String)

  case class Etag(value: String)

}