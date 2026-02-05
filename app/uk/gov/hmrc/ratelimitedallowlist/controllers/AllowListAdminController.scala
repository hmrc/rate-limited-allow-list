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

package uk.gov.hmrc.ratelimitedallowlist.controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.ratelimitedallowlist.models.{TokenIncrementRequest, UpdateRequest}
import uk.gov.hmrc.ratelimitedallowlist.models.UpdateRequest.{StartIssuingTokens, StopIssuingTokens, UpdateTokens}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.AllowListMetadataRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class AllowListAdminController @Inject()(
  cc: ControllerComponents,
  metadata: AllowListMetadataRepository
)(using ExecutionContext) extends BackendController(cc), Logging:

  def get(service: Service, feature: Feature): Action[AnyContent] =
    Action.async:
      metadata.get(service, feature).map:
        case Some(value) => Ok(Json.toJsObject(value))
        case None        => NotFound

  def patch(service: Service, feature: Feature): Action[UpdateRequest] =
    Action.async(parse.json[UpdateRequest]):
      request =>
        (request.body match
          case UpdateTokens(tokens) => metadata.setTokens(service, feature, tokens)
          case StartIssuingTokens   => metadata.startIssuingTokens(service, feature)
          case StopIssuingTokens    => metadata.stopIssuingTokens(service, feature)
        ).map:
          case UpdateSuccessful => NoContent
          case NoOpUpdateResult => NotFound

  def addTokens(service: Service, feature: Feature): Action[TokenIncrementRequest] =
    Action.async(parse.json[TokenIncrementRequest]):
      request =>
        metadata.addTokens(service, feature, request.body.value).map:
          case UpdateSuccessful => NoContent
          case NoOpUpdateResult => NotFound
