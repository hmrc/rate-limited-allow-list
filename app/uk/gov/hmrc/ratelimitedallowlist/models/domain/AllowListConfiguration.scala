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

import java.time.{Clock, Instant}
import scala.language.implicitConversions

case class AllowListConfiguration(service: String,
                                  feature: String,
                                  userLimitPerTimeframe: Int,
                                  timeframe: String,
                                  userLimit: Int,
                                  percentageLoad: Int,
                                  private val acceptedCounter: Int = 0,
                                  private val totalCounter: Int = 0,
                                  private val created: Instant):

  require(100 >= percentageLoad && percentageLoad >= 0, s"Invalid percentage for $service/$feature: $percentageLoad")

  val serviceFeature = s"$service-$feature"
  lazy val asAllowList = AllowList(Service(service), Feature(feature))

  def checkUserLoadBalance: Boolean = AllowListConfiguration.percentageCheck(this)
  
  val matchesUpdate: AllowListConfiguration.Update => Boolean = AllowListConfiguration.matchesUpdate(this)


object AllowListConfiguration extends MongoJavatimeFormats.Implicits:
  given format: OFormat[AllowListConfiguration] = Json.format[AllowListConfiguration]

  private def percentageCheck(config: AllowListConfiguration): Boolean =
    if config.percentageLoad == 0 then false
    else if config.totalCounter == 0 then true
    else (config.acceptedCounter.toDouble / config.totalCounter * 100) < config.percentageLoad

  def fromRequest(service: Service, request: CreateAllowListConfigurationRequest)(using clock: Clock) = AllowListConfiguration(
    service.value,
    request.feature,
    request.userLimitPerTimeframe,
    request.timeframe.bound,
    request.userLimit,
    request.percentageLoad,
    0,
    0,
    clock.instant()
  )

  private case class ConfigPatch(userLimitPerTimeframe: Option[Int],
                                 timeframe: Option[String],
                                 userLimit: Option[Int],
                                 percentageLoad: Option[Int]):
    val isValid: Boolean =
      percentageLoad.forall(load => 100 >= load && load >= 0) &&
        userLimit.forall(_ >= 0) &&
        userLimitPerTimeframe.forall(_ >= 0)
    require(isValid)

  private def matchesUpdate(config: AllowListConfiguration)(update: Update): Boolean =
    update.userLimitPerTimeframe.forall(_ == config.userLimitPerTimeframe) &&
    update.timeframe.forall(_ == config.timeframe) &&
    update.userLimit.forall(_ == config.userLimit) &&
    update.percentageLoad.forall(_ == config.percentageLoad)

  opaque type Update = ConfigPatch
  object Update:
    given patchFormat: OFormat[Update] = Json.format[ConfigPatch]

    def apply(userLimitPerTimeframe: Option[Int],
      timeframe: Option[String],
      userLimit: Option[Int],
      percentageLoad: Option[Int]): Update =
      val configUpdate = ConfigPatch(userLimitPerTimeframe, timeframe, userLimit, percentageLoad)
      if (configUpdate.isValid) configUpdate
      else throw RuntimeException(s"Invalid request to update configuration: $ConfigPatch")

  extension (u: Update)
    def userLimitPerTimeframe: Option[Int] = u.userLimitPerTimeframe
    def timeframe: Option[String] = u.timeframe
    def userLimit: Option[Int] = u.userLimit
    def percentageLoad: Option[Int] = u.percentageLoad
