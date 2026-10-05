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

package uk.gov.hmrc.ratelimitedallowlist.models.domain

import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.ratelimitedallowlist.models.CreateAllowListConfigurationRequest

import java.time.Instant
import scala.language.implicitConversions

case class AllowListConfiguration(service: String,
                                  feature: String,
                                  isEnabled: Boolean,
                                  userLimitPerTimeframe: Int,
                                  timeframe: String,
                                  userLimit: Option[Int] = None,
                                  percentageLoad: Int,
                                  created: Instant,
                                  lastUpdated: Instant,
                                  private val acceptedCounter: Int,
                                  private val totalCounter: Int):

  require(100 >= percentageLoad && percentageLoad >= 0, s"Invalid percentage for $service/$feature: $percentageLoad")

  val serviceFeature = s"$service-$feature"
  lazy val asAllowList = AllowList(Service(service), Feature(feature))

  def checkUserLoadBalance: Boolean =
    if percentageLoad == 0 then false
    else if totalCounter == 0 then true
    else (acceptedCounter.toDouble / totalCounter * 100) < percentageLoad

  def matchesUpdate(update: AllowListConfiguration.Update): Boolean =
    update.userLimitPerTimeframe.forall(_ == userLimitPerTimeframe) &&
      update.timeframe.forall(_ == timeframe) &&
      update.userLimit.flatMap { update => userLimit.map(_ == update) }.getOrElse(true) &&
      update.percentageLoad.forall(_ == percentageLoad)


object AllowListConfiguration extends MongoJavatimeFormats.Implicits:
  given format: OFormat[AllowListConfiguration] = Json.format[AllowListConfiguration]

  def fromRequest(service: Service, request: CreateAllowListConfigurationRequest, instant: Instant) = AllowListConfiguration(
    service = service.value,
    feature = request.feature,
    isEnabled = false,
    userLimitPerTimeframe = request.userLimitPerTimeframe,
    timeframe = request.timeframe.bound,
    userLimit = request.userLimit,
    percentageLoad = request.percentageLoad,
    created = instant,
    lastUpdated = instant,
    acceptedCounter = 0,
    totalCounter = 0
  )

  case class Update private (userLimitPerTimeframe: Option[Int],
                             timeframe: Option[String],
                             userLimit: Option[Int],
                             percentageLoad: Option[Int],
                             isEnabled: Option[Boolean]):
    val isValid: Boolean =
      percentageLoad.forall(load => 100 >= load && load >= 0) &&
        userLimit.forall(_ >= 0) &&
        userLimitPerTimeframe.forall(_ >= 0)

    def nonEmpty: Boolean = !isEmpty

    def isEmpty: Boolean =
      this match
        case Update(None, None, None, None, None) => true
        case _ => false

    require(
      isValid,
      s"""Invalid request to update configuration
          |  userLimitPerTimeframe=$userLimitPerTimeframe
          |  timeframe=$timeframe
          |  userLimit=$userLimit
          |  percentageLoad=$percentageLoad
          |  isEnabled=$isEnabled""".stripMargin
    )

  object Update:
    given patchFormat: OFormat[Update] = Json.format

    def apply(userLimitPerTimeframe: Option[Int],
              timeframe: Option[String],
              userLimit: Option[Int],
              percentageLoad: Option[Int],
              isEnabled: Option[Boolean]): Update =
      val update = new Update(userLimitPerTimeframe, timeframe, userLimit, percentageLoad, isEnabled)
      if (update.isValid) update
      else throw RuntimeException(s"Invalid request to update configuration: $update")
