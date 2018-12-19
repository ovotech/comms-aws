package com.ovoenergy.comms.aws
package auth

import common._
import common.model._
import cats.effect.{IO, ContextShift}
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import cats.implicits._
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import org.http4s.{Uri, Status}
import org.http4s.client.middleware.{ResponseLogger, RequestLogger}

import scala.concurrent.duration._

class AwsSignerItSpec extends IntegrationSpec with Http4sClientDsl[IO] {

  implicit val ctx: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  "AwsSigner" should {
    "sign request valid for S3" in {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service.S3)

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        for {
          req <- GET(Uri.uri("https://s3-eu-west-1.amazonaws.com/ovo-comms-test/more.pdf"))
          status <- signedClient.status(req)
        } yield {
          status.isSuccess shouldBe true
        }
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds))
    }

    "sign request valid for S3 with nested paths" in {
      withHttpClient { client =>
        val awsSigner = AwsSigner(
          CredentialsProvider.fromAwsCredentialProvider[IO](
            new DefaultAWSCredentialsProviderChain()),
          Region.`eu-west-1`,
          Service.S3)

        val requestLogger: Client[IO] => Client[IO] = RequestLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)
        val responseLogger: Client[IO] => Client[IO] = ResponseLogger[IO](logHeaders = true, logBody = true, redactHeadersWhen = _ => false)

        val signedClient: Client[IO] = awsSigner(requestLogger(responseLogger(client)))

        for {
          req <- GET(Uri.uri("https://s3-eu-west-1.amazonaws.com/ovo-comms-test/test/more.pdf"))
          status <- signedClient.status(req)
        } yield status
      }.futureValue(timeout(scaled(5.seconds)), interval(500.milliseconds)) should (not be Status.Unauthorized and not be Status.Forbidden)
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
