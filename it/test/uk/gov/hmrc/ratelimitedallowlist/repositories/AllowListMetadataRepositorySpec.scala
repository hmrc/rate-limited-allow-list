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
import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.NoOpUpdateResult
import uk.gov.hmrc.ratelimitedallowlist.utils.TimeTravelClock

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowListMetadataRepositorySpec extends AnyFreeSpecLike, Matchers, DefaultPlayMongoRepositorySupport[AllowListMetadata], OptionValues, ScalaFutures {

  private val config = AllowListRepositoryConfig(1, true)
  private val fixedInstant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val clock = TimeTravelClock(fixedInstant)

  override protected val repository: AllowListMetadataRepositoryImpl =
    AllowListMetadataRepositoryImpl(mongoComponent, config, clock)

  private val service1 = Service("service 1")
  private val service2 = Service("service 2")
  private val feature1 = Feature("feature 1")
  private val feature2 = Feature("feature 2")

  override def beforeEach(): Unit = {
    clock.resetTimeTravel()
    super.beforeEach()
  }

  ".create" - {
    "must save an entry that does not already exist in the repository and set values to initialised values" in {
      Future.sequence(List(
        repository.create(service1, feature1),
        repository.create(service1, feature2),
        repository.create(service2, feature1),
        repository.create(service2, feature2)
      )).futureValue

      val insertedRecord = findAll().futureValue

      insertedRecord must contain theSameElementsAs List(
        AllowListMetadata(service1, feature1, 0, false, fixedInstant, fixedInstant),
        AllowListMetadata(service1, feature2, 0, false, fixedInstant, fixedInstant),
        AllowListMetadata(service2, feature1, 0, false, fixedInstant, fixedInstant),
        AllowListMetadata(service2, feature2, 0, false, fixedInstant, fixedInstant),
      )
    }

    "must return without failing or overwriting fields when a duplicate records is inserted" in {
      Future.sequence(List(
        repository.create(service1, feature1),
        repository.create(service1, feature2)
      )).futureValue

      clock.fastForwardTime(60)

      val insertResult = repository.create(service1, feature1).futureValue
      insertResult mustEqual Done

      val result = findAll().futureValue

      result must contain theSameElementsAs Seq(
        AllowListMetadata(service1, feature1, 0, false, fixedInstant, fixedInstant),
        AllowListMetadata(service1, feature2, 0, false, fixedInstant, fixedInstant)
      )
    }
  }

  ".get" - {
    "returns the entry with the matching service and feature" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, true, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 0, true, fixedInstant, fixedInstant)
      val entry3 = AllowListMetadata(service2, feature1, 0, true, fixedInstant, fixedInstant)
      val entry4 = AllowListMetadata(service2, feature2, 0, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      repository.get(service1, feature1).futureValue.value mustEqual entry1
    }

    "returns None when there are no matching service and feature" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, true, fixedInstant, fixedInstant)

      insert(entry1).futureValue

      repository.get(service1, feature2).futureValue mustBe None
      repository.get(service2, feature1).futureValue mustBe None
    }
  }

  ".clear" - {
    "must remove a matching item" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, true, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 0, true, fixedInstant, fixedInstant)
      val entry3 = AllowListMetadata(service2, feature1, 0, true, fixedInstant, fixedInstant)
      val entry4 = AllowListMetadata(service2, feature2, 0, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1, entry2, entry3, entry4)

      val result = repository.clear(service1, feature1).futureValue

      result mustEqual DeleteResult.DeleteSuccessful

      findAll().futureValue must contain theSameElementsAs Seq(entry2, entry3, entry4)
    }

    "will successful return a noop result if there is no item to delete" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, true, fixedInstant, fixedInstant)
      insert(entry1).futureValue

      val result = repository.clear(service1, feature2).futureValue

      result mustEqual DeleteResult.NoOpDeleteResult
      findAll().futureValue mustEqual List(entry1)
    }

  }

  ".addTokens" - {
    "succeeds when the increment value is positive and updates the updated field" in {
      val entry1 = AllowListMetadata(service1, feature1, 10, false, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 10, true, fixedInstant, fixedInstant)
      val entry3 = AllowListMetadata(service2, feature1, 10, true, fixedInstant, fixedInstant)
      val entry4 = AllowListMetadata(service2, feature2, 10, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      clock.fastForwardTime(60)
      val time1 = clock.instant()

      val result1 = repository.addTokens(service1, feature1, 10).futureValue
      result1 mustEqual UpdateResultResult.UpdateSuccessful

      clock.fastForwardTime(60)
      val time2 = clock.instant()
      
      val result2 = repository.addTokens(service2, feature2, 189).futureValue
      result2 mustEqual UpdateResultResult.UpdateSuccessful

      findAll().futureValue must contain theSameElementsAs List(
        entry1.copy(tokenCount = 20, lastUpdated = time1),
        entry2,
        entry3,
        entry4.copy(tokenCount = 199, lastUpdated = time2)
      )
    }

    "returns a noOp result when there is no service or feature" in {
      val result = repository.addTokens(service1, feature1, 10).futureValue
      result mustEqual UpdateResultResult.NoOpUpdateResult
    }

    "returns a failed future with IllegalArgumentException when the increment value is invalid" in {
      val entry1 = AllowListMetadata(service1, feature1, 10, true, fixedInstant, fixedInstant)
      insert(entry1).futureValue

      val result1 = repository.addTokens(service1, feature1, -1).failed.futureValue
      val result2 = repository.addTokens(service1, feature1, 0).failed.futureValue

      result1 mustBe an[IllegalArgumentException]
      result2 mustBe an[IllegalArgumentException]

      findAll().futureValue must contain theSameElementsAs List(entry1)
    }
  }

  ".stopIssuingTokens" - {
    "set the canIssueTokens field to false" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, true, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 0, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2).map(insert)).futureValue

      clock.fastForwardTime(60)

      findAll().futureValue must contain theSameElementsAs Seq(entry1, entry2)

      repository.stopIssuingTokens(service1, feature1).futureValue

      findAll().futureValue must contain theSameElementsAs List(
        entry1.copy(canIssueTokens = false, lastUpdated = clock.instant()),
        entry2
      )
    }

    "returns a noOp result when there is no matching service and feature" in {
      val result = repository.stopIssuingTokens(service2, feature2).futureValue
      result mustEqual UpdateResultResult.NoOpUpdateResult
    }
  }

  ".startIssuingTokens" - {
    "set the canIssueTokens field to false" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, false, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 0, false, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2).map(insert)).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry1, entry2)

      clock.fastForwardTime(60)

      repository.startIssuingTokens(service1, feature1).futureValue

      findAll().futureValue must contain theSameElementsAs List(
        entry1.copy(canIssueTokens = true, lastUpdated = clock.instant()),
        entry2
      )
    }

    "returns a noOp result when there is no matching service and feature" in {
      val result = repository.startIssuingTokens(service2, feature2).futureValue
      result mustEqual UpdateResultResult.NoOpUpdateResult
    }
  }

  ".issueToken" - {
    "returns true and decrements the token count when the service has a non-zero positive number of tokens" in {
      val entry1 = AllowListMetadata(service1, feature1, 10, true, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 0, true, fixedInstant, fixedInstant)
      val entry3 = AllowListMetadata(service2, feature1, 10, true, fixedInstant, fixedInstant)
      val entry4 = AllowListMetadata(service2, feature2, 10, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      clock.fastForwardTime(60)

      val result1 = repository.issueToken(service1, feature1).futureValue
      result1 mustEqual UpdateResultResult.UpdateSuccessful

      clock.fastForwardTime(60)
      val time2 = clock.instant()

      val result2 = repository.issueToken(service1, feature1).futureValue
      result2 mustEqual UpdateResultResult.UpdateSuccessful

      clock.fastForwardTime(60)
      val time3 = clock.instant()

      val result3 = repository.issueToken(service2, feature2).futureValue
      result3 mustEqual UpdateResultResult.UpdateSuccessful

      findAll().futureValue must contain theSameElementsAs List(
        entry1.copy(tokenCount = 8, lastUpdated = time2),
        entry2,
        entry3,
        entry4.copy(tokenCount = 9, lastUpdated = time3),
      )
    }

    "returns a noOp result and does not change the token count when the service has zero tokens" in {
      val entry1 = AllowListMetadata(service1, feature1, 0, true, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 10, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2).map(insert)).futureValue

      clock.fastForwardTime(60)

      val result1 = repository.issueToken(service1, feature1).futureValue
      result1 mustEqual UpdateResultResult.NoOpUpdateResult

      findAll().futureValue must contain theSameElementsAs List(
        entry1,
        entry2
      )
    }

    "returns false and does not change the token count for records where the canIssueTokens field is false" in {
      val entry1 = AllowListMetadata(service1, feature1, 10, false, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 10, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2).map(insert)).futureValue

      clock.fastForwardTime(60)

      val result1 = repository.issueToken(service1, feature1).futureValue
      result1 mustEqual UpdateResultResult.NoOpUpdateResult

      val result3 = repository.issueToken(service1, feature2).futureValue
      result3 mustEqual UpdateResultResult.UpdateSuccessful

      findAll().futureValue must contain theSameElementsAs List(
        entry1,
        entry2.copy(tokenCount = 9, lastUpdated = clock.instant())
      )
    }

    "returns a noOp result when there is no matching service and feature" in {
      val result = repository.issueToken(service2, feature2).futureValue
      result mustEqual UpdateResultResult.NoOpUpdateResult
    }
  }

  ".setTokens" - {
    "returns true and decrements the token count when the service has a non-zero positive number of tokens" in {
      val entry1 = AllowListMetadata(service1, feature1, 10, true, fixedInstant, fixedInstant)
      val entry2 = AllowListMetadata(service1, feature2, 0, true, fixedInstant, fixedInstant)
      val entry3 = AllowListMetadata(service2, feature1, 10, true, fixedInstant, fixedInstant)
      val entry4 = AllowListMetadata(service2, feature2, 10, true, fixedInstant, fixedInstant)

      Future.sequence(List(entry1, entry2, entry3, entry4).map(insert)).futureValue

      clock.fastForwardTime(60)

      val result1 = repository.setTokens(service1, feature1, 5).futureValue
      result1 mustEqual UpdateResultResult.UpdateSuccessful

      val result2 = repository.setTokens(service1, feature2, 100).futureValue
      result2 mustEqual UpdateResultResult.UpdateSuccessful

      val result3 = repository.setTokens(service2, feature1, 0).futureValue
      result3 mustEqual UpdateResultResult.UpdateSuccessful
      
      val result4 = repository.setTokens(service2, feature2, 10).futureValue
      result4 mustEqual UpdateResultResult.UpdateSuccessful

      findAll().futureValue must contain theSameElementsAs List(
        entry1.copy(tokenCount = 5, lastUpdated = clock.instant()),
        entry2.copy(tokenCount = 100, lastUpdated = clock.instant()),
        entry3.copy(tokenCount = 0, lastUpdated = clock.instant()),
        entry4.copy(lastUpdated = clock.instant()),
      )
    }

    "returns a noOp result when there is no matching service and feature" in {
      val result = repository.setTokens(service1, feature1, 100).futureValue
      result mustEqual UpdateResultResult.NoOpUpdateResult
    }
  }



  given Conversion[Service, String] with 
    def apply(x: Service): String = x.value

  given Conversion[Feature, String] with 
    def apply(x: Feature): String = x.value
}
