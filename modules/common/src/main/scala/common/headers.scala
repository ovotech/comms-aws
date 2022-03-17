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

import org.http4s._
import util.Writer
import org.typelevel.ci._

import cats.implicits._

object headers extends HttpCodecs {

  object `X-Amz-Date` {
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

    implicit val xAmzDateHeaderInstance: Header[`X-Amz-Date`, Header.Single] =
      Header.create(
        ci"X-Amz-Date",
        _.date.renderString,
        parse _
      )
  }

  final case class `X-Amz-Date`(date: HttpDate) {
    def key: `X-Amz-Date`.type = `X-Amz-Date`

    def renderValue(writer: Writer): writer.type = writer << date
  }

  object `X-Amz-Content-SHA256` {
    def parse(s: String): ParseResult[`X-Amz-Content-SHA256`] =
      `X-Amz-Content-SHA256`(s).asRight

    implicit val xAmzContentSHA256HeaderInstance: Header[`X-Amz-Content-SHA256`, Header.Single] =
      Header.create(
        ci"X-Amz-Content-SHA256",
        _.hashedContent,
        parse _
      )
  }

  final case class `X-Amz-Content-SHA256`(hashedContent: String) {
    def key: `X-Amz-Content-SHA256`.type = `X-Amz-Content-SHA256`

    def renderValue(writer: Writer): writer.type = writer << hashedContent
  }

  object `X-Amz-Security-Token` {
    def parse(s: String): ParseResult[`X-Amz-Security-Token`] =
      HttpCodec[SessionToken].parse(s).map(`X-Amz-Security-Token`.apply)

    implicit val xAmzSecurityTokenHeaderInstance: Header[`X-Amz-Security-Token`, Header.Single] =
      Header.create(
        ci"X-Amz-Security-Token",
        _.sessionToken.value,
        parse _
      )
  }

  final case class `X-Amz-Security-Token`(sessionToken: SessionToken) {
    def key: `X-Amz-Security-Token`.type = `X-Amz-Security-Token`

    def renderValue(writer: Writer): writer.type = writer << sessionToken
  }

  object `X-Amz-Target` {
    def parse(s: String): ParseResult[`X-Amz-Target`] =
      `X-Amz-Target`(s).asRight

    implicit val xAmzTargetHeaderInstance: Header[`X-Amz-Target`, Header.Single] =
      Header.create(
        ci"X-Amz-Target",
        _.target,
        parse _
      )
  }

  final case class `X-Amz-Target`(target: String) {
    def key: `X-Amz-Target`.type = `X-Amz-Target`

    def renderValue(writer: Writer): writer.type = writer << target
  }
}
