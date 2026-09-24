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
import uk.gov.hmrc.ratelimitedallowlist.models.domain.*
import uk.gov.hmrc.ratelimitedallowlist.models.domain.CheckResult.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListConfigurationRepository, AllowListRepository}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

trait RateLimitedAllowListService:
  def checkOrAdd(allowList: AllowList, identifier: String): Future[CheckResult]

class RateLimitedAllowListServiceImpl @Inject()(allowListConfiguration: AllowListConfigurationRepository,
                                                allowedUsers: AllowListRepository,
                                                configuration: Configuration)(using ExecutionContext)
  extends RateLimitedAllowListService, Logging:

  private val checkIsDisabled = !configuration.get[Boolean]("features.allow-checks")
  private val excludeUser: Future[CheckResult] = Future.successful(Excluded)

  override def checkOrAdd(allowList: AllowList, user: String): Future[CheckResult] =
    ifUserAlreadyExists(allowList, user)(
      allowListConfiguration.get(allowList).flatMap:
        case None => excludeUser
        case Some(allowListConfig) =>
          val isAccepted = allowListConfig.checkUserLoadBalance
          handleUser(allowListConfig, user, isAccepted)
    )

  private def ifUserAlreadyExists(allowList: AllowList, user: String)(f: => Future[CheckResult]) =
    if checkIsDisabled then excludeUser
    else
      allowedUsers.checkExists(allowList.service, allowList.feature, user)
        .flatMap:
          case true => Future.successful(Exists)
          case false => f

  private def handleUser(config: AllowListConfiguration, user: String, accepted: Boolean): Future[CheckResult] =
    if !accepted then excludeUser else
      allowListConfiguration.updateAllowListPercentageCounters(config.asAllowList, accepted)
      allowedUsers.countWithinTimeframe(config)
        .flatMap:
          (withinTimeframe, total) =>
            if
              total < config.userLimit &&
              withinTimeframe < config.userLimitPerTimeframe
            then
              allowedUsers.set(Service(config.service), Feature(config.feature), user)
                .map(_ => Added)
            else
              excludeUser
