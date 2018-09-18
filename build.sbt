lazy val fs2Version = "0.10.6"
lazy val catsEffectVersion = "0.10.1"
lazy val catsVersion = "1.2.0"
lazy val awsSdkVersion = "1.11.391"
lazy val scalatestVersion = "3.0.5"
lazy val scalacheckVersion = "1.13.5"
lazy val slf4jVersion = "1.7.25"
lazy val log4jVersion = "2.11.1"
lazy val htt4sVersion = "0.18.17"
lazy val commsDockerkitVersion = "1.8.2"


lazy val IntegrationTest = config("it") extend Test

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val releaseOptions = Seq(
  releaseEarlyWith := BintrayPublisher,
  releaseEarlyEnableSyncToMaven := false,
  releaseEarlyNoGpg := true,
  bintrayOrganization := Some("ovotech"),
  bintrayRepository := "maven",
  bintrayPackageLabels := Seq(
    "aws",
    "cats",
    "cats-effect",
    "http4s",
    "fs2",
    "scala",
  ),
  version ~= (_.replace('+', '-')),
  dynver ~= (_.replace('+', '-'))
)

lazy val root = (project in file("."))
  .aggregate(common, auth)
  .configs(IntegrationTest)
  .settings(
    name := "comms-aws",
    inThisBuild(List(
      organization := "com.ovoenergy.comms",
      organizationName := "OVO Energy",
      organizationHomepage := Some(url("https://ovoenergy.com")),
      startYear := Some(2018),
      licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/apache-2.0")),
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/ovotech/comms-aws"),
          "scm:git:git@github.com:ovotech/comms-aws.git")
      ),
      scalaVersion := "2.12.6",
      scalacOptions ++= Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-language:existentials",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-language:postfixOps",
        "-unchecked",
        "-Xlint",
        "-Yno-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ywarn-value-discard",
        "-Xfuture",
        "-Ywarn-unused-import",
        "-Ywarn-unused",
        "-Ypartial-unification"
      ),
      scalacOptions in(Compile, console) --= Seq("-Xlint", "-Ywarn-unused", "-Ywarn-unused-import"),
      scalacOptions in(Test, console) := (scalacOptions in(Compile, console)).value,
      testOptions in Test += Tests.Argument("-oDF"),
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-core" % htt4sVersion,
        "org.http4s" %% "http4s-client" % htt4sVersion,
        "co.fs2" %% "fs2-core" % fs2Version,
        "co.fs2" %% "fs2-io" % fs2Version,
        "org.slf4j" % "slf4j-api" % slf4jVersion,
      ),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % scalatestVersion,
        "org.scalacheck" %% "scalacheck" % scalacheckVersion,
        "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
        "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
        "com.ovoenergy" %% "comms-docker-testkit" % commsDockerkitVersion,
        "org.http4s" %% "http4s-blaze-client" % htt4sVersion,
      ).map(_ % s"$Test,$IntegrationTest"),
      scalafmtOnCompile := true,
    )),
  )

lazy val common = (project in file("common"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(
    name := "comms-aws-common",
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion % Optional,
    )
  )


lazy val auth = (project in file("auth"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common % s"$Compile->$Compile;$Test->$Test;$IntegrationTest->$IntegrationTest")
  .configs(IntegrationTest)
  .settings(
    name := "comms-aws-auth",
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion % s"$Test,$IntegrationTest",
    )
  )


