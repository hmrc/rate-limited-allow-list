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

package uk.gov.hmrc.ratelimitedallowlist.repositories

import java.time.DayOfWeek.MONDAY
import java.time.temporal.ChronoUnit.{DAYS, HOURS}
import java.time.temporal.TemporalAdjusters.previousOrSame
import java.time.{Clock, Instant, ZoneId}
import javax.inject.{Inject, Singleton}

@Singleton
class UkTime @Inject()(clock: Clock):
  // This time zone will be either UTC+0 or UTC+1, depending on British Summer Time
  private final val timezone = ZoneId.of("Europe/London")
  private final val ukClock = clock.withZone(timezone)

  def now: Instant = ukClock.instant

  def thisHour: Instant = now.truncatedTo(HOURS)
  def nextHour: Instant = thisHour.plus(1, HOURS)
  def today: Instant = now.truncatedTo(DAYS)
  def tomorrow: Instant = now.truncatedTo(DAYS).plus(1, DAYS)
  def startOfWeek: Instant = now.atZone(timezone).`with`(previousOrSame(MONDAY)).toInstant.truncatedTo(DAYS)
  def startOfWorkingWeek: Instant = startOfWeek.plus(9, HOURS)
  def endOfWorkingWeek: Instant = startOfWeek.plus(4, DAYS).plus(17, HOURS)
  def nextWeek: Instant = startOfWeek.plus(7, DAYS)
