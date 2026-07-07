/*
 * Copyright 2023 HM Revenue & Customs
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

import com.mongodb.MongoException
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import play.api.Configuration
import play.api.libs.json.{JsValue, Json, OFormat}
import uk.gov.hmrc.crypto.{Hasher, PlainText}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.models.domain.AllowListEntry.Field
import uk.gov.hmrc.ratelimitedallowlist.models.domain.*

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class Res(count: Int, usersInTimeframe: Int)
object Res:
  implicit val format: OFormat[Res] = Json.format[Res]

trait AllowListRepository:
  def set(service: Service, feature: Feature, value: String): Future[Done]
  def clear(service: Service, feature: Feature): Future[Done]
  def checkExists(service: Service, feature: Feature, value: String): Future[Boolean]
  def count(service: Service, feature: Feature): Future[Long]
  def countWithinTimeframe(config: AllowListConfiguration): Future[(Long, Long)]

@Singleton
class AllowListRepositoryImpl @Inject()(mongoComponent: MongoComponent,
                                        config: Configuration,
                                        hasher: Hasher,
                                        clock: UkTime
                                   )(using ExecutionContext)
  extends PlayMongoRepository[AllowListEntry](
    collectionName = "allow-list",
    mongoComponent = mongoComponent,
    domainFormat = AllowListEntry.format,
    replaceIndexes = config.get[Boolean]("mongodb.collections.allow-list.replaceIndexes"),
    indexes = Seq(
      IndexModel(
        Indexes.ascending(Field.created),
        IndexOptions()
          .name(s"${Field.created}-idx")
          .expireAfter(config.get[Long]("mongodb.collections.allow-list.ttlInDays"), TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending(Field.service, Field.feature, Field.hashedValue),
        IndexOptions()
          .name(s"${Field.service}-${Field.feature}-${Field.hashedValue}-idx")
          .unique(true)
      )
    )
  ) with AllowListRepository:

  override def set(service: Service, feature: Feature, value: String): Future[Done] =
    val hashedValue = hasher.hash(PlainText(value)).value
    val entry = AllowListEntry(service.value, feature.value, hashedValue, clock.now)

    collection
      .insertOne(entry)
      .toFuture()
      .map(_ => Done)
      .recover:
        case e: MongoException if e.getCode == DuplicateErrorCode => Done

  override def clear(service: Service, feature: Feature): Future[Done] =
    collection
      .deleteMany(Filters.and(
        Filters.equal(Field.service, service.value),
        Filters.equal(Field.feature, feature.value)
      ))
      .toFuture()
      .map(_ => Done)

  override def checkExists(service: Service, feature: Feature, value: String): Future[Boolean] =
    val hashedValue = hasher.hash(PlainText(value)).value
    collection
      .find(Filters.and(
        Filters.equal(Field.service, service.value),
        Filters.equal(Field.feature, feature.value),
        Filters.equal(Field.hashedValue, hashedValue)
      ))
      .toFuture()
      .map(_.nonEmpty)

  override def count(service: Service, feature: Feature): Future[Long] =
    collection.countDocuments(Filters.and(
      Filters.equal(Field.service, service.value),
      Filters.equal(Field.feature, feature.value)
    )).toFuture()

  def countWithinTimeframe(config: AllowListConfiguration): Future[(Long, Long)] =
    val totalQuery = collection.countDocuments(Filters.and(
      Filters.equal(Field.service, config.service),
      Filters.equal(Field.feature, config.feature)
    )).toFuture()

    val timeframeQuery = collection.countDocuments(Filters.and(
      Filters.equal(Field.service, config.service),
      Filters.equal(Field.feature, config.feature),
      filterWithin(config.timeframe)
    )).toFuture()

    for
      total <- totalQuery
      inTimeframe <- timeframeQuery
    yield (inTimeframe, total)

  private def filterBetween(lower: Instant, upper: Instant) =
    Filters.and(Filters.gte("created", lower), Filters.lt("created", upper))

  private val filterWithin = (timeframe: Timeframe) => timeframe match
    case Timeframe.unbounded => Filters.lte("created", clock.now)
    case Timeframe.hourly    => filterBetween(clock.thisHour, clock.nextHour)
    case Timeframe.daily     => filterBetween(clock.today, clock.tomorrow)
    case Timeframe.weekly    => filterBetween(clock.startOfWeek, clock.nextWeek)
    case Timeframe.weekdaily => filterBetween(clock.startOfWorkingWeek, clock.endOfWorkingWeek)
