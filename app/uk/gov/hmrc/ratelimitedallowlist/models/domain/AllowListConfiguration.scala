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
  given Format[Timeframe] = Timeframe.format
  given format: OFormat[AllowListConfiguration] = Json.format[AllowListConfiguration]
  given patchFormat: OFormat[Update] = Json.format[ConfigPatch]

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


sealed trait Timeframe(final val bound: String)
  /** Timeframes for configuring user limits as "N users per T timeframe."
   *  e.g. 50 users per Hour, 250 users per day, 1000 users per Week.
   *
   *  All timeframes will move between UTC+0 (GMT) and UTC+1 (BST) to match
   *  UK daylight savings time, avoiding off-by-one errors if it is crucial
   *  that a limit does not begin/end on, for instance, the wrong calendar
   *  day.
   *
   *  hourly    - bounds the limit to the current hour, from HH:00 to HH:59
   *  daily     - bounds the limit to the current calendar day
   *  weekdaily - bounds the limit to the current working week
   *              excluding weekends, e.g. Mon-Fri
   *  weekly    - bounds the limit to the current week starting on Monday
   *              and ending on Sunday
   */
case object Hourly    extends Timeframe("hourly")
case object Daily     extends Timeframe("daily")
case object Weekdaily extends Timeframe("weekdaily")
case object Weekly    extends Timeframe("weekly")
case object Unbounded extends Timeframe("unbounded")

object Timeframe:
  given format: Format[Timeframe] = Format[Timeframe](
    Reads[Timeframe] {
      case JsString(Hourly.bound) => JsSuccess(Hourly)
      case JsString(Daily.bound) => JsSuccess(Daily)
      case JsString(Weekdaily.bound) => JsSuccess(Weekdaily)
      case JsString(Weekly.bound) => JsSuccess(Weekly)
      case JsString(Unbounded.bound) => JsSuccess(Unbounded)
      case other => JsError(s"Timeframe must be a known JsString value, found: $other")
    },
    Writes[Timeframe] { t =>
      JsString(t.bound)
    }
  )

case class CreateAllowListConfigurationRequest(feature: String,
                                               userLimitPerTimeframe: Int,
                                               timeframe: Timeframe,
                                               userLimit: Int,
                                               percentageLoad: Int)
object CreateAllowListConfigurationRequest:
  given Format[Timeframe] = Timeframe.format
  given format: OFormat[CreateAllowListConfigurationRequest] = Json.format[CreateAllowListConfigurationRequest]