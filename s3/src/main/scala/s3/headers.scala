package com.ovoenergy.comms.aws
package s3

import model._
import common.Credentials._

import java.time._

import org.http4s._
import syntax.all._
import Header.Raw
import util.{CaseInsensitiveString, Writer}

import cats.implicits._


trait HttpCodecs {

  implicit lazy val storageClassHttpCodec: HttpCodec[StorageClass] = new HttpCodec[StorageClass] {
    override def parse(s: String): ParseResult[StorageClass] =
      StorageClass.fromString(s).toRight(new ParseFailure("Failed to parse a storage class", s"$s is nota valid storage class, valid values are: ${StorageClass.values}"))
    override def render(writer: Writer, t: StorageClass): writer.type = writer << t.toString
  }

}

object headers extends HttpCodecs {

  object `X-Amz-Storage-Class` extends HeaderKey.Singleton {
    type HeaderT = `X-Amz-Storage-Class`

    val name: CaseInsensitiveString = "X-Amz-Storage-Class".ci

    def matchHeader(header: Header): Option[`X-Amz-Storage-Class`] = header match {
      case h: `X-Amz-Storage-Class` => h.some
      case Raw(n, _) if n == name =>
        header.parsed.asInstanceOf[`X-Amz-Storage-Class`].some
      case _ => None
    }

    def parse(s: String): ParseResult[`X-Amz-Storage-Class`] =
      HttpCodec[StorageClass].parse(s).map(`X-Amz-Storage-Class`.apply)

  }

  final case class `X-Amz-Storage-Class`(storageClass: StorageClass) extends Header.Parsed {
    def key: `X-Amz-Storage-Class`.type = `X-Amz-Storage-Class`

    def renderValue(writer: Writer): writer.type = writer << storageClass
  }



}
