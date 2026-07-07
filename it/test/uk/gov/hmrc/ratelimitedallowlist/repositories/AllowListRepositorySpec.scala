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
import uk.gov.hmrc.crypto.{Hasher, Scrambled}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.ratelimitedallowlist.models.domain.Timeframe.{daily, hourly, unbounded, weekdaily, weekly}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListConfiguration, AllowListEntry, Feature, Service, Timeframe}

import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.{DAYS, HOURS}
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class AllowListRepositorySpec extends AnyFreeSpecLike, Matchers, DefaultPlayMongoRepositorySupport[AllowListEntry], OptionValues, ScalaFutures:

  private val config = Configuration.load(Environment.simple())
  private val fixedInstant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val clock = UkTime(Clock.fixed(fixedInstant, ZoneId.systemDefault()))

  override protected val repository: AllowListRepositoryImpl =
    val fakeHasher: Hasher = x => Scrambled(s"hashed-${x.value}")
    AllowListRepositoryImpl(
      mongoComponent = mongoComponent,
      hasher = fakeHasher,
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
  private val value1Hashed = s"hashed-$value1"
  private val value2 = "value2"
  private val value2Hashed = s"hashed-$value2"

  ".set" - {
    "must save an entry that does not already exist in the repository, hashing the value" in:
      repository.set(service, feature, value1).futureValue
      val insertedRecord = findAll().futureValue.head

      insertedRecord mustEqual AllowListEntry(service, feature, value1Hashed, fixedInstant)

    "must save multiple different entries, hashing their values" in:
      Future.sequence(List(
        repository.set(service, feature, value1),
        repository.set(service, feature, value2)
      )).futureValue

      val insertedRecords = findAll().futureValue

      insertedRecords must contain theSameElementsAs Seq(
        AllowListEntry(service, feature, value1Hashed, fixedInstant),
        AllowListEntry(service, feature, value2Hashed, fixedInstant)
      )

    "must return without failing when a duplicate records is inserted" in:
      repository.set(service, feature, value1).futureValue
      repository.set(service, feature, value2).futureValue
      repository.set(service, feature, value1).futureValue

      val result = findAll().futureValue

      result must contain theSameElementsAs Seq(
        AllowListEntry(service, feature, value1Hashed, fixedInstant),
        AllowListEntry(service, feature, value2Hashed, fixedInstant)
      )
  }

  ".clear" - {
    "must remove all items for a given service and feature" in:
      val entry1 = AllowListEntry(service1, feature1, value1Hashed, fixedInstant)
      val entry2 = AllowListEntry(service1, feature1, value2Hashed, fixedInstant)
      val entry3 = AllowListEntry(service1, feature2, value1Hashed, fixedInstant)
      val entry4 = AllowListEntry(service2, feature1, value1Hashed, fixedInstant)

      Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

      repository.clear(service1, feature1).futureValue

      findAll().futureValue must contain theSameElementsAs Seq(entry3, entry4)
  }

  ".checkExists" - {
    "must return true when a record exists for the given service, feature and value" in:
      val entry = AllowListEntry(service1, feature1, value1Hashed, fixedInstant)

      insert(entry).futureValue

      repository.checkExists(service1, feature1, value1).futureValue mustBe true

    "must return false when a record for the given service, feature and value does not exist" in:
      val entry1 = AllowListEntry(service1, feature1, value1Hashed, fixedInstant)
      val entry2 = AllowListEntry(service1, feature2, value2Hashed, fixedInstant)
      val entry3 = AllowListEntry(service2, feature1, value2Hashed, fixedInstant)

      Future.sequence(Seq(entry1, entry2, entry3).map(insert)).futureValue

      repository.checkExists(service1, feature1, value2).futureValue mustBe false
  }

  ".count" - {
    "must return the number of documents for a given service and feature" in:
      val entry1 = AllowListEntry(service1, feature1, value1Hashed, fixedInstant)
      val entry2 = AllowListEntry(service1, feature1, value2Hashed, fixedInstant)
      val entry3 = AllowListEntry(service1, feature2, value1Hashed, fixedInstant)
      val entry4 = AllowListEntry(service2, feature1, value1Hashed, fixedInstant)

      Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

      repository.count(service1, feature1).futureValue mustBe 2

    "must return 0 when there is no matching of documents for a given service and feature" in:
      repository.count(service1, feature1).futureValue mustBe 0
  }

  ".countWithinTimeframe" - {
    def testConfig(timeframe: Timeframe = daily) = AllowListConfiguration(
      service = service1.value,
      feature = feature1.value,
      userLimit = 20,
      userLimitPerTimeframe = 5,
      timeframe = timeframe,
      percentageLoad = 100,
      acceptedCounter = 3,
      totalCounter = 3,
      created = clock.now
    )

    def entry(time: Instant) = AllowListEntry(service1, feature1, Random.nextString(5), time)

    "must only return the counts of documents for a given service and feature" in:
      val entry1 = AllowListEntry(service1, feature1, value1Hashed, fixedInstant)
      val entry2 = AllowListEntry(service1, feature1, value2Hashed, fixedInstant)
      val entry3 = AllowListEntry(service1, feature2, value1Hashed, fixedInstant)
      val entry4 = AllowListEntry(service2, feature1, value1Hashed, fixedInstant)

      Future.sequence(Seq(entry1, entry2, entry3, entry4).map(insert)).futureValue

      repository.countWithinTimeframe(testConfig()).futureValue mustBe (2,2)

    "must correctly count documents within HOURLY timeframe as well as total count for a given service and feature" in:
      val maxYesterday = entry(clock.now.truncatedTo(HOURS).minusNanos(1)) // N-1:59:59 - outside
      val minToday = entry(clock.now.truncatedTo(HOURS)) // N:00:00 - inside
      val now = entry(fixedInstant) // Now - inside
      val maxToday = entry(clock.now.plus(1, HOURS).truncatedTo(HOURS).minusNanos(1)) // N:59:59 - inside
      val minTomorrow = entry(clock.now.plus(1, HOURS).truncatedTo(HOURS)) // N+1:00:00 - outside

      Future.sequence(Seq(maxYesterday, minToday, now, maxToday, minTomorrow).map(insert)).futureValue

      repository.countWithinTimeframe(testConfig(hourly)).futureValue mustBe (3,5)

    "must correctly count documents within DAY timeframe as well as total count for a given service and feature" in:
      val maxSunday = entry(clock.now.truncatedTo(DAYS).minusNanos(1)) // Yesterday, 23:59:59 - outside
      val minMonday = entry(clock.now.truncatedTo(DAYS)) // Today, 00:00:00 - inside
      val now = entry(fixedInstant) // Now - inside
      val maxNextSunday = entry(clock.now.plus(1, DAYS).truncatedTo(DAYS).minusNanos(1)) // Today 23:59:59 - inside
      val minNextMonday = entry(clock.now.plus(1, DAYS).truncatedTo(DAYS)) // Tomorrow 00:00:00 - outside

      Future.sequence(Seq(maxSunday, minMonday, now, maxNextSunday, minNextMonday).map(insert)).futureValue

      repository.countWithinTimeframe(testConfig()).futureValue mustBe (3,5)

    "must correctly count documents within WEEKLY timeframe as well as total count for a given service and feature" in:
      val maxYesterday = entry(clock.startOfWeek.minusNanos(1)) // previous Sunday, 23:59:59 - outside
      val minToday = entry(clock.startOfWeek) // Monday, 00:00:00 - inside
      val now = entry(fixedInstant) // Now - inside
      val maxToday = entry(clock.nextWeek.minusNanos(1)) // Sunday 23:59:59 - inside
      val minTomorrow = entry(clock.nextWeek) // next Monday 00:00:00 - outside

      Future.sequence(Seq(maxYesterday, minToday, now, maxToday, minTomorrow).map(insert)).futureValue

      repository.countWithinTimeframe(testConfig(weekly)).futureValue mustBe (3,5)

    "must correctly count documents within WEEKDAILY timeframe as well as total count for a given service and feature" in:
      val mondayBefore0859 = entry(clock.startOfWorkingWeek.minusNanos(1))
      val mondayAt0900 = entry(clock.startOfWorkingWeek)
      val fridayAt1659 = entry(clock.endOfWorkingWeek.minusNanos(1))
      val fridayAt1700 = entry(clock.endOfWorkingWeek)

      Future.sequence(Seq(mondayBefore0859, mondayAt0900, fridayAt1659, fridayAt1700).map(insert)).futureValue

      repository.countWithinTimeframe(testConfig(weekdaily)).futureValue mustBe (2,4)

    "must correctly count documents with a variety of timeframes as well as total count for a given service and feature" in:
      val beforeThisWeek = entry(clock.startOfWeek.minusNanos(1))
      val beforeThisWorkingWeek = entry(clock.startOfWorkingWeek.minusNanos(1))
      val beforeToday = entry(clock.now.truncatedTo(DAYS).minusNanos(1))
      val beforeThisHour = entry(clock.now.truncatedTo(HOURS).minusNanos(1))
      val now = entry(fixedInstant)

      Future.sequence(Seq(beforeThisWeek, beforeThisWorkingWeek, beforeToday, beforeThisHour, now).map(insert)).futureValue

      repository.countWithinTimeframe(testConfig(unbounded)).futureValue mustBe (5,5)
      repository.countWithinTimeframe(testConfig(weekly)).futureValue mustBe (4,5)
      repository.countWithinTimeframe(testConfig(weekdaily)).futureValue mustBe (3,5)
      repository.countWithinTimeframe(testConfig(daily)).futureValue mustBe (2,5)
      repository.countWithinTimeframe(testConfig(hourly)).futureValue mustBe (1,5)
  }

  given Conversion[Service, String] with 
    def apply(x: Service): String = x.value

  given Conversion[Feature, String] with 
    def apply(x: Feature): String = x.value
