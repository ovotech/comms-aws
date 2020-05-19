package com.ovoenergy.comms.aws
package common

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

abstract class IntegrationSpec extends AnyWordSpec with Matchers with IOFutures {
  sys.props.put("log4j.configurationFile", "log4j2-it.xml")
}

