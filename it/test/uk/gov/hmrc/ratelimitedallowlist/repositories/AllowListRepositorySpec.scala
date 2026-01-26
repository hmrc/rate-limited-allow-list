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
import uk.gov.hmrc.ratelimitedallowlist.crypto.ShaHashingFunction
import uk.gov.hmrc.ratelimitedallowlist.models.domain.AllowListEntry

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowListRepositorySpec extends AnyFreeSpecLike, Matchers, DefaultPlayMongoRepositorySupport[AllowListEntry], OptionValues, ScalaFutures {

  private val config = AllowListRepositoryConfig(1)
  private val fixedInstant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val clock = Clock.fixed(fixedInstant, ZoneId.systemDefault())

  private val hashingFn = ShaHashingFunction(hashKey = "5oTprrr+Aa6BPklnnwRjB+Xnxq3HjX/abQ8o+Q511JA=")
  protected override val repository: AllowListRepository =
    AllowListRepository(
      mongoComponent = mongoComponent,
      hasher = hashingFn,
      config = config,
      clock = clock
    )

  ".set" - {
    "must save an entry that does not already exist in the repository, hashing the value" in {
      val service = "service"
      val feature = "feature"
      val value = "value"

      repository.set(service, feature, value).futureValue
      val insertedRecord = findAll().futureValue.head

      insertedRecord mustEqual AllowListEntry(service, feature, hashingFn.hash(value), fixedInstant)
    }

    "must save multiple different entries, hashing their values" in {
      val service = "service"
      val feature = "feature"
      val value1 = "value1"
      val value2 = "value2"

      Future.sequence(List(
        repository.set(service, feature, value1),
        repository.set(service, feature, value2)
      )).futureValue

      val insertedRecords = findAll().futureValue

      insertedRecords must contain theSameElementsAs Seq(
        AllowListEntry(service, feature, hashingFn.hash(value1), fixedInstant),
        AllowListEntry(service, feature, hashingFn.hash(value2), fixedInstant)
      )
    }

    "must return without failing when a duplicate records is inserted" in {
      val service = "service"
      val feature = "feature"
      val value1 = "value1"
      val value2 = "value2"

      repository.set(service, feature, value1).futureValue
      repository.set(service, feature, value2).futureValue
      repository.set(service, feature, value1).futureValue

      val result = findAll().futureValue

      result must contain theSameElementsAs Seq(
        AllowListEntry(service, feature, hashingFn.hash(value1), fixedInstant),
        AllowListEntry(service, feature, hashingFn.hash(value2), fixedInstant)
      )
    }
  }

  ".remove" - {
    "must remove a matching item" in {
      val service = "service"
      val feature = "feature"
      val value1 = "value1"
      val value2 = "value2"

      val entry1 = AllowListEntry(service, feature, hashingFn.hash(value1), fixedInstant)
      val entry2 = AllowListEntry(service, feature, hashingFn.hash(value2), fixedInstant)

      insert(entry1).futureValue
      insert(entry2).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1, entry2)

      val result = repository.remove(service, feature, value1).futureValue

      result mustEqual AllowListDeleteResult.DeleteSuccessful
      findAll().futureValue must contain only entry2
    }

    "will successful return a noop result if there is no item to delete" in {
      val service = "service"
      val feature = "feature"
      val value1 = "value1"

      val result = repository.remove(service, feature, value1).futureValue

      result mustEqual AllowListDeleteResult.NoOpDeleteResult
      findAll().futureValue must be(empty)
    }

    ".clear" - {

      "must remove all items for a given service and feature" in {
        val value1 = "value1"
        val value2 = "value2"

        val entry1 = AllowListEntry("service 1", "feature 1", hashingFn.hash(value1), fixedInstant)
        val entry2 = AllowListEntry("service 1", "feature 1", hashingFn.hash(value2), fixedInstant)
        val entry3 = AllowListEntry("service 1", "feature 2", hashingFn.hash(value1), fixedInstant)
        val entry4 = AllowListEntry("service 2", "feature 1", hashingFn.hash(value1), fixedInstant)

        Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

        repository.clear("service 1", "feature 1").futureValue

        findAll().futureValue must contain theSameElementsAs Seq(entry3, entry4)
      }
    }

    ".check" - {

      "must return true when a record exists for the given service, feature and value" in {
        val value = "value"
        val entry = AllowListEntry("service", "feature", hashingFn.hash(value), fixedInstant)

        insert(entry).futureValue

        repository.check("service", "feature", value).futureValue mustBe true
      }

      "must return false when a record for the given service, feature and value does not exist" in {

        val value1 = "value1"
        val value2 = "value2"

        val entry1 = AllowListEntry("service", "feature", hashingFn.hash(value2), fixedInstant)
        val entry2 = AllowListEntry("service", "feature 1", hashingFn.hash(value1), fixedInstant)
        val entry3 = AllowListEntry("service 1", "feature", hashingFn.hash(value1), fixedInstant)

        Future.sequence(Seq(entry1, entry2, entry3).map(insert)).futureValue

        repository.check("service", "feature", value1).futureValue mustBe false
      }
    }

    ".count" - {

      "must return the number of documents for a given service and feature" in {

        val value1 = "value1"
        val value2 = "value2"

        val entry1 = AllowListEntry("service 1", "feature 1", hashingFn.hash(value1), fixedInstant)
        val entry2 = AllowListEntry("service 1", "feature 1", hashingFn.hash(value2), fixedInstant)
        val entry3 = AllowListEntry("service 1", "feature 2", hashingFn.hash(value1), fixedInstant)
        val entry4 = AllowListEntry("service 2", "feature 1", hashingFn.hash(value1), fixedInstant)

        Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

        repository.count("service 1", "feature 1").futureValue mustBe 2
      }
    }
  }
}
