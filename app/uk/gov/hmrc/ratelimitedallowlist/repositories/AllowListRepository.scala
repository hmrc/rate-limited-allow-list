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
import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListEntry, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.AllowListEntry.Field
import org.mongodb.scala.model.*
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.crypto.OneWayHash

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AllowListRepository {
  def set(service: Service, feature: Feature, value: String): Future[Done]
  def clear(service: Service, feature: Feature): Future[Done]
  def check(service: Service, feature: Feature, value: String): Future[Boolean]
  def count(service: Service, feature: Feature): Future[Long]
}

@Singleton
class AllowListRepositoryImpl @Inject()(mongoComponent: MongoComponent,
                                        config: Configuration,
                                        oneWayHash: OneWayHash,
                                        clock: Clock
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
          .expireAfter(config.get[Long]("mongodb.collections.allow-list.allowListTtlInDays"), TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending(Field.service, Field.feature, Field.hashedValue),
        IndexOptions()
          .name(s"${Field.service}-${Field.feature}-${Field.hashedValue}-idx")
          .unique(true)
      )
    )
  ) with AllowListRepository {

  def set(service: Service, feature: Feature, value: String): Future[Done] = {
    val entry = AllowListEntry(service.value, feature.value, oneWayHash(value), clock.instant())

    collection
      .insertOne(entry)
      .toFuture()
      .map(_ => Done)
      .recover {
        case e: MongoException if e.getCode == DuplicateErrorCode => Done
      }
  }

  def clear(service: Service, feature: Feature): Future[Done] =
    collection
      .deleteMany(Filters.and(
        Filters.equal(Field.service, service.value),
        Filters.equal(Field.feature, feature.value)
      )).toFuture()
      .map(_ => Done)

  def check(service: Service, feature: Feature, value: String): Future[Boolean] =
    collection
      .find(Filters.and(
        Filters.equal(Field.service, service.value),
        Filters.equal(Field.feature, feature.value),
        Filters.equal(Field.hashedValue, oneWayHash(value))
      ))
      .toFuture()
      .map(_.nonEmpty)

  def count(service: Service, feature: Feature): Future[Long] =
    collection.countDocuments(Filters.and(
      Filters.equal(Field.service, service.value),
      Filters.equal(Field.feature, feature.value)
    )).toFuture()
}

