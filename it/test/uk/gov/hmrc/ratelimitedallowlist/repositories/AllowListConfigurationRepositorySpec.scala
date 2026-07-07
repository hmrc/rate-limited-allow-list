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

package uk.gov.hmrc.ratelimitedallowlist.repositories

import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers
import play.api.{Configuration, Environment}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.ratelimitedallowlist.models.domain.Timeframe.daily
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowList, AllowListConfiguration, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.CreateResult.NoOpCreateResult
import uk.gov.hmrc.ratelimitedallowlist.repositories.DeleteResult.*

import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowListConfigurationRepositorySpec
  extends AnyFreeSpecLike, Matchers, DefaultPlayMongoRepositorySupport[AllowListConfiguration], OptionValues, ScalaFutures:

  private val config = Configuration.load(Environment.simple())

  override protected val repository: AllowListConfigurationRepositoryImpl =
    AllowListConfigurationRepositoryImpl(mongoComponent, config)

  private val now = Instant.now.truncatedTo(HOURS)
  private val service1 = "service 1"
  private val service2 = "service 2"
  private val feature1 = "feature 1"
  private val feature2 = "feature 2"

  private def allowListConfiguration(s: String, f: String) = AllowListConfiguration(
    service = s,
    feature = f,
    userLimitPerTimeframe = 0,
    timeframe = daily,
    userLimit = 0,
    percentageLoad = 0,
    created = now
  )

  ".create" - {
    "must save an entry that does not already exist in the repository and set values to initialised values" in:
      Future.sequence(List(
        repository.create(allowListConfiguration(service1, feature1)),
        repository.create(allowListConfiguration(service1, feature2)),
        repository.create(allowListConfiguration(service2, feature1)),
        repository.create(allowListConfiguration(service2, feature2))
      )).futureValue

      val insertedRecord = findAll().futureValue

      insertedRecord must contain theSameElementsAs List(
        allowListConfiguration(service1, feature1),
        allowListConfiguration(service1, feature2),
        allowListConfiguration(service2, feature1),
        allowListConfiguration(service2, feature2),
      )

    "must return without failing or overwriting fields when a duplicate records is inserted" in:
      Future.sequence(List(
        repository.create(allowListConfiguration(service1, feature1)),
        repository.create(allowListConfiguration(service1, feature2))
      )).futureValue

      val insertResult = repository.create(allowListConfiguration(service1, feature1)).futureValue
      insertResult mustEqual NoOpCreateResult

      val result = findAll().futureValue

      result must contain theSameElementsAs Seq(
        allowListConfiguration(service1, feature1),
        allowListConfiguration(service1, feature2)
      )
  }

  ".getServices" - {
    val service3 = Service("service 3")
    val service4 = Service("service 4")

    "returns all services" in:
      val entry1 = allowListConfiguration(service1, feature1)
      val entry2 = allowListConfiguration(service1, feature2)
      val entry3 = allowListConfiguration(service2, feature1)
      val entry4 = allowListConfiguration(service2, feature2)
      val entry5 = allowListConfiguration(service3, feature1)
      val entry6 = allowListConfiguration(service4, feature1)

      Future.sequence(List(entry1, entry2, entry3, entry4, entry5, entry6).map(insert)).futureValue

      repository.getServices().futureValue must contain theSameElementsAs
        List(Service(service1), Service(service2), service3, service4)

    "returns all services that match the filter" in:
      val entry1 = allowListConfiguration(service1, feature1)
      val entry2 = allowListConfiguration(service1, feature2)
      val entry3 = allowListConfiguration(service2, feature1)
      val entry4 = allowListConfiguration(service2, feature2)
      val entry5 = allowListConfiguration(service3, feature1)
      val entry6 = allowListConfiguration(service4, feature1)

      Future.sequence(List(entry1, entry2, entry3, entry4, entry5, entry6).map(insert)).futureValue

      val filter = List(service1, service2)

      repository.getServices(filter).futureValue must contain theSameElementsAs
        List(Service(service1), Service(service2))

    "returns an empty list when there are are no matching services" - {
      "when the filter will exclude all matches" in:
        val entry1 = allowListConfiguration(service1, feature1)
        val entry2 = allowListConfiguration(service1, feature2)
        val entry3 = allowListConfiguration(service2, feature1)
        val entry4 = allowListConfiguration(service2, feature2)

        Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

        val filter = List(service3.value)

        repository.getServices(filter).futureValue must be(empty)

      "when there are no services" in:
        repository.getServices().futureValue must be(empty)
    }
  }

  ".get by service" - {
    "returns the entry with the matching service and feature" in:
      val entry1 = allowListConfiguration(service1, feature1)
      val entry2 = allowListConfiguration(service1, feature2)
      val entry3 = allowListConfiguration(service2, feature1)
      val entry4 = allowListConfiguration(service2, feature2)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      repository.get(Service(service1)).futureValue must contain theSameElementsAs List(entry1, entry2)

    "returns None when there are no matching service and feature" in:
      repository.get(Service(service1)).futureValue mustEqual List.empty
  }

  ".get by service and feature" - {
    "returns the entry with the matching service and feature" in:
      val entry1 = allowListConfiguration(service1, feature1)
      val entry2 = allowListConfiguration(service1, feature2)
      val entry3 = allowListConfiguration(service2, feature1)
      val entry4 = allowListConfiguration(service2, feature2)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      repository.get(AllowList(Service(service1), Feature(feature1))).futureValue.value mustEqual entry1

    "returns None when there are no matching service and feature" in:
      val entry1 = allowListConfiguration(service1, feature1)

      insert(entry1).futureValue

      repository.get(AllowList(Service(service1), Feature(feature2))).futureValue mustBe None
      repository.get(AllowList(Service(service2), Feature(feature1))).futureValue mustBe None
  }

  ".updateAllowListPercentageCounters" - {
    val testAllowList = AllowList(Service(service1), Feature(feature1))

    "must increment only the total when the user is not accepted" in:
      val entry1 = allowListConfiguration(service1, feature1)

      insert(entry1).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1)

      val result = repository.updateAllowListPercentageCounters(testAllowList, false).futureValue

      result mustEqual allowListConfiguration(service1,feature1)

      findAll().futureValue must contain theSameElementsAs Seq(entry1.copy(totalCounter = 1))

    "must increment both the accepted and the total when the user is accepted" in:
      val entry1 = allowListConfiguration(service1, feature1)

      insert(entry1).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1)

      val result = repository.updateAllowListPercentageCounters(testAllowList, true).futureValue

      result mustEqual allowListConfiguration(service1,feature1)

      findAll().futureValue must contain theSameElementsAs Seq(entry1.copy(acceptedCounter = 1, totalCounter = 1))
  }

  ".clear" - {
    "must remove a matching item" in:
      val entry1 = allowListConfiguration(service1, feature1)
      val entry2 = allowListConfiguration(service1, feature2)
      val entry3 = allowListConfiguration(service2, feature1)
      val entry4 = allowListConfiguration(service2, feature2)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1, entry2, entry3, entry4)

      val result = repository.clear(AllowList(Service(service1), Feature(feature1))).futureValue

      result mustEqual DeleteSuccessful

      findAll().futureValue must contain theSameElementsAs Seq(entry2, entry3, entry4)

    "will successful return a noop result if there is no item to delete" in:
      val entry1 = allowListConfiguration(service1, feature1)
      insert(entry1).futureValue

      val result = repository.clear(AllowList(Service(service1), Feature(feature2))).futureValue

      result mustEqual NoOpDeleteResult
      findAll().futureValue mustEqual List(entry1)
  }


  given Conversion[Service, String] with 
    def apply(x: Service): String = x.value

  given Conversion[Feature, String] with 
    def apply(x: Feature): String = x.value
