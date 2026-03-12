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
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, IAAction, Resource}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.ratelimitedallowlist.models.{AllowListReportQueryParams, AllowListReportResponse, TokenIncrementRequest, UpdateRequest, CreateAllowListRequest}
import uk.gov.hmrc.ratelimitedallowlist.models.UpdateRequest.{StartIssuingTokens, StopIssuingTokens, UpdateTokens}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListMetadataRepository, AllowListRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton()
class AllowListAdminController @Inject()(
  cc: ControllerComponents,
  auth: BackendAuthComponents,
  metadata: AllowListMetadataRepository,
  allowList: AllowListRepository
)(using ExecutionContext) extends BackendController(cc), Logging {

  private def authorised(service: Service) =
    auth.authorizedAction(
      Permission(
        Resource.from("rate-limited-allow-list-admin-frontend", service.value),
        IAAction("ADMIN")
      )
    )

  def getFeatures(service: Service): Action[AnyContent] =
    authorised(service).async:
      metadata.get(service).map:
        case list if list.isEmpty => NotFound
        case list                 => Ok(Json.toJson(list))

  def get(service: Service, feature: Feature): Action[AnyContent] =
    authorised(service).async:
      metadata.get(service, feature).map:
        case Some(value) => Ok(Json.toJsObject(value))
        case None        => NotFound

  def getAllowListReport(service: Service,
                         feature: Feature,
                         queryParams: AllowListReportQueryParams): Action[AnyContent] =
    authorised(service).async:
      allowList.count(service, feature).map:
        count =>
          val response = AllowListReportResponse(service.value, feature.value, count, List.empty)
          Ok(Json.toJsObject(response))

  def patch(service: Service, feature: Feature): Action[UpdateRequest] =
    authorised(service).async(parse.json[UpdateRequest]):
      request =>
        (request.body match
          case UpdateTokens(tokens) => metadata.setTokens(service, feature, tokens)
          case StartIssuingTokens   => metadata.startIssuingTokens(service, feature)
          case StopIssuingTokens    => metadata.stopIssuingTokens(service, feature)
        ).map:
          case UpdateSuccessful => NoContent
          case NoOpUpdateResult => NotFound

  def addTokens(service: Service, feature: Feature): Action[TokenIncrementRequest] =
    authorised(service).async(parse.json[TokenIncrementRequest]):
      request =>
        metadata.addTokens(service, feature, request.body.tokens).map:
          case UpdateSuccessful => NoContent
          case NoOpUpdateResult => NotFound

  def create(service: Service): Action[CreateAllowListRequest] =
    authorised(service).async(parse.json[CreateAllowListRequest]) {
      request => {
        metadata.create(service, request.body.allowList).map {
          _ => Created
        }
      }
    }

}
