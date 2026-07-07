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
import play.api.mvc.Result
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.Resource
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.ratelimitedallowlist.models.*
import uk.gov.hmrc.ratelimitedallowlist.models.domain.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.CreateResult.CreateSuccessful
import uk.gov.hmrc.ratelimitedallowlist.repositories.DeleteResult.DeleteSuccessful
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResult.{NoOpUpdateResult, UpdateFailed, UpdateSuccessful}
import uk.gov.hmrc.ratelimitedallowlist.repositories.{FakeAllowListConfigurationRepository, FakeAllowListRepository}
import uk.gov.hmrc.ratelimitedallowlist.utils.TimeTravelClock

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RateLimitedAllowListAdminControllerSpec extends AnyFreeSpec, Matchers, MockitoSugar, ScalaFutures:

  private val service = Service("service-a")
  private val feature = Feature("list-1")
  val instant: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val clock = TimeTravelClock()
  val data1 = AllowListConfiguration(
    service = service.value,
    feature = feature.value,
    userLimitPerTimeframe = 10,
    timeframe = Daily.bound,
    userLimit = 100,
    percentageLoad = 20,
    created = clock.instant()
  )

  val requestData = CreateAllowListConfigurationRequest(
    feature = feature.value,
    userLimitPerTimeframe = 10,
    timeframe = Daily,
    userLimit = 100,
    percentageLoad = 0
  )
  
  private val resources = Set(
    Resource.from("rate-limited-allow-list-admin-frontend", service.value),
    Resource.from("rate-limited-allow-list-admin-frontend", "foo")
  )

  "getServices" - {
    val fakeRequest = FakeRequest(routes.RateLimitedAllowListAdminController.getServices())
      .withHeaders("Authorization" -> "Token foo")

    "return 200 with list of services when services are found" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(resources))

      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getServicesResult = Some(List(service))),
        FakeAllowListRepository()
      )

      val result = controller.getServices()(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.arr(Json.toJson(service.value))
    }

    "return 204 when there are no services found in database" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(resources))

      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getServicesResult = Some(List.empty)),
        FakeAllowListRepository()
      )

      val result = controller.getServices()(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]

      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(routes.RateLimitedAllowListAdminController.getServices())

      controller.getServices()(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }

    "returns 404 when there are no services found for the user" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(Set()))

      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val result = controller.getServices()(fakeRequest)

      status(result) mustBe Status.NO_CONTENT
    }
  }

  "getAllowLists" - {
    val fakeRequest = FakeRequest(routes.RateLimitedAllowListAdminController.getAllowLists(service))
      .withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))

      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getByServiceResult = Some(List(data1))),
        FakeAllowListRepository()
      )

      val result = controller.getAllowLists(service)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.arr(Json.toJsObject(data1))
    }

    "return 404 when there is no data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))

      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getByServiceResult = Some(List.empty)),
        FakeAllowListRepository()
      )

      val result = controller.getAllowLists(service)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getByServiceResult = Some(List.empty)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(routes.RateLimitedAllowListAdminController.getAllowLists(service))

      controller.getAllowLists(service)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "get" - {
    val url = routes.RateLimitedAllowListAdminController.get(service, feature)

    val fakeRequest = FakeRequest(url)
      .withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getResult = Some(Some(data1))),
        FakeAllowListRepository()
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(data1)
    }

    "return 400 when there is no data for a service and feature" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(getResult = Some(None)),
        FakeAllowListRepository()
      )

      val result = controller.get(service, feature)(fakeRequest)

      status(result) mustBe Status.NOT_FOUND
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)

      controller.get(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _                          => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "getFeatureReport" - {
    val url = routes.RateLimitedAllowListAdminController.getAllowListReport(service, feature)
    val fakeRequest = FakeRequest(url).withHeaders("Authorization" -> "Token foo")

    "return 200 when there is data for a service and feature" in {
      val report = AllowListReportResponse(service.value, feature.value, 11, List.empty)
 
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository(countResult = Some(report.currentUserCount))
      )

      val result = controller.getAllowListReport(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(report)
    }

    "return 200 with empty response when there is no data for a service and feature" in {
      val report = AllowListReportResponse(service.value, feature.value, 0, List.empty)
 
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository(countResult = Some(0))
      )

      val result = controller.getAllowListReport(service, feature)(fakeRequest)

      status(result) mustBe Status.OK
      contentAsJson(result) mustBe Json.toJsObject(report)
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)

      controller.getAllowListReport(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "patch" - {
    val url = routes.RateLimitedAllowListAdminController.patch(service, feature)

    "return 200" - {
      "when the update is successful and the values in the database match those in the update request" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = RateLimitedAllowListAdminController(
          Helpers.stubControllerComponents(),
          AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
          FakeAllowListConfigurationRepository(updateResult = Some(UpdateSuccessful)),
          FakeAllowListRepository()
        )

        val fakeRequest =FakeRequest(url)
          .withBody(AllowListConfiguration.Update.apply(Some(1), Some(Hourly.bound), Some(1), Some(1)))
          .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.OK
      }
    }

    "return 204" - {
      "when no configuration values are provided in the update" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = RateLimitedAllowListAdminController(
          Helpers.stubControllerComponents(),
          AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
          FakeAllowListConfigurationRepository(updateResult = Some(NoOpUpdateResult)),
          FakeAllowListRepository()
        )

        val fakeRequest = FakeRequest(url)
          .withBody(AllowListConfiguration.Update(None, None, None, None))
          .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.NO_CONTENT
      }
    }

    "return 503" - {
      "when values in the database do not match those in the update request after database operation completes" in {
        val mockStubBehaviour = mock[StubBehaviour]
        when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
        val controller = RateLimitedAllowListAdminController(
          Helpers.stubControllerComponents(),
          AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
          FakeAllowListConfigurationRepository(updateResult = Some(UpdateFailed)),
          FakeAllowListRepository()
        )

        val fakeRequest = FakeRequest(url)
            .withBody(AllowListConfiguration.Update(None, None, None, None))
            .withHeaders("Authorization" -> "Token foo")

        val result = controller.patch(service, feature)(fakeRequest)

        status(result) mustBe Status.INTERNAL_SERVER_ERROR
      }
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
        .withBody(AllowListConfiguration.Update(None, None, None, None))

      controller.patch(service, feature)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "create" - {
    val url = routes.RateLimitedAllowListAdminController.create(service)

    "return 201 when the service and feature do not exist" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(createResult = Some(CreateSuccessful)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
          .withBody(requestData)
          .withHeaders("Authorization" -> "Token foo")

      val result: Future[Result] = controller.create(service)(fakeRequest)

      status(result) mustBe Status.CREATED
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url).withBody(requestData)

      controller.create(service)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }

  "delete" - {
    val url = routes.RateLimitedAllowListAdminController.delete(service, feature)

    "return 201" in {
      val mockStubBehaviour = mock[StubBehaviour]
      when(mockStubBehaviour.stubAuth(any(), any())).thenReturn(Future.successful(()))
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(clearResult = Some(DeleteSuccessful)),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url)
          .withHeaders("Authorization" -> "Token foo")

      val result: Future[Result] = controller.delete(service, feature)(fakeRequest)

      status(result) mustBe Status.NO_CONTENT
    }

    "returns 401 when there is no session" in {
      val mockStubBehaviour = mock[StubBehaviour]
      val controller = RateLimitedAllowListAdminController(
        Helpers.stubControllerComponents(),
        AuthActions(BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), global)),
        FakeAllowListConfigurationRepository(),
        FakeAllowListRepository()
      )

      val fakeRequest = FakeRequest(url).withBody(requestData)

      controller.create(service)(fakeRequest).failed.futureValue match
        case res: UpstreamErrorResponse => res.statusCode mustEqual 401
        case _ => fail("Expected but did not get UpstreamErrorResponse")
    }
  }
