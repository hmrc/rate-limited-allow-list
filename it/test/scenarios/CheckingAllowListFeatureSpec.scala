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

package scenarios

import org.apache.pekko.actor.ActorSystem
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{AppendedClues, GivenWhenThen, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.{Application, Configuration, Environment, Play, inject}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.PlayMongoModule
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.ratelimitedallowlist.controllers.routes
import uk.gov.hmrc.ratelimitedallowlist.models.{CheckRequest, CheckResponse}
import uk.gov.hmrc.ratelimitedallowlist.models.domain.{AllowListMetadata, Service, Feature as FeatureModel}
import uk.gov.hmrc.ratelimitedallowlist.repositories.AllowListMetadataRepositoryImpl

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CheckingAllowListFeatureSpec extends AnyFeatureSpec, GivenWhenThen, Matchers, AppendedClues,
  DefaultPlayMongoRepositorySupport[AllowListMetadata], OptionValues:

  val fixedInstant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  val clock = Clock.fixed(fixedInstant, ZoneId.systemDefault())

  val service1 = Service("foo-bar-frontend")
  val service2 = Service("other-frontend")
  val feature1 = FeatureModel("private-beta-2026")

  val initialMetadata1 = AllowListMetadata(service1.value, feature1.value, 0, true, clock.instant(), clock.instant(), "")
  val initialMetadata2 = AllowListMetadata(service2.value, feature1.value, 0, true, clock.instant(), clock.instant(), "")

  val configuration = Configuration(
    "mongodb.collections.allow-list-metadata.token-updates.0.service" -> service1.value,
    "mongodb.collections.allow-list-metadata.token-updates.0.feature" -> feature1.value,
    "mongodb.collections.allow-list-metadata.token-updates.0.tokens" -> 100,
    "mongodb.collections.allow-list-metadata.token-updates.0.id" -> "update-1",
    "features.allow-config-token-updates" -> true
  ).withFallback(Configuration.load(Environment.simple()))

  def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .disable(classOf[PlayMongoModule])
      .configure(configuration)
      .overrides(
        inject.bind[MongoComponent].to(mongoComponent),
        inject.bind[Clock].to(clock),
      )
      .build()

  override val repository: AllowListMetadataRepositoryImpl =
    AllowListMetadataRepositoryImpl(mongoComponent, configuration, clock)
    
  val app = fakeApplication()

  override def beforeAll(): Unit = {
    super.beforeAll()
    Play.start(app)
    prepareDatabase()
    Future.sequence(List(
      insert(initialMetadata1),
      insert(initialMetadata2)
    )).futureValue
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Play.stop(app)
  }

  given ActorSystem = ActorSystem()
  val wsClient: StandaloneAhcWSClient   = StandaloneAhcWSClient()

  Feature("Updating tokens from config"):
    Scenario(s"Tokens are updated when the app is restarted"):
      Given(s"token config for ${service1.value} ${feature1.value}")

      Then("token config should be updated")
      repository.get(service1, feature1).futureValue.value mustEqual initialMetadata2.copy(tokens = 100)

  Feature("Checking the allow list"):

    Scenario("an empty allow list with no tokens"):
       Given(s"the service ${service1.value} for feature ${feature1.value}")

       Then("the response should be a 200")
       val url = "http://localhost:11308" + routes.AllowListController.checkAllowList(service1, feature1).url
       val body = Json.toJsObject(CheckRequest("service1-foo"))
       val res = wsClient.url(url).post(body).futureValue
       {res.status mustEqual 200 } withClue s"Response body was ${res.body}"

       And("The body should be JSON with a negative flag")
       Json.parse(res.body) mustEqual Json.toJsObject(CheckResponse(false))

    Scenario("an empty allow list with 1 token"):
      Given(s"the service ${service1.value} for feature ${feature1.value}")

      Then("the response should be a 200")
      val url = "http://localhost:11308" + routes.AllowListController.checkAllowList(service2, feature1).url
      val body = Json.toJsObject(CheckRequest("service2-foo"))
      val res = wsClient.url(url).post(body).futureValue
      {
        res.status mustEqual 200
      } withClue s"Response body was ${res.body}"

      And("The body should be JSON with a positive flag")
      Json.parse(res.body) mustEqual Json.toJsObject(CheckResponse(true))

    Scenario("an allow list with a saved identifier and no tokens"):
      Given(s"the service ${service1.value} for feature ${feature1.value}")

      Then("the response should be a 200")
      val url = "http://localhost:11308" + routes.AllowListController.checkAllowList(service2, feature1).url
      val body = Json.toJsObject(CheckRequest("service2-foo"))
      val res = wsClient.url(url).post(body).futureValue
      {
        res.status mustEqual 200
      } withClue s"Response body was ${res.body}"

      And("The body should be JSON with a positive flag")
      Json.parse(res.body) mustEqual Json.toJsObject(CheckResponse(true))
