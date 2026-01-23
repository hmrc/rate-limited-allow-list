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
import play.api.mvc.Result
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.ratelimitedallowlist.models.{CheckRequest, CheckResponse, ListName, ServiceName}

import scala.concurrent.Future

class AllowListControllerSpec extends AnyFreeSpec with Matchers:
  val serviceName = ServiceName("service-a")
  val listName = ListName("list-1")
  private val fakeRequest =
    FakeRequest("POST", routes.AllowListController.checkAllowList(serviceName, listName).url)
      .withBody(CheckRequest("user-identifier"))
  private val controller = new AllowListController(Helpers.stubControllerComponents())

  "POST checkAllowList" - {
    "return 200 with a failed check" in {
      val result: Future[Result] = controller.checkAllowList(serviceName, listName)(fakeRequest)
      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(CheckResponse(false))
    }
  }
