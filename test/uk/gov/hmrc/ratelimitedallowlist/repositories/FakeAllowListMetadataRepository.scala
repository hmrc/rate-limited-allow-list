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

import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}

import scala.annotation.unused
import scala.concurrent.Future

class FakeAllowListMetadataRepository(createResult: Option[CreateResult] = None,
                                      getResult: Option[Option[AllowListMetadata]] = None,
                                      getByServiceResult: Option[List[AllowListMetadata]] = None,
                                      clearResult: Option[DeleteResult] = None,
                                      addTokensResult: Option[UpdateResultResult] = None,
                                      stopIssuingTokensResult: Option[UpdateResultResult] = None,
                                      startIssuingTokensResult: Option[UpdateResultResult] = None,
                                      issueTokenResult: Option[UpdateResultResult] = None,
                                      setTokensResult: Option[UpdateResultResult] = None
                                     ) extends AllowListMetadataRepository {

  def throwNotImplemented(method: String) =
    throw new NotImplementedError(
      s"""FakeAllowListRepository return result not configured for method `$method`. To resolve the either: 
         |  (1) pass a value that should be returned by the implementation, or 
         |  (2) if this method should not have been invoked then check your implementation""".stripMargin
    )

  @unused
  override def create(service: Service, feature: Feature): Future[CreateResult] =
    Future.successful(createResult.getOrElse(throwNotImplemented("create")))

  @unused
  override def create(service: Service, feature: Feature, canIssueTokens: Boolean): Future[CreateResult] =
    Future.successful(createResult.getOrElse(throwNotImplemented("create")))

  @unused
  override def get(service: Service): Future[List[AllowListMetadata]] =
    Future.successful(getByServiceResult.getOrElse(throwNotImplemented("getByService")))

  @unused
  override def get(service: Service, feature: Feature): Future[Option[AllowListMetadata]] =
    Future.successful(getResult.getOrElse(throwNotImplemented("get")))

  @unused
  override def clear(service: Service, feature: Feature): Future[DeleteResult] =
    Future.successful(clearResult.getOrElse(throwNotImplemented("clear")))

  @unused
  override def addTokens(service: Service, feature: Feature, incrementCount: Long): Future[UpdateResultResult] =
    Future.successful(addTokensResult.getOrElse(throwNotImplemented("addTokens")))

  @unused
  override def stopIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    Future.successful(stopIssuingTokensResult.getOrElse(throwNotImplemented("stopIssuingTokens")))

  @unused
  override def startIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    Future.successful(startIssuingTokensResult.getOrElse(throwNotImplemented("startIssuingTokens")))

  @unused
  override def issueToken(service: Service, feature: Feature): Future[UpdateResultResult] =
    Future.successful(issueTokenResult.getOrElse(throwNotImplemented("issueToken")))

  @unused
  override def setTokens(service: Service, feature: Feature, count: Long): Future[UpdateResultResult] =
    Future.successful(setTokensResult.getOrElse(throwNotImplemented("setTokens")))
    

}
