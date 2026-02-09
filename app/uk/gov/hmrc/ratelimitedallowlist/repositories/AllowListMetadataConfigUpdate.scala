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

import play.api.{ConfigLoader, Configuration}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}

case class AllowListMetadataConfigUpdate(service: Service, feature: Feature, maxUsers: Int)

object AllowListMetadataConfigUpdate:
  import ConfigLoader.*

  given ConfigLoader[AllowListMetadataConfigUpdate] =
    ConfigLoader.apply:
      config => rawPrefix =>
        val prefix = if rawPrefix.nonEmpty then s"$rawPrefix." else rawPrefix
        val maxUsers = config.getInt(s"${prefix}maxUsers")

        assert(maxUsers >= 0, "Invalid value for maxUsers, must be >0.")

        AllowListMetadataConfigUpdate(
          Service(config.getString(s"${prefix}service")),
          Feature(config.getString(s"${prefix}feature")),
          maxUsers,
        )

  given ConfigLoader[Seq[AllowListMetadataConfigUpdate]] =
    ConfigLoader.apply:
      config => prefix =>
        Configuration(config)
          .get[Seq[Configuration]](prefix)
          .map(_.get[AllowListMetadataConfigUpdate](""))
