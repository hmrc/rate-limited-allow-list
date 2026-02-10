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

import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers
import play.api.{Configuration, Environment}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListMetadataRepositoryImpl, FakeAllowListRepository}
import uk.gov.hmrc.ratelimitedallowlist.utils.TimeTravelClock

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ConfigUpdateServiceSpec extends AnyFreeSpecLike, Matchers, OptionValues, ScalaFutures, DefaultPlayMongoRepositorySupport[AllowListMetadata]:

  private val baseConfig = Configuration.load(Environment.simple())
  private val clock = TimeTravelClock()

  private val service1 = Service("service 1")
  private val service2 = Service("service 2")
  private val feature1 = Feature("feature 1")
  private val feature2 = Feature("feature 2")

  override val repository: AllowListMetadataRepositoryImpl =
    AllowListMetadataRepositoryImpl(mongoComponent, baseConfig, clock)

  override def beforeEach(): Unit = {
    clock.resetTimeTravel()
    super.beforeEach()
  }

  "token update via config" - {
    "will create config for service and feature if not created and set the tokens" in {
      val config = Configuration(
        "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
        "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
        "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> 100,
        "features.allow-config-token-updates" -> true
      ).withFallback(baseConfig)
      
      ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(0)), config).initCompleted.futureValue

      findAll().futureValue must contain theSameElementsAs List(
        AllowListMetadata(service1, feature1, 100, true, clock.instant(), clock.instant())
      )
    }

    "will update the token count" - {
      "when the update sets the max users above current number of users and the token count will be increased" in {
        val entry1 = AllowListMetadata(service1, feature1, 10, true, clock.instant(), clock.instant())
        insert(entry1).futureValue

        clock.fastForwardTime(60)

        val config = Configuration(
          "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> 100,
          "features.allow-config-token-updates" -> true
        ).withFallback(baseConfig)
        ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(10)), config).initCompleted.futureValue

        findAll().futureValue must contain theSameElementsAs List(
          entry1.copy(tokens = 90, lastUpdated = clock.instant())
        )
      }

      "when the update sets the max users above current number of users and the token count will be decreased" in {
        val entry1 = AllowListMetadata(service1, feature1, 10, true, clock.instant(), clock.instant())
        insert(entry1).futureValue

        clock.fastForwardTime(60)

        val config = Configuration(
          "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> 20,
          "features.allow-config-token-updates" -> true
        ).withFallback(baseConfig)
        ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(15)), config).initCompleted.futureValue

        findAll().futureValue must contain theSameElementsAs List(
          entry1.copy(tokens = 5, lastUpdated = clock.instant())
        )
      }

      "when the update sets the max users below current number of users" in {
        val entry1 = AllowListMetadata(service1, feature1, 10, true, clock.instant(), clock.instant())
        insert(entry1).futureValue

        clock.fastForwardTime(60)

        val config = Configuration(
          "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> 20,
          "features.allow-config-token-updates" -> true
        ).withFallback(baseConfig)
        ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(100)), config).initCompleted.futureValue

        findAll().futureValue must contain theSameElementsAs List(
          entry1.copy(tokens = 0, lastUpdated = clock.instant())
        )
      }

      "when there are multiple configs that have not been applied" in {
        val entry1 = AllowListMetadata(service1, feature1, 10, true, clock.instant(), clock.instant())
        val entry2 = AllowListMetadata(service2, feature2, 10, true, clock.instant(), clock.instant())

        Future.sequence(List(entry1, entry2).map(insert)).futureValue
 
        clock.fastForwardTime(60)

        val config = Configuration(
          "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> 200,
          "mongodb.collections.allow-list-metadata.token-updates.1.service" -> service2.value,
          "mongodb.collections.allow-list-metadata.token-updates.1.feature" -> feature2.value,
          "mongodb.collections.allow-list-metadata.token-updates.1.maxUsers" -> 0,
          "features.allow-config-token-updates" -> true
        ).withFallback(baseConfig)
        ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(10)), config).initCompleted.futureValue

        findAll().futureValue must contain theSameElementsAs List(
          entry1.copy(tokens = 190, lastUpdated = clock.instant()),
          entry2.copy(tokens = 0, lastUpdated = clock.instant())
        )
      }
    }

    "will not update the token count" - {
      "when config update is disabled" in {
        val entry1 = AllowListMetadata(service1, feature1, 10, true, clock.instant(), clock.instant())
        val entry2 = AllowListMetadata(service2, feature2, 10, true, clock.instant(), clock.instant())

        Future.sequence(List(entry1, entry2).map(insert)).futureValue

        clock.fastForwardTime(60)

        val config = Configuration(
          "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
          "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> 100,
          "mongodb.collections.allow-list-metadata.token-updates.1.service" -> service2.value,
          "mongodb.collections.allow-list-metadata.token-updates.1.features" -> feature2.value,
          "mongodb.collections.allow-list-metadata.token-updates.1.maxUsers" -> 0,
          "features.allow-config-token-updates" -> false
        ).withFallback(baseConfig)
        ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(10)), config).initCompleted.futureValue
       
        findAll().futureValue must contain theSameElementsAs List(entry1, entry2)
      }
    }

    "fails when the config is invalid" in {
      val config = Configuration(
        "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
        "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
        "mongodb.collections.allow-list-metadata.token-updates.0.maxUsers" -> -100,
        "features.allow-config-token-updates" -> true
      ).withFallback(baseConfig)

      val err = intercept[AssertionError] {
        ConfigUpdatesService(repository, FakeAllowListRepository(countResult = Some(10)), config)
      }

      err.getMessage must include("Invalid value for maxUsers, must be >0")
    }
  }

  given Conversion[Service, String] with
    def apply(x: Service): String = x.value

  given Conversion[Feature, String] with
    def apply(x: Feature): String = x.value
