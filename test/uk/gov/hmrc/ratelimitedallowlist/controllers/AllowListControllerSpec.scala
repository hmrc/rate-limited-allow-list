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

import org.scalatest.matchers.must.Matchers
import org.scalatest.freespec.AnyFreeSpec
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.ratelimitedallowlist.models.{CheckRequest, CheckResponse}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.services.FakeAllowListService

import scala.concurrent.ExecutionContext.Implicits.global

class AllowListControllerSpec extends AnyFreeSpec with Matchers:
  private val service = Service("service-a")
  private val feature = Feature("list-1")
  private val fakeRequest =
    FakeRequest("POST", routes.AllowListController.checkAllowList(service, feature).url)
      .withBody(CheckRequest("user-identifier"))

  "POST checkAllowList" - {
    "when checks pass, return 200 with a passing check" in {
      val controller = AllowListController(Helpers.stubControllerComponents(), FakeAllowListService(true))

      val result = controller.checkAllowList(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(CheckResponse(true))
    }

    "when checks fail, return 200 with a failed check" in {
      val controller = AllowListController(Helpers.stubControllerComponents(), FakeAllowListService(false))

      val result = controller.checkAllowList(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(CheckResponse(false))
    }
  }