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
import uk.gov.hmrc.ratelimitedallowlist.models.*
import uk.gov.hmrc.ratelimitedallowlist.models.domain.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResult.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListConfigurationRepository, AllowListRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class RateLimitedAllowListAdminController @Inject()(
  cc: ControllerComponents,
  auth: AuthActions,
  allowListConfig: AllowListConfigurationRepository,
  allowList: AllowListRepository
)(using ExecutionContext) extends BackendController(cc), Logging:

  def create(service: Service): Action[CreateAllowListConfigurationRequest] =
    auth.authorized.admin.service(service).async(parse.json[CreateAllowListConfigurationRequest]):
      request =>
        allowListConfig.create(service, request.body)
          .map: _ =>
            Created

  def getServices: Action[AnyContent] =
    auth.authenticated.admin.locations async:
      req =>
        val services = req.retrieval.map(_.resourceLocation.value)
        if services.nonEmpty then
          allowListConfig.getServices(services.toList).map:
            case services if services.isEmpty => NotFound
            case services => Ok(Json.toJson(services.map(_.value)))
        else
          logger.info("No services found for user. The user likely not added to the GitHub or not added to a team with services.")
          Future.successful(NoContent)

  def getAllowLists(service: Service): Action[AnyContent] =
    auth.authorized.admin.service(service) async:
      allowListConfig.get(service)
        .map:
          case list if list.isEmpty => NotFound
          case list                 => Ok(Json.toJson(list))

  def get(service: Service, feature: Feature): Action[AnyContent] =
    auth.authorized.admin.service(service) async:
      allowListConfig.get(AllowList(service, feature))
        .map:
          case Some(value) => Ok(Json.toJsObject(value))
          case None => NotFound

  def getAllowListReport(service: Service, feature: Feature): Action[AnyContent] =
    auth.authorized.admin.service(service) async:
      allowList.count(service, feature)
        .map: count =>
          val response = AllowListReportResponse(service.value, feature.value, count, List.empty)
          Ok(Json.toJsObject(response))

  def patch(service: Service, feature: Feature): Action[AllowListConfiguration.Update] =
    auth.authorized.admin.service(service).async(parse.json[AllowListConfiguration.Update]):
      request =>
         allowListConfig.patch(AllowList(service, feature), request.body).map:
           case UpdateSuccessful => Ok
           case NoOpUpdateResult => NoContent
           case UpdateFailed => InternalServerError

  def delete(service: Service, feature: Feature): Action[AnyContent] =
    auth.authorized.admin.service(service).async: _ =>
      allowListConfig.clear(AllowList(service, feature)).map:
        _ => NoContent
