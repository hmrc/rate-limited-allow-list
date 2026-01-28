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
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.ratelimitedallowlist.crypto.{OneWayHash, ShaOneWayHash}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListEntry, Feature, Service}

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowListRepositorySpec extends AnyFreeSpecLike, Matchers, DefaultPlayMongoRepositorySupport[AllowListEntry], OptionValues, ScalaFutures {

  private val config = AllowListRepositoryConfig(1)
  private val fixedInstant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val clock = Clock.fixed(fixedInstant, ZoneId.systemDefault())

  private val hashingFn: OneWayHash = ShaOneWayHash(hashKey = "5oTprrr+Aa6BPklnnwRjB+Xnxq3HjX/abQ8o+Q511JA=")
  override protected val repository: AllowListRepositoryImpl =
    AllowListRepositoryImpl(
      mongoComponent = mongoComponent,
      oneWayHash = hashingFn,
      config = config,
      clock = clock
    )

  private val service = Service("service")
  private val feature = Feature("feature")
  private val service1 = Service("service 1")
  private val service2 = Service("service 2")
  private val feature1 = Feature("feature 1")
  private val feature2 = Feature("feature 2")
  private val value1 = "value1"
  private val value2 = "value2"

  ".set" - {
    "must save an entry that does not already exist in the repository, hashing the value" in {
      repository.set(service, feature, value1).futureValue
      val insertedRecord = findAll().futureValue.head

      insertedRecord mustEqual AllowListEntry(service, feature, hashingFn(value1), fixedInstant)
    }

    "must save multiple different entries, hashing their values" in {
      Future.sequence(List(
        repository.set(service, feature, value1),
        repository.set(service, feature, value2)
      )).futureValue

      val insertedRecords = findAll().futureValue

      insertedRecords must contain theSameElementsAs Seq(
        AllowListEntry(service, feature, hashingFn(value1), fixedInstant),
        AllowListEntry(service, feature, hashingFn(value2), fixedInstant)
      )
    }

    "must return without failing when a duplicate records is inserted" in {
      repository.set(service, feature, value1).futureValue
      repository.set(service, feature, value2).futureValue
      repository.set(service, feature, value1).futureValue

      val result = findAll().futureValue

      result must contain theSameElementsAs Seq(
        AllowListEntry(service, feature, hashingFn(value1), fixedInstant),
        AllowListEntry(service, feature, hashingFn(value2), fixedInstant)
      )
    }
  }

  ".remove" - {
    "must remove a matching item" in {
      val entry1 = AllowListEntry(service, feature, hashingFn(value1), fixedInstant)
      val entry2 = AllowListEntry(service, feature, hashingFn(value2), fixedInstant)

      insert(entry1).futureValue
      insert(entry2).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1, entry2)

      val result = repository.remove(service, feature, value1).futureValue

      result mustEqual AllowListDeleteResult.DeleteSuccessful
      findAll().futureValue must contain only entry2
    }

    "will successful return a noop result if there is no item to delete" in {
      val result = repository.remove(service, feature, value1).futureValue

      result mustEqual AllowListDeleteResult.NoOpDeleteResult
      findAll().futureValue must be(empty)
    }

    ".clear" - {

      "must remove all items for a given service and feature" in {
        val entry1 = AllowListEntry(service1, feature1, hashingFn(value1), fixedInstant)
        val entry2 = AllowListEntry(service1, feature1, hashingFn(value2), fixedInstant)
        val entry3 = AllowListEntry(service1, feature2, hashingFn(value1), fixedInstant)
        val entry4 = AllowListEntry(service2, feature1, hashingFn(value1), fixedInstant)

        Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

        repository.clear(service1, feature1).futureValue

        findAll().futureValue must contain theSameElementsAs Seq(entry3, entry4)
      }
    }

    ".check" - {

      "must return true when a record exists for the given service, feature and value" in {
        val entry = AllowListEntry(service1, feature1, hashingFn(value1), fixedInstant)

        insert(entry).futureValue

        repository.check(service1, feature1, value1).futureValue mustBe true
      }

      "must return false when a record for the given service, feature and value does not exist" in {
        val entry1 = AllowListEntry(service1, feature1, hashingFn(value1), fixedInstant)
        val entry2 = AllowListEntry(service1, feature2, hashingFn(value2), fixedInstant)
        val entry3 = AllowListEntry(service2, feature1, hashingFn(value2), fixedInstant)

        Future.sequence(Seq(entry1, entry2, entry3).map(insert)).futureValue

        repository.check(service1, feature1, value2).futureValue mustBe false
      }
    }

    ".count" - {

      "must return the number of documents for a given service and feature" in {
        val entry1 = AllowListEntry(service1, feature1, hashingFn(value1), fixedInstant)
        val entry2 = AllowListEntry(service1, feature1, hashingFn(value2), fixedInstant)
        val entry3 = AllowListEntry(service1, feature2, hashingFn(value1), fixedInstant)
        val entry4 = AllowListEntry(service2, feature1, hashingFn(value1), fixedInstant)

        Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

        repository.count(service1, feature1).futureValue mustBe 2
      }
    }
  }

  given Conversion[Service, String] with 
    def apply(x: Service): String = x.value

  given Conversion[Feature, String] with 
    def apply(x: Feature): String = x.value
}
