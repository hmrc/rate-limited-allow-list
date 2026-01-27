/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.ratelimitedallowlist.crypto

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers

class ShaHashingFunctionSpec extends AnyFreeSpecLike, Matchers, ScalaFutures:

  "Sha256Checksum should hash value" in {
    val hashingFunction = ShaHashingFunction("5oTprrr+Aa6BPklnnwRjB+Xnxq3HjX/abQ8o+Q511JA=")
    val result = hashingFunction.hash("value to hash")

    result mustEqual "VKD8t5IwbgVlTMxHFgqcwJ6AT333n2GRQBfAcN9Zt66TtpiaPfzqD70V1tKCDpwr3rJfdFTuTQXqDL4wIm1Qxg=="
  }


