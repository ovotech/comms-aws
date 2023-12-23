package com.ovoenergy.comms.aws
package common

import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.matchers.should.Matchers

abstract class IntegrationSpec extends AsyncWordSpec with Matchers {
  sys.props.put("log4j.configurationFile", "log4j2-it.xml")
}

