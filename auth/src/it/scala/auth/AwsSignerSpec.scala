package com.ovoenergy.comms.aws
package auth

import common._

import cats.effect.IO
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import cats.implicits._
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.Uri
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}

import scala.concurrent.duration._

class AwsSignerSpec extends IntegrationSpec with Http4sClientDsl[IO] {

  "AwsSigner" should {
    "sign request valid for S3" in {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service.S3)

        val requestLogger: Client[IO] => Client[IO] = RequestLogger.apply0[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger.apply0[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        for {
          req <- GET(Uri.uri("https://s3-eu-west-1.amazonaws.com/ovo-comms-test/more.pdf"))
          status <- signedClient.status(req)
        } yield {
          println(s"Status: $status")
          status.isSuccess shouldBe true
        }
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds))
    }
  }

  def withHttpClient[A](f: Client[IO] => IO[A]): IO[A] = {
    Http1Client
      .stream[IO]()
      .evalMap(f)
      .compile
      .last
      .map(
        _.toRight[Throwable](new IllegalStateException("The stream was empty")))
      .rethrow
  }

}
