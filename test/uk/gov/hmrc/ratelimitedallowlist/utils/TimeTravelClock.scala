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

package uk.gov.hmrc.ratelimitedallowlist.utils


import java.time.{Clock, Instant, ZoneId}

/**
 * Implements the [[java.time.Clock]] interface and allows for simulating time moving forward and backwards.
 * Note: This is not threadsafe. When using the same instance across multiple test, ensure that tests are run
 *       sequentially.
 * @param fixedInstant - the reference instant that all time travels are done in reference to.
 */
class TimeTravelClock(fixedInstant: Instant) extends Clock {
  private var modifiedTime: Int = 0

  override def getZone: ZoneId = Clock.systemDefaultZone().getZone

  /**
   * Changing time zone is not supported. This returns the clock without changing the time zone.
   */
  override def withZone(zone: ZoneId): Clock = this

  def fastForwardTime(seconds: Int): Unit = {
    if (seconds <= 0) {
      throw new IllegalArgumentException("Expected argument to fastForwardSeconds to be > 0 but got " + seconds)
    }
    modifiedTime = modifiedTime + seconds
  }

  def rewindTime(seconds: Int): Unit = {
    if (seconds <= 0) {
      throw new IllegalArgumentException("Expected argument to rewindSeconds to be > 0 but got " + seconds)
    }
    modifiedTime = modifiedTime - seconds
  }

  def resetTimeTravel(): Unit = {
    modifiedTime = 0
  }

  override def instant(): Instant = fixedInstant.plusSeconds(modifiedTime) 
 
}
