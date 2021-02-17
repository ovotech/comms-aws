lazy val fs2Version = "2.5.0"
lazy val catsEffectVersion = "2.0.0"
lazy val catsVersion = "2.0.0"
lazy val scalatestVersion = "3.2.0"
lazy val awsSdkVersion = "2.15.82"
lazy val scalacheckVersion = "1.15.3"
lazy val scalatestScalacheckVersion = "3.1.1.1"
lazy val slf4jVersion = "1.7.30"
lazy val log4jVersion = "2.14.0"
lazy val http4sVersion = "0.21.19"
lazy val scalaXmlVersion = "1.3.0"
lazy val circeVersion = "0.12.2"
lazy val scodecBitsVersion = "1.1.12"
lazy val commonCodecVersion = "1.14"

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
  //  releaseEarlyEnableSyncToMaven := false,
  bintrayOrganization := Some("ovotech"),
  bintrayRepository := "maven",
  bintrayPackageLabels := Seq(
    "aws",
    "cats",
    "cats-effect",
    "http4s",
    "fs2",
    "scala"
  ),
  version ~= (_.replace('+', '-')),
  dynver ~= (_.replace('+', '-'))
)

lazy val root = (project in file("."))
  .aggregate(auth, common, s3)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws",
    inThisBuild(
      List(
        organization := "com.ovoenergy.comms",
        organizationName := "OVO Energy",
        organizationHomepage := Some(url("https://ovoenergy.com")),
        // TODO Extract contributors from github
        developers := List(
          Developer(
            "filosganga",
            "Filippo De Luca",
            "filippo.deluca@ovoenergy.com",
            url("https://github.com/filosganga")
          ),
          Developer(
            "laurence-bird",
            "Laurence Bird",
            "laurence.bird@ovoenergy.com",
            url("https://github.com/laurence-bird")
          ),
          Developer(
            "SystemFw",
            "Fabio Labella",
            "fabio.labella@ovoenergy.com",
            url("https://github.com/SystemFw")
          ),
          Developer(
            "ZsoltBalvanyos",
            "Zsolt Balvanyos",
            "zsolt.balvanyos@ovoenergy.com",
            url("https://github.com/ZsoltBalvanyos")
          )
        ),
        startYear := Some(2018),
        licenses := Seq("Apache-2.0" -> url("https://opensource.org/licenses/apache-2.0")),
        scmInfo := Some(
          ScmInfo(
            url("https://github.com/ovotech/comms-aws"),
            "scm:git:git@github.com:ovotech/comms-aws.git"
          )
        ),
        scalaVersion := "2.13.2",
        crossScalaVersions += "2.12.10",
        resolvers ++= Seq(
          Resolver.bintrayRepo("ovotech", "maven")
        ),
        libraryDependencies ++= Seq(
          "org.http4s" %% "http4s-core" % http4sVersion,
          "org.http4s" %% "http4s-client" % http4sVersion,
          "co.fs2" %% "fs2-core" % fs2Version,
          "co.fs2" %% "fs2-io" % fs2Version,
          "org.slf4j" % "slf4j-api" % slf4jVersion
        ),
        libraryDependencies ++= Seq(
          "org.scalatest" %% "scalatest" % scalatestVersion,
          "org.scalacheck" %% "scalacheck" % scalacheckVersion,
          "org.scalatestplus" %% "scalacheck-1-14" % scalatestScalacheckVersion,
          "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
          "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
          "org.http4s" %% "http4s-blaze-client" % http4sVersion
        ).map(_ % s"$Test,$IntegrationTest"),
        scalafmtOnCompile := true
      )
    )
  )

lazy val common = (project in file("modules/common"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-common",
    scalacOptions -= "-Xfatal-warnings" // enable all options from sbt-tpolecat except fatal warnings
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "software.amazon.awssdk" % "auth" % awsSdkVersion,
      "software.amazon.awssdk" % "sdk-core" % awsSdkVersion % Optional
    )
  )

lazy val auth = (project in file("modules/auth"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common % s"$Compile->$Compile;$Test->$Test;$IntegrationTest->$IntegrationTest")
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-auth",
    scalacOptions -= "-Xfatal-warnings" // enable all options from sbt-tpolecat except fatal warnings
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % commonCodecVersion,
      "software.amazon.awssdk" % "s3" % awsSdkVersion % s"$Test,$IntegrationTest"
    )
  )

lazy val s3 = (project in file("modules/s3"))
  .enablePlugins(AutomateHeaderPlugin)
  .dependsOn(common % s"$Compile->$Compile;$Test->$Test;$IntegrationTest->$IntegrationTest", auth)
  .configs(IntegrationTest)
  .settings(releaseOptions)
  .settings(
    name := "comms-aws-s3",
    scalacOptions -= "-Xfatal-warnings" // enable all options from sbt-tpolecat except fatal warnings
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-scala-xml" % http4sVersion,
      "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sVersion % Optional,
      "software.amazon.awssdk" % "s3" % awsSdkVersion % s"$Test,$IntegrationTest"
    )
  )
