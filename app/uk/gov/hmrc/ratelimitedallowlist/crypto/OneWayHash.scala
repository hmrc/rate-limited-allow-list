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

package uk.gov.hmrc.ratelimitedallowlist.crypto

import play.api.Configuration
import uk.gov.hmrc.crypto.{OnewayCryptoFactory, PlainText}

import javax.inject.{Inject, Provider, Singleton}

trait OneWayHash extends (String => String)

class ShaOneWayHash(hashKey: String) extends OneWayHash:
  override def apply(v1: String): String =
    OnewayCryptoFactory
      .sha(hashKey)
      .hash(PlainText(v1))
      .value
 
@Singleton
class OneWayHashProvider @Inject()(configuration: Configuration) extends Provider[ShaOneWayHash]:
  override def get(): ShaOneWayHash =
    val hashKey = configuration.get[String]("crypto.sha.hashKey")
    ShaOneWayHash(hashKey)
