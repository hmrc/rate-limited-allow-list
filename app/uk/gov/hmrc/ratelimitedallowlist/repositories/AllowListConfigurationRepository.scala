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
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListConfiguration, Service, AllowList}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


trait AllowListConfigurationRepository:
  def create(configuration: AllowListConfiguration): Future[CreateResult]
  def getServices(filterBy: Seq[String]): Future[Seq[Service]]
  def get(service: Service): Future[Seq[AllowListConfiguration]]
  def get(allowList: AllowList): Future[Option[AllowListConfiguration]]
  def updateAllowListPercentageCounters(allowList: AllowList, accepted: Boolean): Future[AllowListConfiguration]
  def clear(allowList: AllowList): Future[DeleteResult]

@Singleton
class AllowListConfigurationRepositoryImpl @Inject()(
    mongoComponent: MongoComponent,
    config: Configuration
)(using ExecutionContext) extends PlayMongoRepository[AllowListConfiguration](
    collectionName = "allow-list-configuration",
    mongoComponent = mongoComponent,
    domainFormat = AllowListConfiguration.format,
    replaceIndexes = config.get[Boolean]("mongodb.collections.allow-list-configuration.replaceIndexes"),
    indexes = Seq(
      IndexModel(
        Indexes.ascending("created"),
        IndexOptions()
          .name("created-idx")
          .expireAfter(config.get[Long]("mongodb.collections.allow-list-configuration.ttlInDays"), TimeUnit.DAYS)
      ),
      IndexModel(
        Indexes.ascending("service", "feature"),
        IndexOptions()
          .name("service-feature-idx")
          .unique(true)
      ),
    ),
  ) with AllowListConfigurationRepository with Logging:

  override def create(configuration: AllowListConfiguration): Future[CreateResult] =
    collection
      .insertOne(configuration)
      .toFuture()
      .map:
        _ => CreateResult.CreateSuccessful
      .recover:
        case e: MongoException if e.getCode == DuplicateErrorCode => CreateResult.NoOpCreateResult


  override def getServices(filterBy: Seq[String] = List.empty): Future[Seq[Service]] =
    if filterBy.isEmpty then
      collection
        .distinct[String]("service")
        .toFuture()
        .map:
          _.map(Service.apply)
    else
      collection
        .distinct[String]("service", Filters.in("service", filterBy*))
        .toFuture()
        .map:
          _.map:
            Service.apply

  override def get(service: Service): Future[Seq[AllowListConfiguration]] =
    collection
      .find(Filters.equal("service", service.value))
      .toFuture()
 
  override def get(allowList: AllowList): Future[Option[AllowListConfiguration]] =
    collection.find(allowList.filters)
      .toFuture()
      .map:
        _.headOption

  override def updateAllowListPercentageCounters(allowList: AllowList, accepted: Boolean): Future[AllowListConfiguration] =
    collection.findOneAndUpdate(
        allowList.filters,
        Updates.combine(
          Updates.inc("acceptedCounter", if accepted then 1 else 0),
          Updates.inc("totalCounter", 1)
        )
      )
      .toFuture()


  override def clear(allowList: AllowList): Future[DeleteResult] =
    collection
      .deleteMany(allowList.filters)
      .toFuture()
      .map:
        res =>
          if res.getDeletedCount > 0 then DeleteResult.DeleteSuccessful
          else DeleteResult.NoOpDeleteResult
