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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.models.{TokenIncrementRequest, UpdateRequest}
import uk.gov.hmrc.ratelimitedallowlist.repositories.FakeAllowListMetadataRepository
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.{NoOpUpdateResult, UpdateSuccessful}

import java.time.temporal.ChronoUnit
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class AllowListAdminControllerSpec extends AnyFreeSpec, Matchers:
  private val service = Service("service-a")
  private val feature = Feature("list-1")
  val instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val data1 = AllowListMetadata(service.value, feature.value, 10, true, instant, instant, "")

  "get" - {
    val fakeRequest =
      FakeRequest("GET", routes.AllowListAdminController.get(service, feature).url)

    "return 200 when there is data for a service and feature" in {
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        FakeAllowListMetadataRepository(getResult = Some(Some(data1)))
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(data1)
    }

    "return 400 when there is no data for a service and feature" in {
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        FakeAllowListMetadataRepository(getResult = Some(None))
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }
  }

  "patch" - {
    "return 204" - {
      "when token value is updated" in {
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          FakeAllowListMetadataRepository(setTokensResult = Some(UpdateSuccessful))
        )

        val fakeRequest =
          FakeRequest("PATCH", routes.AllowListAdminController.patch(service, feature).url)
            .withBody(UpdateRequest.UpdateTokens(20))
        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }

      "when canIssueTokens is true and value is updated" in {
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          FakeAllowListMetadataRepository(startIssuingTokensResult = Some(UpdateSuccessful))
        )

        val fakeRequest =
          FakeRequest("PATCH", routes.AllowListAdminController.patch(service, feature).url)
            .withBody(UpdateRequest.StartIssuingTokens)
        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }

      "when canIssueTokens is false and value is updated" in {
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          FakeAllowListMetadataRepository(stopIssuingTokensResult = Some(UpdateSuccessful))
        )

        val fakeRequest =
          FakeRequest("PATCH", routes.AllowListAdminController.patch(service, feature).url)
            .withBody(UpdateRequest.StopIssuingTokens)
        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }
    }

    "return 404" - {
      "when token value is updated" in {
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          FakeAllowListMetadataRepository(setTokensResult = Some(NoOpUpdateResult))
        )

        val fakeRequest =
          FakeRequest("PATCH", routes.AllowListAdminController.patch(service, feature).url)
            .withBody(UpdateRequest.UpdateTokens(20))
        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NOT_FOUND
      }

      "when canIssueTokens is true and value is updated" in {
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          FakeAllowListMetadataRepository(startIssuingTokensResult = Some(NoOpUpdateResult))
        )

        val fakeRequest =
          FakeRequest("PATCH", routes.AllowListAdminController.patch(service, feature).url)
            .withBody(UpdateRequest.StartIssuingTokens)
        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NOT_FOUND
      }

      "when canIssueTokens is false and value is updated" in {
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          FakeAllowListMetadataRepository(stopIssuingTokensResult = Some(NoOpUpdateResult))
        )

        val fakeRequest =
          FakeRequest("PATCH", routes.AllowListAdminController.patch(service, feature).url)
            .withBody(UpdateRequest.StopIssuingTokens)
        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NOT_FOUND
      }
    }
  }

  "addTokens" - {
    "return 204 when tokens and canIssueTokens value is updated" in {
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        FakeAllowListMetadataRepository(addTokensResult = Some(UpdateSuccessful))
      )

      val fakeRequest =
        FakeRequest("POST", routes.AllowListAdminController.addTokens(service, feature).url)
          .withBody(TokenIncrementRequest(10))
      val result = controller.addTokens(service, feature)(fakeRequest)

      status(result) mustBe Status.NO_CONTENT
    }

    "return 404 when there is no matching data for service and feature" in {
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        FakeAllowListMetadataRepository(addTokensResult = Some(NoOpUpdateResult))
      )

      val fakeRequest =
        FakeRequest("POST", routes.AllowListAdminController.addTokens(service, feature).url)
          .withBody(TokenIncrementRequest(10))
      val result = controller.addTokens(service, feature)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }
  }
