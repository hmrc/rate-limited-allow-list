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
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.crypto.OneWayHash

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AllowListRepository {
  def set(service: Service, feature: Feature, value: String): Future[Done]
  def remove(service: Service, feature: Feature, value: String): Future[AllowListDeleteResult]
  def clear(service: Service, feature: Feature): Future[Done]
  def check(service: Service, feature: Feature, value: String): Future[Boolean]
  def count(service: Service, feature: Feature): Future[Long]
}

@Singleton
class AllowListRepositoryImpl @Inject()(mongoComponent: MongoComponent,
                                        config: AllowListRepositoryConfig,
                                        oneWayHash: OneWayHash,
                                        clock: Clock
                                   )(using ExecutionContext)
  extends PlayMongoRepository[AllowListEntry](
    collectionName = "allow-list",
    mongoComponent = mongoComponent,
    domainFormat = AllowListEntry.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("created"),
        IndexOptions()
          .name("createdIdx")
          .expireAfter(config.allowListTtlInDays, TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending("service", "feature", "hashedValue"),
        IndexOptions()
          .name("serviceListHashedValueIdx")
          .unique(true)
      )
    )
  ) with AllowListRepository {

  import AllowListRepository.*

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

  def remove(service: Service, feature: Feature, value: String): Future[AllowListDeleteResult] =
    val hashedValue = oneWayHash(value)
    collection
      .deleteMany(Filters.and(
        Filters.equal("service", service.value),
        Filters.equal("feature", feature.value),
        Filters.equal("hashedValue", hashedValue)
      )).toFuture()
      .map {
        case deleteResult if deleteResult.getDeletedCount == 0 => AllowListDeleteResult.NoOpDeleteResult
        case _ => AllowListDeleteResult.DeleteSuccessful
      }

  def clear(service: Service, feature: Feature): Future[Done] =
    collection
      .deleteMany(Filters.and(
        Filters.equal("service", service.value),
        Filters.equal("feature", feature.value)
      )).toFuture()
      .map(_ => Done)

  def check(service: Service, feature: Feature, value: String): Future[Boolean] =
    collection
      .find(Filters.and(
        Filters.equal("service", service.value),
        Filters.equal("feature", feature.value),
        Filters.equal("hashedValue", oneWayHash(value))
      ))
      .toFuture()
      .map(_.nonEmpty)

  def count(service: Service, feature: Feature): Future[Long] =
    collection.countDocuments(Filters.and(
      Filters.equal("service", service.value),
      Filters.equal("feature", feature.value)
    )).toFuture()
}

object AllowListRepository {
  val DuplicateErrorCode = 11000
}

