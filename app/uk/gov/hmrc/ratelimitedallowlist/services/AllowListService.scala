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

package uk.gov.hmrc.ratelimitedallowlist.services

import play.api.Configuration
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.AllowListRepository

import javax.inject.Inject
import scala.concurrent.Future

trait AllowListService:
  def check(serviceName: Service, listName: Feature, identifier: String): Future[Boolean]

class AllowListServiceImpl @Inject()(repository: AllowListRepository,
                                     configuration: Configuration) extends AllowListService:
 
  private val checkIsEnabled = configuration.get[Boolean]("features.allow-checks")
  
  override def check(service: Service, feature: Feature, identifier: String): Future[Boolean] = {
    if checkIsEnabled then
      repository.check(service, feature, identifier)
    else 
      Future.successful(false)
  }
