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

case class AllowListMetadataConfigUpdate(service: String, feature: String, tokens: Int, id: String)

object AllowListMetadataConfigUpdate:
  import ConfigLoader.*

  given ConfigLoader[AllowListMetadataConfigUpdate] =
    ConfigLoader.apply:
      config => rawPrefix =>
        val prefix = if rawPrefix.nonEmpty then s"$rawPrefix." else rawPrefix
        AllowListMetadataConfigUpdate(
          config.getString(s"${prefix}service"),
          config.getString(s"${prefix}feature"),
          config.getInt(s"${prefix}tokens"),
          config.getString(s"${prefix}id")
        )

  given ConfigLoader[Seq[AllowListMetadataConfigUpdate]] =
    ConfigLoader.apply:
      config => prefix =>
        Configuration(config)
          .get[Seq[Configuration]](prefix)
          .map(_.get[AllowListMetadataConfigUpdate](""))
