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
import play.api.libs.json.Reads.*
import play.api.libs.functional.syntax.*

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
 *  unbounded - no bounds
 */
type Timeframe = "hourly" | "daily" | "weekdaily" | "weekly" | "unbounded"

sealed trait TimeframeLimit(final val bound: Timeframe):
  def maxOpt: Option[Int]

case object Unbounded extends TimeframeLimit("unbounded"):
  override def maxOpt: Option[Int] = None

sealed abstract class TimeframeWithLimit(timeframe: Timeframe, val max: Int) extends TimeframeLimit(timeframe):
  override def maxOpt: Option[Int] = Some(max)

object TimeframeWithLimit:
  def unapply(timeframeLimit: TimeframeWithLimit): Option[(Timeframe, Int)] =
    Some(timeframeLimit.bound -> timeframeLimit.max)

final case class Hourly(value: Int) extends TimeframeWithLimit("hourly", value)
final case class Daily(value: Int) extends TimeframeWithLimit("daily", value)
final case class Weekdaily(value: Int) extends TimeframeWithLimit("weekdaily", value)
final case class Weekly(value: Int) extends TimeframeWithLimit("weekly", value)

object TimeframeLimit:
  def unapply(timeframeLimit: TimeframeLimit): Option[(Timeframe, Option[Int])] =
    Some(timeframeLimit.bound -> timeframeLimit.maxOpt)

  given reads: Reads[TimeframeLimit] =
    __.read[String].filter(_ == "unbounded").map(_ => Unbounded) or
      (__ \ "hourly").read[Int].map(Hourly.apply) or
      (__ \ "daily").read[Int].map(Daily.apply) or
      (__ \ "weekdaily").read[Int].map(Weekdaily.apply) or
      (__ \ "weekly").read[Int].map(Weekly.apply)

  given writes: Writes[TimeframeLimit] = {
    case tf: TimeframeWithLimit => Json.obj(tf.bound -> tf.max)
    case Unbounded => JsString("unbounded")
  }

  val format: Format[TimeframeLimit] = Format(reads, writes)
