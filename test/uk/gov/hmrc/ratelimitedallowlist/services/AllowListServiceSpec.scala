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

package uk.gov.hmrc.ratelimitedallowlist.services

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration
import play.api.mvc.PathBindable
import uk.gov.hmrc.ratelimitedallowlist.repositories.FakeAllowListRepository

import scala.concurrent.Future

class AllowListServiceSpec extends AnyFreeSpec, Matchers, ScalaFutures:
  private val service1 = Service("service1")
  private val feature1 = Feature("feature1")
  private val identifier = "identifier value"

  ".check" - {
    "returns false when the checks are disabled in configuration" in {
      val repo = FakeAllowListRepository()
      val config = Configuration.from(Map("features.allow-checks" -> "false"))
      val service = AllowListServiceImpl(repo, config)

      service.check(service1, feature1, identifier).futureValue mustEqual false
    }

    "returns false when the checks are enabled in configuration and the repository returns false" in {
      val repo = FakeAllowListRepository(checkResult = Some(false))
      val config = Configuration.from(Map("features.allow-checks" -> "true"))
      val service = AllowListServiceImpl(repo, config)

      service.check(service1, feature1, identifier).futureValue mustEqual false
    }

    "returns true when the checks are enabled in configuration and the repository returns true" in {
      val repo = FakeAllowListRepository(checkResult = Some(true))
      val config = Configuration.from(Map("features.allow-checks" -> "true"))
      val service = AllowListServiceImpl(repo, config)

      service.check(service1, feature1, identifier).futureValue mustEqual true
    }
  }
