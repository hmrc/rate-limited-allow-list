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

import org.bson.conversions.Bson
import org.mongodb.scala.model.Updates
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant
import scala.language.implicitConversions

case class AllowListConfiguration(service: String,
                                  feature: String,
                                  isEnabled: Boolean,
                                  limits: AllowListLimits,
                                  percentageLoad: Int,
                                  private val acceptedCounter: Int,
                                  private val totalCounter: Int,
                                  private val created: Instant,
                                  private val lastUpdated: Instant):

  require(100 >= percentageLoad && percentageLoad >= 0, s"Invalid percentage for $service/$feature: $percentageLoad")

  val serviceFeature = s"$service-$feature"
  lazy val asAllowList = AllowList(Service(service), Feature(feature))

  def checkUserLoadBalance: Boolean =
    if percentageLoad == 0 then false
    else if totalCounter == 0 then true
    else (acceptedCounter.toDouble / totalCounter * 100) < percentageLoad

  def matchesUpdate(update: AllowListConfiguration.Update): Boolean =
    AllowListConfiguration.matchesUpdate(this, update)


object AllowListConfiguration:
  object Givens:
    object Mongo extends MongoJavatimeFormats.Implicits:
      given format: OFormat[AllowListConfiguration] = Json.format[AllowListConfiguration]
    object Http:
      given format: OFormat[AllowListConfiguration] = Json.format[AllowListConfiguration]

  def fromRequest(service: Service, request: Create, instant: Instant): AllowListConfiguration = {
    val initialTimeframe = request.timeframe.getOrElse(Unbounded)
    val limits = AllowListLimits(max = request.userLimit, timeframe = initialTimeframe)
    AllowListConfiguration(
      service = service.value,
      feature = request.feature,
      isEnabled = false,
      limits = limits,
      percentageLoad = request.percentageLoad,
      acceptedCounter = 0,
      totalCounter = 0,
      created = instant,
      lastUpdated = instant
    )
  }

  def matchesUpdate(config: AllowListConfiguration, update: AllowListConfiguration.Update): Boolean =
      update.limits.forall(_.timeframe.forall(_ == config.limits.timeframe)) &&
      update.limits.forall(_.max.forall(config.limits.max.contains)) &&
      update.percentageLoad.forall(_ == config.percentageLoad) &&
      update.isEnabled.forall(_ == config.isEnabled)

  case class Create(feature: String,
                    percentageLoad: Int,
                    timeframe: Option[TimeframeLimit],
                    userLimit: Option[Int])

  object Create:
    given OFormat[Create] = Json.format

  case class Update private (limits: Option[AllowListLimits.Update],
                             percentageLoad: Option[Int],
                             isEnabled: Option[Boolean]):

    val isValid: Boolean =
      limits.forall(_.isValid) &&
      percentageLoad.forall(load => 100 >= load && load >= 0)

    def nonEmpty: Boolean = !isEmpty

    def isEmpty: Boolean =
      this match
        case Update(None, None, None) => true
        case Update(Some(limits), None, None) => limits.isEmpty
        case _ => false

    require(
      isValid,
      s"""Invalid request to update configuration
          |  limits=$limits
          |  percentageLoad=$percentageLoad
          |  isEnabled=$isEnabled""".stripMargin
    )

  object Update extends Codecs:
    given Reads[Update] = Json.reads

    def apply(timeframeLimit: Option[TimeframeLimit],
              userLimit: Option[Int],
              percentageLoad: Option[Int],
              isEnabled: Option[Boolean]): Update =
      val allowListLimitUpdate = Option.when(userLimit.isDefined || timeframeLimit.isDefined)(
        AllowListLimits.Update(userLimit, timeframeLimit)
      )
      val update = new Update(allowListLimitUpdate, percentageLoad, isEnabled)
      if (update.isValid) update
      else throw RuntimeException(s"Invalid request to update configuration: $update")

    def toBson(u: Update): Seq[Bson] = (u match {
      case Update(limits, percentageLoad, isEnabled) =>
        limits.fold(List.empty[Option[Bson]]) {
          case AllowListLimits.Update(max, tf) =>
            List(
              max.map(Updates.set("limits.max", _)),
              tf.map(Updates.set("limits.timeframe", _)),
            )
        }
        .appended(percentageLoad.map(Updates.set("percentageLoad", _)))
        .appended(isEnabled.map(Updates.set("isEnabled", _)))
    }).flatten