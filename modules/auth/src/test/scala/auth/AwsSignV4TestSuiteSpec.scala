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

import common._
import headers._
import model._
import Credentials._

import cats.effect.{IO, Sync}

import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.{HttpDate, Request, Uri}
import org.http4s.syntax.all._

/*
  Validate AwsSigner with test cases from 'AWS Signature Version 4 Test Suite'
  https://docs.aws.amazon.com/general/latest/gr/signature-v4-test-suite.html
  Download the test cases zip file, extract in the project root dir and run tests.
 */

class AwsSignV4TestSuiteSpec extends UnitSpec with Http4sClientDsl[IO] {
  import cats.data.EitherT

  val DateFormatter =
    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssVV")
  def parseTestCaseDate(s: String) =
    java.time.ZonedDateTime.parse(s, DateFormatter)

  val TestCaseFileBaseDir =
    "modules/auth/src/test/resources/aws-signv4-test-suite"
  val AuthorizationFileSuffix = ".authz"
  val RequestFileSuffix = ".req"

  val AwsRegion = Region.`us-east-1`
  val AwsService = Service.TestService
  val AwsCredentials = Credentials(
    AccessKeyId("AKIDEXAMPLE"),
    SecretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
  )

  val expectedFailures = Set(
    "get-space",
    "post-x-www-form-urlencoded",
    "get-unreserved",
    "get-header-value-multiline",
    "get-relative-relative",
    "get-slash-pointless-dot",
    "get-slash-dot-slash",
    "get-vanilla-utf8-query",
    "get-utf8",
    "get-vanilla-query-order-value",
    "post-x-www-form-urlencoded-parameters",
    "get-slashes",
    "get-relative",
    "get-vanilla-query-unreserved",
    "get-vanilla-query-order-key"
  )

  "AwsSigner" should {
    getTestCaseFileBaseNames(TestCaseFileBaseDir) foreach { testFile =>
      s"pass test case '${testFile.getName}'" in {
        (for {
          testCase <- EitherT(getTestCase[IO](testFile.getAbsolutePath))
          (request, expectedSignature) = testCase
          res <- EitherT.right[String](withSignRequest(IO(request)) { signed =>
            val signature = signed.headers.get("Authorization".ci).get.value
            IO(signature shouldBe expectedSignature)
          })
        } yield res).value.map {
          case Left(msg) => fail(msg)
          case Right(a) => a
        }.futureValue
      }
    }
  }

  def getTestCaseFileBaseNames(baseDir: String) = {
    import java.io.File

    def getRecursiveListOfFiles(dir: File): Array[File] = {
      val these = dir.listFiles
      these ++ these.filter(_.isDirectory).flatMap(getRecursiveListOfFiles)
    }

    getRecursiveListOfFiles(new File(baseDir)).toList
      .filter(f => f.getName.endsWith(AuthorizationFileSuffix))
      .map(f => new File(f.getPath.replace(AuthorizationFileSuffix, "")))
      .filter(f => !expectedFailures.contains(f.getName))
  }

  def getTestCase[F[_]](baseFileName: String)(implicit F: Sync[F]) = {
    import java.io._
    import cats.implicits._
    import cats.data.EitherT
    import cats.effect.Resource
    import org.http4s.{Header, Headers}

    def source(fn: String) =
      Resource.fromAutoCloseable(F.delay {
        scala.io.Source.fromFile(new File(fn), "UTF-8")
      })
    def parseRequest(requestText: String): F[Either[String, Request[F]]] = {
      val RequestRe = "(?s)(.+?)(\n\n(.+))?".r
      val RequestLineRe = "(GET|POST) (.+) HTTP/1.1".r
      def headers(rows: List[String]) =
        rows.map(_.split(":", 2).toList).collect {
          case "X-Amz-Date" :: v :: Nil =>
            `X-Amz-Date`(HttpDate.unsafeFromZonedDateTime(parseTestCaseDate(v)))
          case k :: v :: Nil => Header(k, v)
        }
      (requestText match {
        case RequestRe(requestSection, _, body) =>
          requestSection.split("\n").toList match {
            case RequestLineRe(methodText, uri) :: headersRows =>
              val method = methodText match {
                case "POST" => POST
                case "GET" => GET
              }
              val request = Request[F](
                method = method,
                uri = Uri.unsafeFromString(uri),
                headers = Headers(headers(headersRows))
              )
              Right(
                Option(body).map(b => request.withEntity(b)).getOrElse(request)
              )
            case l => Left(s"unexpected request line syntax: $l")
          }
        case l => Left(s"unexpected request syntax: '$l'")
      }).pure[F]
    }

    (for {
      request <- EitherT(
        source(s"${baseFileName}${RequestFileSuffix}").use(r => parseRequest(r.mkString))
      )
      signature <- EitherT.right[String](
        source(s"${baseFileName}${AuthorizationFileSuffix}")
          .use(_.mkString.pure[F])
      )
    } yield request -> signature).value
  }

  def withSignRequest[A](
      req: IO[Request[IO]],
      region: Region = AwsRegion,
      service: Service = AwsService,
      credentials: Credentials = AwsCredentials
  )(f: Request[IO] => IO[A]): IO[A] = {
    for {
      request <- req
      signedRequest <- AwsSigner.signRequest(
        request,
        credentials,
        region,
        service
      )
      result <- f(signedRequest)
    } yield result
  }

}
