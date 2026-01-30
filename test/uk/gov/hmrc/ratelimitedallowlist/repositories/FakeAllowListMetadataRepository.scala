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
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}

import scala.concurrent.Future

class FakeAllowListMetadataRepository(createResult: Option[Done] = None,
                                      getResult: Option[Option[AllowListMetadata]] = None,
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

  def create(service: Service, feature: Feature): Future[Done] =
    Future.successful(createResult.getOrElse(throwNotImplemented("set")))

  def get(service: Service, feature: Feature): Future[Option[AllowListMetadata]] =
    Future.successful(getResult.getOrElse(throwNotImplemented("get")))

  def clear(service: Service, feature: Feature): Future[DeleteResult] =
    Future.successful(clearResult.getOrElse(throwNotImplemented("clear")))

  def addTokens(service: Service, feature: Feature, incrementCount: Int): Future[UpdateResultResult] =
    Future.successful(addTokensResult.getOrElse(throwNotImplemented("addTokens")))
    
  def stopIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    Future.successful(stopIssuingTokensResult.getOrElse(throwNotImplemented("stopIssuingTokens")))
    
  def startIssuingTokens(service: Service, feature: Feature): Future[UpdateResultResult] =
    Future.successful(startIssuingTokensResult.getOrElse(throwNotImplemented("startIssuingTokens")))
    
  def issueToken(service: Service, feature: Feature): Future[UpdateResultResult] =
    Future.successful(issueTokenResult.getOrElse(throwNotImplemented("issueToken")))
    
  def setTokens(service: Service, feature: Feature, count: Int): Future[UpdateResultResult] =
    Future.successful(setTokensResult.getOrElse(throwNotImplemented("setTokens")))
    

}
