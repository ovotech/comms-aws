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

import com.ovoenergy.comms.aws.common.UnitSpec
import com.ovoenergy.comms.aws.common.model.Region
import org.scalatest.Assertion

class S3UriDomainSpec extends UnitSpec {

  "S3UriDomain" should {

    "use the region in the domain name for us-west-2" in assertUsesRegion(Region.`us-west-2`)
    "use the region in the domain name for us-west-1" in assertUsesRegion(Region.`us-west-1`)
    "use the region in the domain name for us-east-2" in assertUsesRegion(Region.`us-east-2`)
    "use the region in the domain name for ap-south-1" in assertUsesRegion(Region.`ap-south-1`)
    "use the region in the domain name for ap-northeast-2" in assertUsesRegion(
      Region.`ap-northeast-2`
    )
    "use the region in the domain name for ap-southeast-1" in assertUsesRegion(
      Region.`ap-southeast-1`
    )
    "use the region in the domain name for ap-southeast-2" in assertUsesRegion(
      Region.`ap-southeast-2`
    )
    "use the region in the domain name for ap-northeast-1" in assertUsesRegion(
      Region.`ap-northeast-1`
    )
    "use the region in the domain name for ca-central-1" in assertUsesRegion(Region.`ca-central-1`)
    "use the region in the domain name for cn-north-1" in assertUsesRegion(Region.`cn-north-1`)
    "use the region in the domain name for eu-central-1" in assertUsesRegion(Region.`eu-central-1`)
    "use the region in the domain name for eu-west-1" in assertUsesRegion(Region.`eu-west-1`)
    "use the region in the domain name for eu-west-2" in assertUsesRegion(Region.`eu-west-2`)
    "use the region in the domain name for eu-west-3" in assertUsesRegion(Region.`eu-west-3`)
    "use the region in the domain name for sa-east-1" in assertUsesRegion(Region.`sa-east-1`)
    "use the region in the domain name for us-gov-west-1" in assertUsesRegion(
      Region.`us-gov-west-1`
    )

    "handle us-east-1 region differently" in {
      S3UriDomain.s3UriDomain("bucketName", Region.`us-east-1`) shouldBe
        "bucketName.s3.amazonaws.com"
    }
  }

  private def assertUsesRegion(region: Region): Assertion = {
    S3UriDomain.s3UriDomain("bucketName", region) shouldBe s"bucketName.s3-${region.value}.amazonaws.com"
  }
}
