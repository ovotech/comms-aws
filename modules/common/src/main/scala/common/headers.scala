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
package common

import model.Credentials._

import java.time._
import org.http4s.{Header, _}
import org.http4s.Header.Raw
import cats.implicits._
import org.typelevel.ci.{CIString, _}

object headers extends HttpCodecs {

  object `X-Amz-Date` {
    def name: CIString = ci"X-Amz-Date"

    def parse(s: String): ParseResult[`X-Amz-Date`] =
      HttpCodec[HttpDate].parse(s).map(`X-Amz-Date`.apply)

    def unsafeFromInstant(instant: Instant): `X-Amz-Date` = {
      `X-Amz-Date`(HttpDate.unsafeFromInstant(instant))
    }

    def unsafeFromDateTime(dateTime: OffsetDateTime): `X-Amz-Date` = {
      `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime.toZonedDateTime))
    }

    def unsafeFromDateTime(dateTime: ZonedDateTime): `X-Amz-Date` = {
      `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(dateTime))
    }

    def matchHeader(header: Any): Option[`X-Amz-Date`] = header match {
      case h: `X-Amz-Date` => h.some
      case Raw(n, v) if n == name => parse(v).toOption
      case _ => None
    }

    implicit val dateInstance: Header[`X-Amz-Date`, Header.Single] = Header.createRendered(
      `X-Amz-Date`.name,
      _.date,
      `X-Amz-Date`.parse
    )
  }

  final case class `X-Amz-Date`(date: HttpDate)

  object `X-Amz-Content-SHA256` extends {
    val name: CIString = ci"X-Amz-Content-SHA256"

    def matchHeader(header: Any): Option[`X-Amz-Content-SHA256`] =
      header match {
        case h: `X-Amz-Content-SHA256` => h.some
        case Raw(n, v) if n == name => parse(v).toOption
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Content-SHA256`] =
      `X-Amz-Content-SHA256`(s).asRight

    implicit val contentSha256Instance: Header[`X-Amz-Content-SHA256`, Header.Single] =
      Header.createRendered(
        `X-Amz-Content-SHA256`.name,
        _.hashedContent,
        `X-Amz-Content-SHA256`.parse
      )
  }

  final case class `X-Amz-Content-SHA256`(hashedContent: String)

  object `X-Amz-Security-Token` {
    val name: CIString = ci"X-Amz-Security-Token"

    def matchHeader(header: Any): Option[`X-Amz-Security-Token`] =
      header match {
        case h: `X-Amz-Security-Token` => h.some
        case Raw(n, v) if n == name => parse(v).toOption
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Security-Token`] =
      HttpCodec[SessionToken].parse(s).map(`X-Amz-Security-Token`.apply)

    implicit val securityTokenInstance: Header[`X-Amz-Security-Token`, Header.Single] =
      Header.createRendered(
        `X-Amz-Security-Token`.name,
        _.sessionToken,
        `X-Amz-Security-Token`.parse
      )
  }

  final case class `X-Amz-Security-Token`(sessionToken: SessionToken)

  object `X-Amz-Target` {
    val name: CIString = ci"X-Amz-Target"

    def matchHeader(header: Any): Option[`X-Amz-Target`] =
      header match {
        case h: `X-Amz-Target` => h.some
        case Raw(n, v) if n == name => parse(v).toOption
        case _ => None
      }

    def parse(s: String): ParseResult[`X-Amz-Target`] =
      `X-Amz-Target`(s).asRight

    implicit val targetInstance: Header[`X-Amz-Target`, Header.Single] = Header.createRendered(
      `X-Amz-Target`.name,
      _.target,
      `X-Amz-Target`.parse
    )
  }

  final case class `X-Amz-Target`(target: String)
}
