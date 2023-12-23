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

import model._
import org.http4s._
import syntax.all._
import Header.Raw
import util.Writer
import cats.implicits._
import org.typelevel.ci.CIString

trait HttpCodecs {

  implicit lazy val storageClassHttpCodec: HttpCodec[StorageClass] =
    new HttpCodec[StorageClass] {
      override def parse(s: String): ParseResult[StorageClass] =
        StorageClass
          .fromString(s)
          .toRight(
            new ParseFailure(
              "Failed to parse a storage class",
              s"$s is nota valid storage class, valid values are: ${StorageClass.values}"
            )
          )
      override def render(writer: Writer, t: StorageClass): writer.type =
        writer << t.toString
    }

}

object headers extends HttpCodecs {

  val `X-Amz-Meta-` = "x-amz-meta-"

  object `X-Amz-Storage-Class` {

    val name: CIString = "X-Amz-Storage-Class".ci

    def matchHeader(header: Any): Option[`X-Amz-Storage-Class`] =
      header match {
        case h: `X-Amz-Storage-Class` => h.some
        case Raw(n, v) if n == name => parse(v).toOption
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Storage-Class`] =
      HttpCodec[StorageClass].parse(s).map(`X-Amz-Storage-Class`.apply)

  }

  final case class `X-Amz-Storage-Class`(storageClass: StorageClass)

  implicit val storageClassInstance: Header[`X-Amz-Storage-Class`, Header.Single] =
    Header.createRendered(
      `X-Amz-Storage-Class`.name,
      _.storageClass,
      `X-Amz-Storage-Class`.parse
    )

}
