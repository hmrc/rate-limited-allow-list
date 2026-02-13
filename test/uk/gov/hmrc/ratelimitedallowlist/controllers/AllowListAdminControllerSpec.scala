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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.models.{TokenIncrementRequest, UpdateRequest}
import uk.gov.hmrc.ratelimitedallowlist.repositories.FakeAllowListMetadataRepository
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResultResult.{NoOpUpdateResult, UpdateSuccessful}

import java.time.temporal.ChronoUnit
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AllowListAdminControllerSpec extends AnyFreeSpec, Matchers, MockitoSugar, ScalaFutures:

  private val service = Service("service-a")
  private val feature = Feature("list-1")
  val instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val data1 = AllowListMetadata(service.value, feature.value, 10, true, instant, instant)

  "getFeatures" - {
    val fakeRequest = FakeRequest(routes.AllowListAdminController.getFeatures(service))
      .withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))

      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getByServiceResult = Some(List(data1)))
      )

      val result = controller.getFeatures(service)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.arr(Json.toJsObject(data1))
    }

    "return 404 when there is no data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))

      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getByServiceResult = Some(List.empty))
      )

      val result = controller.getFeatures(service)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getByServiceResult = Some(List.empty))
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.getFeatures(service))

      controller.getFeatures(service)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _                          => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "get" - {
    val fakeRequest = FakeRequest(routes.AllowListAdminController.get(service, feature))
      .withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getResult = Some(Some(data1)))
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(data1)
    }

    "return 400 when there is no data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getResult = Some(None))
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository()
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.get(service, feature))

      controller.get(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _                          => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "patch" - {
    "return 204" - {
      "when token value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(setTokensResult = Some(UpdateSuccessful))
        )

        val fakeRequest =FakeRequest(routes.AllowListAdminController.patch(service, feature))
            .withBody(UpdateRequest.UpdateTokens(20))
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }

      "when canIssueTokens is true and value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(startIssuingTokensResult = Some(UpdateSuccessful))
        )

        val fakeRequest =FakeRequest(routes.AllowListAdminController.patch(service, feature))
            .withBody(UpdateRequest.StartIssuingTokens)
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }

      "when canIssueTokens is false and value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(stopIssuingTokensResult = Some(UpdateSuccessful))
        )

        val fakeRequest = FakeRequest(routes.AllowListAdminController.patch(service, feature))
            .withBody(UpdateRequest.StopIssuingTokens)
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }
    }

    "return 404" - {
      "when token value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(setTokensResult = Some(NoOpUpdateResult))
        )

        val fakeRequest = FakeRequest(routes.AllowListAdminController.patch(service, feature))
            .withBody(UpdateRequest.UpdateTokens(20))
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NOT_FOUND
      }

      "when canIssueTokens is true and value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(startIssuingTokensResult = Some(NoOpUpdateResult))
        )

        val fakeRequest = FakeRequest(routes.AllowListAdminController.patch(service, feature))
            .withBody(UpdateRequest.StartIssuingTokens)
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NOT_FOUND
      }

      "when canIssueTokens is false and value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(stopIssuingTokensResult = Some(NoOpUpdateResult))
        )

        val fakeRequest = FakeRequest(routes.AllowListAdminController.patch(service, feature))
            .withBody(UpdateRequest.StopIssuingTokens)
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NOT_FOUND
      }
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository()
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.patch(service, feature))
        .withBody(UpdateRequest.StopIssuingTokens)

      controller.patch(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "addTokens" - {
    "return 204 when tokens and canIssueTokens value is updated" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(addTokensResult = Some(UpdateSuccessful))
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.addTokens(service, feature))
          .withBody(TokenIncrementRequest(10))
          .withHeaders("Authorization" -> "Token foo")

      val result = controller.addTokens(service, feature)(fakeRequest)

      status(result) mustBe Status.NO_CONTENT
    }

    "return 404 when there is no matching data for service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(addTokensResult = Some(NoOpUpdateResult))
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.addTokens(service, feature))
          .withBody(TokenIncrementRequest(10))
          .withHeaders("Authorization" -> "Token foo")

      val result = controller.addTokens(service, feature)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository()
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.addTokens(service, feature))
        .withBody(TokenIncrementRequest(10))

      controller.addTokens(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }
