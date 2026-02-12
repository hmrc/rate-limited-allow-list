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
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonInt64}
import org.mongodb.scala.model.{Updates, *}
import play.api.libs.json.OFormat
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.models.domain.AllowListMetadata.Field
import uk.gov.hmrc.ratelimitedallowlist.models.domain.*

import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

trait AllowListMetadataRepository {
  def create(service: Service, feature: Feature): Future[CreateResult]
  def create(service: Service, feature: Feature, canIssueTokens: Boolean): Future[CreateResult]
  def get(service: Service): Future[Seq[AllowListMetadata]]
  def get(service: Service, feature: Feature): Future[Option[AllowListMetadata]]
  def clear(service: Service, feature: Feature): Future[DeleteResult]
  def addTokens(service: Service, feature: Feature, incrementCount: Long): Future[UpdateResultResult]
  def stopIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult]
  def startIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult]
  def issueToken(service: Service, feature: Feature): Future[UpdateResultResult]
  def setTokens(service: Service, feature: Feature, count: Long): Future[UpdateResultResult]
}
  

@Singleton
class AllowListMetadataRepositoryImpl @Inject()(
    mongoComponent: MongoComponent,
    config: Configuration,
    clock: Clock
)(using ExecutionContext) extends PlayMongoRepository[AllowListMetadata](
    collectionName = "allow-list-metadata",
    mongoComponent = mongoComponent,
    domainFormat = AllowListMetadata.format,
    replaceIndexes = config.get[Boolean]("mongodb.collections.allow-list-metadata.replaceIndexes"),
    indexes = Seq(
      IndexModel(
        Indexes.ascending(Field.created),
        IndexOptions()
          .name(s"${Field.created}-idx")
          .expireAfter(config.get[Long]("mongodb.collections.allow-list-metadata.ttlInDays"), TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending(Field.service, Field.feature),
        IndexOptions()
          .name(s"${Field.service}-${Field.feature}-idx")
          .unique(true)
      )
    )
  ) with AllowListMetadataRepository with Logging {

  def create(service: Service, feature: Feature): Future[CreateResult] = create(service, feature, false)

  def create(service: Service, feature: Feature, canIssueTokens: Boolean): Future[CreateResult] = {
    val entry = AllowListMetadata(service.value, feature.value, 0, canIssueTokens, clock.instant(), clock.instant())
    collection
      .insertOne(entry)
      .toFuture()
      .map(_ => CreateResult.CreateSuccessful)
      .recover {
        case e: MongoException if e.getCode == DuplicateErrorCode => CreateResult.NoOpCreateResult
      }
    
  }

  def get(service: Service): Future[Seq[AllowListMetadata]] =
    collection
      .find(Filters.equal(Field.service, service.value))
      .toFuture()
 
  def get(service: Service, feature: Feature): Future[Option[AllowListMetadata]] =
    collection.find(
      Filters.and(
        Filters.equal(Field.service, service.value),
        Filters.equal(Field.feature, feature.value)
      )
    ).toFuture()
      .map(_.headOption)
 
  def clear(service: Service, feature: Feature): Future[DeleteResult] =
    collection
      .deleteMany(
        Filters.and(
          Filters.equal(Field.service, service.value),
          Filters.equal(Field.feature, feature.value)
        )
      ).toFuture()
      .map {
        res =>
          if (res.getDeletedCount > 0) then DeleteResult.DeleteSuccessful
          else DeleteResult.NoOpDeleteResult
      }
      
  def addTokens(service: Service, feature: Feature, incrementCount: Long): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal(Field.service, service.value),
          Filters.equal(Field.feature, feature.value)
        ),
        List(
          Updates.set(Field.lastUpdated, clock.instant()),
          BsonDocument(
            "$set" -> BsonDocument(
              Field.tokens -> BsonDocument(
                "$max" -> BsonArray(
                  BsonInt64(0),
                  BsonDocument(
                    "$add" -> BsonArray(
                      BsonDocument("$ifNull" -> BsonArray("$"+Field.tokens, BsonInt64(0))),
                      BsonInt64(incrementCount)
                    )
                  )
                )
              )
            )
          )
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then
            logger.info(s"Could not add tokens as no matching record for $service and $feature was found.")
            UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }

  def stopIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal(Field.service, service.value),
          Filters.equal(Field.feature, feature.value)
        ),
        Updates.combine(
          Updates.set(Field.canIssueTokens, false),
          Updates.set(Field.lastUpdated, clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then
            logger.info(s"Could stop issuing tokens as no matching record for $service and $feature was found.")
            UpdateResultResult.NoOpUpdateResult
          else
            UpdateResultResult.UpdateSuccessful
      }

  def startIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal(Field.service, service.value),
          Filters.equal(Field.feature, feature.value)
        ),
        Updates.combine(
          Updates.set(Field.canIssueTokens, true),
          Updates.set(Field.lastUpdated, clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then
            logger.info(s"Could start issuing tokens as no matching record for $service and $feature was found.")
            UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }
 
  def issueToken(service: Service, feature: Feature): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal(Field.service, service.value),
          Filters.equal(Field.feature, feature.value),
          Filters.equal(Field.canIssueTokens, true),
          Filters.gte(Field.tokens, 1),
        ),
        Updates.combine(
          Updates.inc(Field.tokens, -1),
          Updates.set(Field.lastUpdated, clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }
 
  def setTokens(service: Service, feature: Feature, count: Long): Future[UpdateResultResult] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal(Field.service, service.value),
          Filters.equal(Field.feature, feature.value)
        ),
        Updates.combine(
          Updates.set(Field.tokens, count),
          Updates.set(Field.lastUpdated, clock.instant()),
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getModifiedCount == 0) then
            logger.info(s"Could not set tokens as no matching record for $service and $feature was found.")
            UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }
}
