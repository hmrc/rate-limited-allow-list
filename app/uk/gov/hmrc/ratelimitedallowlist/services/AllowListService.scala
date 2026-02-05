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

package uk.gov.hmrc.ratelimitedallowlist.services

import play.api.{Configuration, Logging}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.CheckResult.*
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{CheckResult, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListMetadataRepository, AllowListRepository}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

trait AllowListService:
  def checkOrAdd(service: Service, feature: Feature, identifier: String): Future[CheckResult]

class AllowListServiceImpl @Inject()(metadata: AllowListMetadataRepository,
                                     allowList: AllowListRepository,
                                     configuration: Configuration
                                    )(using ExecutionContext) extends AllowListService, Logging:

  private val checkIsEnabled = configuration.get[Boolean]("features.allow-checks")

  override def checkOrAdd(service: Service, feature: Feature, identifier: String): Future[CheckResult] =
    if checkIsEnabled then
      allowList.checkExist(service, feature, identifier).flatMap:
        case true =>
          logger.debug("Exists")
          Future.successful(Exists)
        case false =>
          metadata.issueToken(service, feature).flatMap:
            case NoOpUpdateResult => 
              logger.debug("Not found, cannot be added")
              Future.successful(Excluded)
            case UpdateSuccessful =>
              allowList.set(service, feature, identifier)
                .map:
                  _ =>
                    logger.debug("Added")
                    Added
    else
      Future.successful(Excluded)
