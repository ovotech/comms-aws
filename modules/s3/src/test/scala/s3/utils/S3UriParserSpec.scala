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

import cats.effect.IO
import com.ovoenergy.comms.aws.common.UnitSpec
import com.ovoenergy.comms.aws.s3.model._

class S3UriParserSpec extends UnitSpec {

  "S3UriParser" should {

    // Bucket
    "parse bucket name from authority" in {
      val testCase = "s3://some-bucket/object-key"
      S3UriParser.getBucket[IO](testCase).futureValue shouldBe Bucket("some-bucket")
    }

    "parse bucket name from authority with no key" in {
      val testCase = "s3://some-bucket"
      S3UriParser.getBucket[IO](testCase).futureValue shouldBe Bucket("some-bucket")
    }

    "return fail if authority empty" in {
      val testCase = "s3://"
      S3UriParser.getBucket[IO](testCase).attempt.futureValue shouldBe a[Left[_, _]]
    }

    "parse bucket name from path" in {
      val testCase = "https://s3.amazonaws.com/some-bucket/key"
      S3UriParser.getBucket[IO](testCase).futureValue shouldBe Bucket("some-bucket")
    }

    "parse bucket name from path with no key" in {
      val testCase = "https://s3.amazonaws.com/some-bucket"
      S3UriParser.getBucket[IO](testCase).futureValue shouldBe Bucket("some-bucket")
    }

    "parse bucket name from path with trailing slash" in {
      val testCase = "https://s3.amazonaws.com/some-bucket/"
      S3UriParser.getBucket[IO](testCase).futureValue shouldBe Bucket("some-bucket")
    }

    // Key
    "Parse key from s3 format uri" in {
      val testCase = "s3://some-bucket/object-key"
      S3UriParser.getKey[IO](testCase).futureValue shouldBe Key("object-key")
    }

    "Return None from s3 format uri with no key" in {
      val testCase = "s3://some-bucket"
      S3UriParser.getKey[IO](testCase).attempt.futureValue shouldBe a[Left[_, _]]
    }

    "Parse key from regular format uri" in {
      val testCase = "https://s3.amazonaws.com/some-bucket/object-key"
      S3UriParser.getKey[IO](testCase).futureValue shouldBe Key("object-key")
    }

    "Parse multipart key from uri" in {
      val testCase = "https://s3.eu-west-1.amazonaws.com/bucket/key-part-1/key-part-2/key-part-3"
      S3UriParser.getKey[IO](testCase).futureValue shouldBe Key("key-part-1/key-part-2/key-part-3")
    }

    "Return None from regular format uri with no key" in {
      val testCase = "https://s3.amazonaws.com/some-bucket/"
      S3UriParser.getKey[IO](testCase).attempt.futureValue shouldBe a[Left[_, _]]
    }

  }

}
