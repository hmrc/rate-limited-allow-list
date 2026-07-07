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

import java.time.Instant


case class AllowListConfiguration(service: String,
                                  feature: String,
                                  userLimitPerTimeframe: Int,
                                  timeframe: Timeframe,
                                  userLimit: Int,
                                  percentageLoad: Int,
                                  private val acceptedCounter: Int = 0,
                                  private val totalCounter: Int = 0,
                                  private val created: Instant):
  require(100 >= percentageLoad && percentageLoad >= 0, s"Invalid percentage for $service/$feature: $percentageLoad")

  val serviceFeature = s"$service-$feature"
  lazy val asAllowList = AllowList(Service(service), Feature(feature))

  def checkUserLoadBalance: Boolean = AllowListConfiguration.percentageCheck(this)


object AllowListConfiguration extends MongoJavatimeFormats.Implicits:
  given Format[Timeframe] = Timeframe.format
  given format: Format[AllowListConfiguration] = Json.format[AllowListConfiguration]

  private def percentageCheck(config: AllowListConfiguration): Boolean =
    if config.percentageLoad == 0 then false
    else if config.totalCounter == 0 then true
    else (config.acceptedCounter.toDouble / config.totalCounter * 100) < config.percentageLoad


enum Timeframe:
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
  case hourly, daily, weekdaily, weekly, unbounded

object Timeframe:
  given format: Format[Timeframe] = Format(
    Reads {
      case JsString(t) => JsSuccess(Timeframe.valueOf(t))
      case _ => JsError("Timeframe value unrecognised")
    },
    Writes {
      t => JsString(t.toString)
    }
  )