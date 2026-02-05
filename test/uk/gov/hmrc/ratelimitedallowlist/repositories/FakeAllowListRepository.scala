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

import uk.gov.hmrc.ratelimitedallowlist.models.Done
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}

import scala.concurrent.Future

class FakeAllowListRepository(setResult: Option[Done] = None,
                              removeResult: Option[DeleteResult] = None,
                              clearResult: Option[Done] = None,
                              checkResult: Option[Boolean] = None,
                              countResult: Option[Long] = None) extends AllowListRepository {

  def throwNotImplemented(method: String) =
    throw new NotImplementedError(
      s"""FakeAllowListRepository return result not configured for method `$method`. To resolve the either: 
         |  (1) pass a value that should be returned by the implementation, or 
         |  (2) if this method should not have been invoked then check your implementation""".stripMargin
    )

  def set(service: Service, feature: Feature, value: String): Future[Done] =
    Future.successful(setResult.getOrElse(throwNotImplemented("set")))

  def remove(service: Service, feature: Feature, value: String): Future[DeleteResult] =
    Future.successful(removeResult.getOrElse(throwNotImplemented("remove")))

  def clear(service: Service, feature: Feature): Future[Done] =
    Future.successful(clearResult.getOrElse(throwNotImplemented("clear")))

  def checkExists(service: Service, feature: Feature, value: String): Future[Boolean] =
    Future.successful(checkResult.getOrElse(throwNotImplemented("check")))

  def count(service: Service, feature: Feature): Future[Long] =
    Future.successful(countResult.getOrElse(throwNotImplemented("count")))
}
