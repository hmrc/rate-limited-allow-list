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
import org.mongodb.scala.model.*
import play.api.libs.json.OFormat
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.models.domain.AllowListMetadata.Field
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

  private val allowTokenUpdate: Boolean = config.get[Boolean]("features.allow-config-token-updates")

  val initCompleted = onInit()

  def create(service: Service, feature: Feature): Future[Done] =
    create(service, feature, false)
  
  private def create(service: Service, feature: Feature, canIssueTokens: Boolean) = {
    val entry = AllowListMetadata(service.value, feature.value, 0, canIssueTokens, clock.instant(), clock.instant(), "")
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
      
  def addTokens(service: Service, feature: Feature, incrementCount: Int): Future[UpdateResultResult] =
    if incrementCount > 0 then
      collection
        .updateOne(
          Filters.and(
            Filters.equal(Field.service, service.value),
            Filters.equal(Field.feature, feature.value)
          ),
          Updates.combine(
            Updates.inc(Field.tokens, incrementCount),
            Updates.set(Field.lastUpdated, clock.instant()),
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
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
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
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
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
 
  def setTokens(service: Service, feature: Feature, count: Int): Future[UpdateResultResult] =
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
          if (result.getModifiedCount == 0) then UpdateResultResult.NoOpUpdateResult
          else UpdateResultResult.UpdateSuccessful
      }


  private def updateByConfig(config: AllowListMetadataConfigUpdate) = {
    collection
      .updateOne(
        Filters.and(
          Filters.equal(Field.service, config.service),
          Filters.equal(Field.feature, config.feature),
          Filters.notEqual(Field.tokenConfigUpdateId, config.id)
        ),
        Updates.combine(
          Updates.set(Field.tokens, config.tokens),
          Updates.set(Field.lastUpdated, clock.instant()),
          Updates.set(Field.tokenConfigUpdateId, config.id)
        )
      )
      .toFuture()
      .map {
        result =>
          if (result.getMatchedCount == 0 || result.getModifiedCount == 0) then {
            logger.info("Did not update token count")
            Done
          } else {
            logger.info(s"Tokens updated for ${config.service} and ${config.service} to ${config.tokens} for id ${config.id}")
            Done
          }
      }
  }

  private def createIfMissing(updates: Seq[AllowListMetadataConfigUpdate]): Future[Seq[Done]] = {
    val services = updates.map(update => Filters.and(
      Filters.equal(Field.service, update.service),
      Filters.equal(Field.feature, update.feature)
    ))

    collection
      .find(Filters.or(services: _*))
      .toFuture()
      .flatMap { results =>
        val existing = results.map(metadata => (metadata.service, metadata.feature)).toSet
        val missing = updates.filterNot(update => existing.contains(update.service -> update.feature))
        Future.sequence(missing.map(x => create(Service(x.service), Feature(x.feature), canIssueTokens = true)))
      }
  }

  private def onInit(): Future[Done] = {
    if !allowTokenUpdate then
      logger.info("Token driven config updates are disabled")
      Future.successful(Done)
    else {
      logger.info("Token driven config updates are enabled")
      val updates = config.get[Seq[AllowListMetadataConfigUpdate]]("mongodb.collections.allow-list-metadata.token-updates")
      for
        _ <- createIfMissing(updates)
        _ <- Future.sequence(updates.map(updateByConfig))
      yield Done
    }
  }
}
