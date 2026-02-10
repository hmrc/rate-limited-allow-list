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

import com.google.inject.Singleton
import play.api.{Configuration, Logging}
import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListMetadataConfigUpdate, AllowListMetadataRepository, AllowListRepository}
import uk.gov.hmrc.ratelimitedallowlist.repositories.CreateResult.*

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import scala.util.control.NonFatal

@Singleton
class ConfigUpdatesService @Inject()(metadata: AllowListMetadataRepository,
                                     allowList: AllowListRepository,
                                     config: Configuration
                                    )(using ExecutionContext) extends Logging:

  private val allowTokenUpdate: Boolean = config.get[Boolean]("features.allow-config-token-updates")


  private def doUpdateAll(updates: Seq[AllowListMetadataConfigUpdate]): Future[Seq[Done]] =
    Future.sequence:
      updates.map:
        case AllowListMetadataConfigUpdate(service, feature, maxUsers) =>
          val userCountF = allowList.count(service, feature)
          val tokenCountF = metadata.get(service, feature).map(_.fold(0)(_.tokens))
          for
            userCount  <- userCountF
            tokenCount <- tokenCountF
            userInc    = maxUsers - (userCount + tokenCount)
            _          <- metadata.addTokens(service, feature, userInc)
          yield {
            logger.info(s"Update completed for $service and $feature")
            Done
          }

  private def doCreateAll(updates: Seq[AllowListMetadataConfigUpdate]): Future[Seq[Done]] =
    Future.sequence:
      updates.map:
        case AllowListMetadataConfigUpdate(service, feature, _) =>
          metadata
            .create(service, feature, canIssueTokens = true)
            .transform:
              case Success(CreateSuccessful) =>
                logger.info(s"Created record for $service and $feature")
                Success(Done)
              case Success(NoOpCreateResult) =>
                logger.info(s"Duplicate, skipping creation for $service and $feature.")
                Success(Done)
              case Failure(NonFatal(e)) =>
                logger.error(s"Could not create record for $service and $feature", e)
                Success(Done)
              case Failure(e) =>
                logger.error(s"Fatal exception encountered. Could not create record for $service and $feature", e)
                Success(Done)

  val initCompleted: Future[Done] =
    if !allowTokenUpdate then
      logger.info("Token driven config updates are disabled")
      Future.successful(Done)
    else
      logger.info("Token driven config updates are enabled")
      val updates = config.get[Seq[AllowListMetadataConfigUpdate]]("mongodb.collections.allow-list-metadata.token-updates")

      for
        _ <- doCreateAll(updates)
        _ <- doUpdateAll(updates)
      yield {
        logger.info("Config updates completed")
        Done
      }
