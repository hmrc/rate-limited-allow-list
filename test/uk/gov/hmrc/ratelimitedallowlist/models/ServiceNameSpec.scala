/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.ratelimitedallowlist.models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.mvc.PathBindable

class ServiceNameSpec extends AnyFreeSpec with Matchers:
  val pathBindable = summon[PathBindable[ServiceName]]

  "PathBindable bind" - {
    "pass when the path is valid" in {
      val expected = ServiceName("asdf-ASDF-123")
      pathBindable.bind("serviceName", expected.value) mustBe Right(expected)
    }

    "fail when the path is valid" in {
      val expected = ServiceName("asdf_ASDF_123")
      pathBindable.bind("serviceName", expected.value) mustBe Left("Invalid format for service name")
    }
  }

  "PathBindable unbind" - {
    "pass" in {
      val value = ServiceName("asdf-ASDF-123")
      pathBindable.unbind("serviceName", value) mustBe value.value
    }
  }
