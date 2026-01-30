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

package uk.gov.hmrc.ratelimitedallowlist.config

import play.api.inject.{Binding, Module as AppModule}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.ratelimitedallowlist.crypto.{OneWayHash, ShaOneWayHash, OneWayHashProvider}
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListRepository, AllowListRepositoryConfig, AllowListRepositoryConfigProvider, AllowListRepositoryImpl}
import uk.gov.hmrc.ratelimitedallowlist.services.{AllowListService, AllowListServiceImpl}
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListMetadataRepository, AllowListMetadataRepositoryImpl}

import java.time.Clock

class Module extends AppModule:
  override def bindings(
    environment: Environment,
    configuration: Configuration
  ): Seq[Binding[_]] =
    List(
      bind[Clock].toInstance(Clock.systemDefaultZone),
      bind[AllowListRepositoryConfig].toProvider[AllowListRepositoryConfigProvider],
      bind[ShaOneWayHash].toProvider[OneWayHashProvider],
      bind[OneWayHash].to[ShaOneWayHash],
      bind[AllowListRepository].to[AllowListRepositoryImpl],
      bind[AllowListService].to[AllowListServiceImpl],
      bind[AllowListMetadataRepository].to[AllowListMetadataRepositoryImpl]
    )