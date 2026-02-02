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
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import org.mongodb.scala.model.*
import play.api.Logging
import play.api.libs.json.OFormat
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.models.domain.*

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AllowListMetadataRepository {
  def create(service: Service, feature: Feature): Future[Done]
  def get(service: Service, feature: Feature): Future[Option[AllowListMetadata]]
  def clear(service: Service, feature: Feature): Future[DeleteResult]
  def addTokens(service: Service, feature: Feature, incrementCount: Int): Future[UpdateResultResult]
  def stopIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult]
  def startIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult]
  def issueToken(service: Service, feature: Feature): Future[UpdateResultResult]
  def setTokens(service: Service, feature: Feature, count: Int): Future[UpdateResultResult]
}
  

@Singleton
class AllowListMetadataRepositoryImpl @Inject()(
  mongoComponent: MongoComponent,
  config: AllowListRepositoryConfig,
  clock: Clock
)(using ExecutionContext) extends PlayMongoRepository[AllowListMetadata](
    collectionName = "allow-list-metadata",
    mongoComponent = mongoComponent,
    domainFormat = AllowListMetadata.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("created"),
        IndexOptions()
          .name("createdIdx")
          .expireAfter(config.allowListTtlInDays, TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending("service", "feature"),
        IndexOptions()
          .name("serviceListHashedValueIdx")
          .unique(true)
      )
    )
  ) with AllowListMetadataRepository with Logging {
  
  def create(service: Service, feature: Feature): Future[Done] = {
    val entry = AllowListMetadata(service.value, feature.value, 0, false, clock.instant(), clock.instant())
    collection
      .insertOne(entry)
      .toFuture()
      .map(_ => Done)
      .recover {
        case e: MongoException if e.getCode == DuplicateErrorCode => Done
      }
  }
  
  def get(service: Service, feature: Feature): Future[Option[AllowListMetadata]] =
    collection.find(
      Filters.and(
        Filters.equal("service", service.value),
        Filters.equal("feature", feature.value)
      )
    ).toFuture()
      .map(_.headOption)

  def clear(service: Service, feature: Feature): Future[DeleteResult] =
    collection
      .deleteMany(
        Filters.and(
          Filters.equal("service", service.value),
          Filters.equal("feature", feature.value)
        )
      ).toFuture()
      .map {
        res =>
          if (res.getDeletedCount > 0) then DeleteResult.DeleteSuccessful
          else DeleteResult.NoOpDeleteResult
      }
      
  def addTokens(service: Service, feature: Feature, incrementCount: Int): Future[UpdateResultResult] =
    if incrementCount > 0 then
      collection
        .updateOne(
          Filters.and(
            Filters.equal("service", service.value),
            Filters.equal("feature", feature.value)
          ),
          Updates.combine(
            Updates.inc("tokenCount", incrementCount),
            Updates.set("lastUpdated", clock.instant()),
          )
        )
        .toFuture()
        .map {
          result =>
            if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
            else UpdateResultResult.UpdateSuccessful
        }
    else
      logger.info(s"Invalid parameter for tokens, got $incrementCount")
      Future.failed(IllegalArgumentException(
        s"Invalid argument for incrementCount. Expected  incrementCount >= 1 got $incrementCount"
      ))

  def stopIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal("service", service.value),
          Filters.equal("feature", feature.value)
        ),
        Updates.combine(
          Updates.set("canIssueTokens", false),
          Updates.set("lastUpdated", clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }

  def startIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal("service", service.value),
          Filters.equal("feature", feature.value)
        ),
        Updates.combine(
          Updates.set("canIssueTokens", true),
          Updates.set("lastUpdated", clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }
 
  def issueToken(service: Service, feature: Feature): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal("service", service.value),
          Filters.equal("feature", feature.value),
          Filters.equal("canIssueTokens", true),
          Filters.gte("tokenCount", 1),
        ),
        Updates.combine(
          Updates.inc("tokenCount", -1),
          Updates.set("lastUpdated", clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }
 
  def setTokens(service: Service, feature: Feature, count: Int): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal("service", service.value),
          Filters.equal("feature", feature.value)
        ),
        Updates.combine(
          Updates.set("tokenCount", count),
          Updates.set("lastUpdated", clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }
}
