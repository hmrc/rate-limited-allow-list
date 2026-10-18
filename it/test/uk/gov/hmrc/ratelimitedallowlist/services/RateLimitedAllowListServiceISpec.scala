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

package uk.gov.hmrc.ratelimitedallowlist.services

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.ratelimitedallowlist.models.domain.CheckResult.*
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{Feature as FeatureModel, *}
import uk.gov.hmrc.ratelimitedallowlist.repositories.UpdateResult.*
import uk.gov.hmrc.ratelimitedallowlist.repositories.{AllowListConfigurationRepository, CreateResult, UpdateResult}
import uk.gov.hmrc.ratelimitedallowlist.utils.TimeTravelClock

import java.time.Clock
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class RateLimitedAllowListServiceISpec extends AnyFeatureSpec, GivenWhenThen, Matchers, ScalaFutures,
  GuiceOneAppPerSuite, BeforeAndAfterEach, BeforeAndAfterAll, MongoSupport, IntegrationPatience {

  given FiniteDuration = 5.seconds

  val clock = TimeTravelClock()

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[Clock].toInstance(clock),
        bind[MongoComponent].toInstance(mongoComponent)
      )
      .build()

  override def beforeAll(): Unit = {
    super.beforeAll()
    dropDatabase()
  }

  override def beforeEach(): Unit = {
    clock.resetTimeTravel()
    dropDatabase()
    super.beforeEach()
  }

  Feature("User onboarding - Percentage load for onboarding of user"):
    Scenario("The percentage load is set to zero"):
      Given("There are no onboarded users")
      val allowList = newAllowList(1)
      allowList.asAdmin.create
      allowList.asAdmin.isEnabled
      allowList.asAdmin.setPercentage(0)

      When("A request to onboard user")
      val result = allowList.forUser.check(identifier(1))

      Then("The user will not be onboarded")
      result mustEqual Excluded

    Scenario("The percentage is set to non-zero - single user"):
      val percentage = 100

      Given(s"The percentage is set to $percentage ")
      val allowList = newAllowList(1)
      allowList.asAdmin.create
      allowList.asAdmin.isEnabled

      val updateResult = allowList.asAdmin.setPercentage(percentage)
      updateResult mustEqual UpdateSuccessful

      When("A request to onboard user")
      val result = allowList.forUser.check(identifier(1))

      Then("The user will not be onboarded")
      result mustEqual Added

    Scenario("The percentage is set to non-zero - multiple users & multi-days"):
      val allowList = newAllowList(2)
      val percentage = 40

      Given("There are no onboarded users")
      allowList.asAdmin.create
      allowList.asAdmin.isEnabled
      clock.fastForwardTime(60)

      And(s"The percentage load is $percentage")
      val setPercentageResult = allowList.asAdmin.setPercentage(percentage)
      setPercentageResult mustEqual UpdateSuccessful

      When("10 users attempt to onboard")
      val sampleSize = 500
      val tolerance = (sampleSize * 0.1).toInt


      val onboardingResult1 = Future.sequence((1 to 100).map(identifier).map(allowList.forUser.future.check)).futureValue
      clock.fastForwardTime(60) // One minute later
      val onboardingResult2 = Future.sequence((101 to 200).map(identifier).map(allowList.forUser.future.check)).futureValue
      clock.fastForwardTime(60) // One minute later
      val onboardingResult3 = Future.sequence((201 to 300).map(identifier).map(allowList.forUser.future.check)).futureValue
      clock.fastForwardTime(60*60) // One hour later
      val onboardingResult4 = Future.sequence((301 to 400).map(identifier).map(allowList.forUser.future.check)).futureValue
      clock.fastForwardTime(60) // One minute later
      val onboardingResult5 = Future.sequence((401 to 500).map(identifier).map(allowList.forUser.future.check)).futureValue
      clock.fastForwardTime(60*60*24) // One day later

      val excludedResult1 = onboardingResult1.count(_ == Excluded)
      val excludedResult2 = onboardingResult2.count(_ == Excluded)
      val excludedResult3 = onboardingResult3.count(_ == Excluded)
      val excludedResult4 = onboardingResult4.count(_ == Excluded)
      val excludedResult5 = onboardingResult5.count(_ == Excluded)

      val excludedResult = excludedResult1 + excludedResult2 + excludedResult3 + excludedResult4 + excludedResult5
      val addedResult = sampleSize - excludedResult

      excludedResult mustBe (300 +- tolerance)
      addedResult mustEqual (200 +- tolerance)


  def identifier(i: Int): String = s"identifier value $i"
  def newAllowList(i: Int): AllowList = AllowList(Service(s"service$i"), FeatureModel(s"feature$i"))

  def repo: AllowListConfigurationRepository = app.injector.instanceOf[AllowListConfigurationRepository]
  def service: RateLimitedAllowListService = app.injector.instanceOf[RateLimitedAllowListService]

  def update(timeframeLimit: Option[TimeframeLimit] = None,
             userLimit: Option[Int] = None,
             percentageLoad: Option[Int] = None,
             isEnabled: Option[Boolean] = None): AllowListConfiguration.Update =
    AllowListConfiguration.Update(timeframeLimit, userLimit, percentageLoad, isEnabled)

  case class AdminActions(a: AllowList) {
    def create: CreateResult =
      repo.create(a.service, AllowListConfiguration.Create(a.feature.value, 0, None, None)).futureValue

    def isEnabled: UpdateResult = repo.patch(a, update(isEnabled = Some(true))).futureValue

    def setPercentage(i: Int): UpdateResult = repo.patch(a, update(percentageLoad = Some(i))).futureValue
  }

  case class UserActions(a: AllowList) {
    object future:
      def check(i: String): Future[CheckResult] = service.checkOrAdd(a, i)

    def check(i: String): CheckResult =
      future.check(i).futureValue
  }

  extension (a: AllowList) {
    def asAdmin: AdminActions = AdminActions(a)
    def forUser: UserActions = UserActions(a)
  }
}