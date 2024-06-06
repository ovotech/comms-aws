import sbtrelease.ExtraReleaseCommands
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.tagsonly.TagsOnly._

lazy val fs2Version = "3.10.2"

lazy val catsEffectVersion = "3.5.2"

lazy val scalatestVersion = "3.2.18"

lazy val awsSdkVersion = "2.25.67"

lazy val scalacheckVersion = "1.18.0"

lazy val scalatestScalacheckVersion = "3.1.1.1"

lazy val slf4jVersion = "1.7.36"

lazy val log4jVersion = "2.23.1"

lazy val http4sVersion = "0.23.27"

lazy val http4sBlazeClientVersion = "0.23.16"

lazy val scalaXmlVersion = "2.3.0"

lazy val circeVersion = "0.12.2"

lazy val scodecBitsVersion = "1.1.12"

lazy val commonCodecVersion = "1.17.0"

lazy val IntegrationTest = config("it") extend Test

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val publicArtifactory = "Artifactory Realm" at "https://kaluza.jfrog.io/artifactory/maven"

lazy val publishOptions = Seq(
  publishTo := Some(publicArtifactory),
  credentials += {
    for {
      usr <- sys.env.get("ARTIFACTORY_USER")
      password <- sys.env.get("ARTIFACTORY_PASS")
    } yield Credentials("Artifactory Realm", "kaluza.jfrog.io", usr, password)
  }.getOrElse(Credentials(Path.userHome / ".ivy2" / ".credentials")),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    releaseStepCommand(ExtraReleaseCommands.initialVcsChecksCommand),
    setVersionFromTags(releaseTagPrefix.value),
    runClean,
    tagRelease,
    publishArtifacts,
    pushTagsOnly
  )
)

lazy val root = (project in file("."))
  .aggregate(auth, common, s3)
  .configs(IntegrationTest)
  .settings(publishOptions)
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
        scalaVersion := "2.13.14",
        crossScalaVersions += "2.12.19",
        resolvers ++= Seq(
          publicArtifactory
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
          "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0",
          "org.scalacheck" %% "scalacheck" % scalacheckVersion,
          "org.scalatestplus" %% "scalacheck-1-14" % scalatestScalacheckVersion,
          "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
          "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
          "org.http4s" %% "http4s-blaze-client" % http4sBlazeClientVersion
        ).map(_ % s"$Test,$IntegrationTest"),
        scalafmtOnCompile := true
      )
    )
  )

lazy val common = (project in file("modules/common"))
  .enablePlugins(AutomateHeaderPlugin)
  .configs(IntegrationTest)
  .settings(publishOptions)
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
  .settings(publishOptions)
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
  .settings(publishOptions)
  .settings(
    name := "comms-aws-s3",
    scalacOptions -= "-Xfatal-warnings" // enable all options from sbt-tpolecat except fatal warnings
  )
  .settings(inConfig(IntegrationTest)(Defaults.itSettings))
  .settings(automateHeaderSettings(IntegrationTest))
  .settings(
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-scala-xml" % "0.23.14",
      "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion,
      "org.http4s" %% "http4s-blaze-client" % http4sBlazeClientVersion % Optional,
      "software.amazon.awssdk" % "s3" % awsSdkVersion % s"$Test,$IntegrationTest"
    )
  )
