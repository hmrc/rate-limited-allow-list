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
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.Resource
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.ratelimitedallowlist.models.ReportFrequency.daily
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Feature, Service}
import uk.gov.hmrc.ratelimitedallowlist.models.{AllowListReportQueryParams, AllowListReportResponse, CreateAllowListRequest, TokenIncrementRequest, UpdateRequest}
import uk.gov.hmrc.ratelimitedallowlist.repositories.CreateResult.CreateSuccessful
import uk.gov.hmrc.ratelimitedallowlist.repositories.{FakeAllowListMetadataRepository, FakeAllowListRepository}
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
  
  private val resources = Set(
    Resource.from("rate-limited-allow-list-admin-frontend", service.value),
    Resource.from("rate-limited-allow-list-admin-frontend", "foo")
  )

  "getServices" - {
    val fakeRequest = FakeRequest(routes.AllowListAdminController.getServices())
      .withHeaders("Authorization" -> "Token foo")
    
    "return 200 with list of services when services are found" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(resources))

      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getServicesResult = Some(List(service))),
        FakeAllowListRepository()
      )
      
      val result = controller.getServices()(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.arr(Json.toJson(service.value))
    }

    "return 200 with an empty list when there are no services found" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(resources))

      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getServicesResult = Some(List.empty)),
        FakeAllowListRepository()
      )

      val result = controller.getServices()(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe JsArray.empty
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]

      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.getServices())

      controller.getServices()(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _                          => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "getFeatures" - {
    val fakeRequest = FakeRequest(routes.AllowListAdminController.getFeatures(service))
      .withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))

      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getByServiceResult = Some(List(data1))),
        FakeAllowListRepository()
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
        FakeAllowListMetadataRepository(getByServiceResult = Some(List.empty)),
        FakeAllowListRepository()
      )

      val result = controller.getFeatures(service)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getByServiceResult = Some(List.empty)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(routes.AllowListAdminController.getFeatures(service))

      controller.getFeatures(service)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "get" - {
    val url = routes.AllowListAdminController.get(service, feature)

    val fakeRequest = FakeRequest(url)
      .withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(getResult = Some(Some(data1))),
        FakeAllowListRepository()
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
        FakeAllowListMetadataRepository(getResult = Some(None)),
        FakeAllowListRepository()
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)

      controller.get(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _                          => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "getFeatureReport" - {
    val queryParams = AllowListReportQueryParams(daily)
    val url = routes.AllowListAdminController.getAllowListReport(service, feature, queryParams)
    val fakeRequest = FakeRequest(url).withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val report = AllowListReportResponse(service.value, feature.value, 11, List.empty)
 
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository(countResult = Some(report.currentUserCount))
      )

      val result = controller.getAllowListReport(service, feature, queryParams)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(report)
    }

    "return 200 with empty response when there is no data for a service and feature" in {
      val report = AllowListReportResponse(service.value, feature.value, 0, List.empty)
 
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository(countResult = Some(0))
      )

      val result = controller.getAllowListReport(service, feature, queryParams)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(report)
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)

      controller.getAllowListReport(service, feature, queryParams)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }
 
  "patch" - {
    val url = routes.AllowListAdminController.patch(service, feature)

    "return 204" - {
      "when token value is updated" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = AllowListAdminController(
          Helpers.stubControllerComponents(),
          BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
          FakeAllowListMetadataRepository(setTokensResult = Some(UpdateSuccessful)),
        FakeAllowListRepository()
        )

        val fakeRequest =FakeRequest(url)
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
          FakeAllowListMetadataRepository(startIssuingTokensResult = Some(UpdateSuccessful)),
        FakeAllowListRepository()
        )

        val fakeRequest =FakeRequest(url)
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
          FakeAllowListMetadataRepository(stopIssuingTokensResult = Some(UpdateSuccessful)),
        FakeAllowListRepository()
        )

        val fakeRequest = FakeRequest(url)
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
          FakeAllowListMetadataRepository(setTokensResult = Some(NoOpUpdateResult)),
        FakeAllowListRepository()
        )

        val fakeRequest = FakeRequest(url)
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
          FakeAllowListMetadataRepository(startIssuingTokensResult = Some(NoOpUpdateResult)),
        FakeAllowListRepository()
        )

        val fakeRequest = FakeRequest(url)
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
          FakeAllowListMetadataRepository(stopIssuingTokensResult = Some(NoOpUpdateResult)),
        FakeAllowListRepository()
        )

        val fakeRequest = FakeRequest(url)
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
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
        .withBody(UpdateRequest.StopIssuingTokens)

      controller.patch(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "addTokens" - {
    val url = routes.AllowListAdminController.addTokens(service, feature)

    "return 204 when tokens and canIssueTokens value is updated" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(addTokensResult = Some(UpdateSuccessful)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
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
        FakeAllowListMetadataRepository(addTokensResult = Some(NoOpUpdateResult)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
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
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
        .withBody(TokenIncrementRequest(10))

      controller.addTokens(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "create" - {
    val url = routes.AllowListAdminController.create(service)

    "return 201 when the service and feature do not exist" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(createResult = Some(CreateSuccessful)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
          .withBody(CreateAllowListRequest(Feature("allow-list-name")))
          .withHeaders("Authorization" -> "Token foo")

      val result = controller.create(service)(fakeRequest)

      status(result) mustBe Status.CREATED
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = AllowListAdminController(
        Helpers.stubControllerComponents(),
        BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global),
        FakeAllowListMetadataRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url).withBody(CreateAllowListRequest(Feature("allow-list-name")))

      controller.create(service)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }
