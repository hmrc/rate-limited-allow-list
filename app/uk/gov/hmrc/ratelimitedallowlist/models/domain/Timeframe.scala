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
enum Timeframe(final val bound: String):
  case Hourly    extends Timeframe("hourly")
  case Daily     extends Timeframe("daily")
  case Weekdaily extends Timeframe("weekdaily")
  case Weekly    extends Timeframe("weekly")
  case Unbounded extends Timeframe("unbounded")

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
