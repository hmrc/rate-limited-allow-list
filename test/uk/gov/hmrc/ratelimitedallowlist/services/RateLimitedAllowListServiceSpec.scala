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
import uk.gov.hmrc.ratelimitedallowlist.models.domain.Timeframe.daily
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowList, AllowListConfiguration, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.{FakeAllowListConfigurationRepository, FakeAllowListRepository}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class RateLimitedAllowListServiceSpec extends AnyFreeSpec, Matchers, ScalaFutures:
  private val service1 = Service("service1")
  private val feature1 = Feature("feature1")
  private val allowList1 = AllowList(service1, feature1)
  private val allowListConfig1 = AllowListConfiguration(
    service = service1.value,
    feature = feature1.value,
    userLimitPerTimeframe = 3,
    timeframe = daily,
    userLimit = 15,
    percentageLoad = 50,
    created = Instant.now
  )
  private val identifier = "identifier value"

  ".checkOrAdd" - {
    "when disabled in configuration" - {
      "returns Excluded by default" in:
        val configurationRepository = FakeAllowListConfigurationRepository()
        val allowListRepository = FakeAllowListRepository()
        val config = Configuration.from(Map("features.allow-checks" -> "false"))
        val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

        service.checkOrAdd(allowList1, identifier).futureValue mustEqual Excluded
    }

    "when checks are enabled" - {
      "returns Excluded" - {
        "when there is no configuration found" in :
          val configurationRepository = FakeAllowListConfigurationRepository(getResult = Some(None))
          val allowListRepository = FakeAllowListRepository(checkResult = Some(false))
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

          service.checkOrAdd(allowList1, identifier).futureValue mustEqual Excluded

        "when the percentage balancer in configuration is set to zero percent" in :
          val configurationRepository = FakeAllowListConfigurationRepository(
            getResult = Some(Some(allowListConfig1.copy(percentageLoad = 0))),
            updateResult = Some(allowListConfig1)
          )
          val allowListRepository = FakeAllowListRepository(
            checkResult = Some(false)
          )
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

          service.checkOrAdd(allowList1, identifier).futureValue mustEqual Excluded

        "when the percentage balancer in configuration rejects the user" in :
          val configurationRepository = FakeAllowListConfigurationRepository(
            getResult = Some(Some(allowListConfig1.copy(acceptedCounter = 8, totalCounter = 10, percentageLoad = 80))),
            updateResult = Some(allowListConfig1)
          )
          val allowListRepository = FakeAllowListRepository(checkResult = Some(false))
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

          service.checkOrAdd(allowList1, identifier).futureValue mustEqual Excluded

        "when the total limit has already been reached" in :
          val configurationRepository = FakeAllowListConfigurationRepository(
            getResult = Some(Some(allowListConfig1.copy(acceptedCounter = 20, totalCounter = 50, percentageLoad = 80))),
            updateResult = Some(allowListConfig1)
          )
          val allowListRepository = FakeAllowListRepository(
            checkResult = Some(false),
            countWithinTimeframe = Some((20, 30))
          )
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

          service.checkOrAdd(allowList1, identifier).futureValue mustEqual Excluded

        "when the timeframe limit has already been reached" in :
          val configurationRepository = FakeAllowListConfigurationRepository(
            getResult = Some(Some(allowListConfig1.copy(acceptedCounter = 10, totalCounter = 50, percentageLoad = 80))),
            updateResult = Some(allowListConfig1)
          )
          val allowListRepository = FakeAllowListRepository(
            checkResult = Some(false),
            countWithinTimeframe = Some((10, 10))
          )
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

          service.checkOrAdd(allowList1, identifier).futureValue mustEqual Excluded
      }

      "returns Added" - {
        "when the percentage balancer in configuration accepts the user and they aren't preexisting" in :
          List(
            allowListConfig1.copy(acceptedCounter = 0, totalCounter = 0, percentageLoad = 1),
            allowListConfig1.copy(acceptedCounter = 8, totalCounter = 10, percentageLoad = 81)
          ).foreach { testConfig =>
            val configurationRepository = FakeAllowListConfigurationRepository(
              getResult = Some(Some(testConfig)),
              updateResult = Some(testConfig)
            )
            val allowListRepository = FakeAllowListRepository(
              checkResult = Some(false),
              countWithinTimeframe = Some((1, 10)),
              setResult = Some(Done)
            )
            val config = Configuration.from(Map("features.allow-checks" -> "true"))
            val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

            service.checkOrAdd(allowList1, identifier).futureValue mustEqual Added
          }
      }

      "returns Exists" - {
        "when the user is already in the database" in :
          val configurationRepository = FakeAllowListConfigurationRepository(getResult = Some(Some(allowListConfig1)))
          val allowListRepository = FakeAllowListRepository(checkResult = Some(true))
          val config = Configuration.from(Map("features.allow-checks" -> "true"))
          val service = RateLimitedAllowListServiceImpl(configurationRepository, allowListRepository, config)

          service.checkOrAdd(allowList1, identifier).futureValue mustEqual Exists
      }
    }
  }