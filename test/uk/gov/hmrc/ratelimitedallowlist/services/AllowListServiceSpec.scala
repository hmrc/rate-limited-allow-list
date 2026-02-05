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
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Configuration
import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.models.domain.CheckResult.{Added, Excluded, Exists}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.{NoOpUpdateResult, UpdateSuccessful}
import uk.gov.hmrc.ratelimitedallowlist.repositories.{FakeAllowListMetadataRepository, FakeAllowListRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class AllowListServiceSpec extends AnyFreeSpec, Matchers, ScalaFutures:
  private val service1 = Service("service1")
  private val feature1 = Feature("feature1")
  private val identifier = "identifier value"

  ".checkExists" - {
    "when checkExists are disabled in configuration" - {
      "returns false" in {
        val metadataRepository = FakeAllowListMetadataRepository()
        val allowListRepository = FakeAllowListRepository()
        val config = Configuration.from(Map("features.allow-checks" -> "false"))
        val service = AllowListServiceImpl(metadataRepository, allowListRepository, config)

        service.checkOrAdd(service1, feature1, identifier).futureValue mustEqual Excluded
      }
    }

    "when checks are enabled" - {
      "returns false" - {
        "when the metadataRepo does not issues a token and allowListRepo returns false" in {
          val metadataRepository = FakeAllowListMetadataRepository(issueTokenResult = Some(NoOpUpdateResult))
          val allowListRepository = FakeAllowListRepository(checkResult = Some(false))
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = AllowListServiceImpl(metadataRepository, allowListRepository, config)

          service.checkOrAdd(service1, feature1, identifier).futureValue mustEqual Excluded
        }
      }

      "returns true" - {
        "when the metadataRepo issues a token and allowListRepo checkExists returns false" in {
          val metadataRepository = FakeAllowListMetadataRepository(issueTokenResult = Some(UpdateSuccessful))
          val allowListRepository = FakeAllowListRepository(checkResult = Some(false), setResult = Some(Done))
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = AllowListServiceImpl(metadataRepository, allowListRepository, config)

          service.checkOrAdd(service1, feature1, identifier).futureValue mustEqual Added
 }
        
        "when the allowListRepo returns true" in {
          val metadataRepository = FakeAllowListMetadataRepository()
          val allowListRepository = FakeAllowListRepository(checkResult = Some(true))
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = AllowListServiceImpl(metadataRepository, allowListRepository, config)

          service.checkOrAdd(service1, feature1, identifier).futureValue mustEqual Exists
        }
      }
    }
  }
