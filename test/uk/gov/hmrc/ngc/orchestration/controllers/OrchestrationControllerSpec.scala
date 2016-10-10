/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.ngc.orchestration.controllers

import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import play.api.libs.json._
import play.api.mvc.{Request, Result}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, PreFlightCheckResponse}
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures with Eventually with StubApplicationConfiguration {

  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  "preFlightCheck live controller " should {

    "return the Pre-Flight Check Response successfully" in new Success {
      val result = await(controller.preFlightCheck()(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"upgradeRequired":true,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"102030394AAA"}}""")
    }

    "return the Pre-Flight Check Response with default version when version-check fails" in new FailurePreFlight {
      val result = await(controller.preFlightCheck()(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"upgradeRequired":false,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"102030394AAA"}}""")
    }

    "return 401 HTTP status code when calls to retrieve the auth account fail" in new AuthWithoutTaxSummary {
      val result = await(controller.preFlightCheck()(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 401
    }

    "return 500 HTTP status code when the HC Authentication object is missing in the request" in new Success {
      val result = await(controller.preFlightCheck()(versionRequest))
      status(result) shouldBe 500
    }

  }

  "async live controller to verify different json attribute response values based on service responses" should {

    "not return taxCreditSummary attribute when 999/auth call fails" in new AuthenticateRenewal {
      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((a, b) => a ++ b)

      invokeStartupAndPollForResult(controller, "async_native-apps-api-id-AuthenticateRenewal", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return bad request when the session auth-token does not match the HC authorization header" in new SessionChecker {
      val body :JsValue= Json.parse("""{"token":"123456"}""")
      val requestWithSessionKeyAndIdBody = FakeRequest().withSession(
        "AuthToken" -> "Invalid"
      ).withHeaders(
          "Accept" -> "application/vnd.hmrc.1.0+json",
          "Authorization" -> "Some Header"
        ).withJsonBody(body)

      val result2: Result = await(controller.startup(nino, None).apply(requestWithSessionKeyAndIdBody))
      status(result2) shouldBe 400
    }

    "return 401 when the Tax Summary response NINO does match the authority NINO" in new SecurityAsyncSetup {
      val emptyTaxCreditSummary = Json.obj("taxCreditSummary" -> Json.parse("""{}"""))
      val jsonMatch = Seq(TestData.taxSummary(), emptyTaxCreditSummary, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, "async_native-apps-api-id-SecurityAsyncSetup", Nino("CS700100A"),
        jsonMatch, 401)(versionRequest)
    }

    "return taxCreditSummary attribute with no summary data when Exclusion returns false" in new ExclusionTrue {
      val emptyTaxCreditSummary = Json.obj("taxCreditSummary" -> Json.parse("""{}"""))
      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, "async_native-apps-api-id-ExclusionTrue", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return taxCreditSummary attribute when submission state is not active" in new RenewalSubmissionNotActive {
        val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummary, TestData.submissionStateOff, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

        invokeStartupAndPollForResult(controller, "async_native-apps-api-id-RenewalSubmissionNotActive", Nino("CS700100A"),
          jsonMatch)(versionRequest)
      }

    "first call fail to exclusion decision with 2nd call returning successfully - validate retry mechanism" in new FailWithRetrySuccess {

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummary, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, "async_native-apps-api-id-FailWithRetrySuccess", Nino("CS700100A"),
        jsonMatch)(versionRequest)

      invokeCount shouldBe 2
    }

    "return an empty taxCreditSummary block when exclusion service throws exceptions" in new ExclusionException {
      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, "async_native-apps-api-id-ExclusionException", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return no taxCreditSummary block when auth/9999 service returns 404 response" in new TestGenericController {
      override val statusCode : Option[Int] = Some(404)
      override val exception: Option[Exception] = None
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesAuthFailMap
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return no taxCreditSummary block when auth/9999 service returns 500 response" in new TestGenericController {
      override val statusCode: Option[Int] = Some(500)
      override val exception: Option[Exception] = None
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesAuthFailMap
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return empty taxCreditSummary block when auth/9999 service returns 200 and tax-credit-decision fails with 404 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new NotFoundException("controlled explosion"))
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-decision" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return empty taxCreditSummary block when auth/9999 service returns 200 and tax-credit-decision fails with 403 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-decision" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return empty taxCreditSummary block when auth/9999 service returns 200 and tax-credit-decision fails with 500 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-decision" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return taxCreditSummary block when submission state returns a non 200 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/tax-credits/submission/state/enabled" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummary, TestData.submissionStateOff, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return error status response when mandatory service tax-summary returns non 200 response and push-registration executed successfully" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val exceptionControl: Boolean = false
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-summary/2016" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.statusError).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)

      pushRegistrationInvokeCount shouldBe 1
    }

    "return taxCreditSummary empty block when the tax-credit-summary service returns a non 200 response + not invoke PushReg when payload is empty" in new TestGenericController {
      override val statusCode : Option[Int] = None
      override val exception :Option[Exception] = Some(new BadRequestException("controlled explosion"))
      override val mapping :Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-summary" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()
      override val exceptionControl: Boolean = false

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeStartupAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch, 200, "{}")(versionRequest)

      pushRegistrationInvokeCount shouldBe 0
    }

    "return throttle status response when throttle limit has been hit" in new ThrottleLimit {
      val result = performStartup("{}", controller, controller.testSessionId, nino)
      status(result) shouldBe 429
      jsonBodyOf(result) shouldBe TestData.statusThrottle
    }


    "Simulating concurrent http requests through the async framework " should {

      "successfully process all concurrent requests and once all tasks are complete, verify the throttle value is 0" in {

        def createController(counter: Int) = {
          new TestGenericController {
            override val time = counter.toLong
            override lazy val test_id = s"test_id_concurrent_$counter"
            override val exception: Option[Exception] = None
            override val statusCode: Option[Int] = None
            override val exceptionControl: Boolean = false
            override val mapping: Map[String, Boolean] = servicesSuccessMap
            override val taxSummaryData: JsValue = TestData.taxSummaryData(Some(test_id))
          }
        }

        excuteParallelAsyncTasks(createController, "async_native-apps-api-id-test_id_concurrent")
      }

      "successfully process all concurrent requests with decision retry, once all tasks are complete verify the throttle value is 0" in {

        def createController(counter: Int) = {

          new TestGenericController {
            override val time = counter.toLong
            override lazy val test_id = s"test_id_concurrent_with_retry_$counter"
            override val exception: Option[Exception] = None
            override val statusCode: Option[Int] = None
            override val exceptionControl: Boolean = true
            override val mapping: Map[String, Boolean] = servicesSuccessMap
            override val taxSummaryData: JsValue = TestData.taxSummaryData(Some(test_id))
          }
        }

        excuteParallelAsyncTasks(createController, "async_native-apps-api-id-test_id_concurrent_with_retry")
      }

      def excuteParallelAsyncTasks(generateController: => Int => TestGenericController, asyncTaskId:String) = {
        val timeStart = System.currentTimeMillis()

        val concurrentRequests = (0 until 20).foldLeft(Seq.empty[TestGenericController]) {
          (list, counter) => {
            list ++ Seq(generateController(counter))
          }
        }

        val result = concurrentRequests.map { asyncTestRequest =>
          val delay = scala.util.Random.nextInt(50)
          TimedEvent.delayedSuccess(delay, 0).map(a => {
            implicit val reqImpl = asyncTestRequest.requestWithAuthSession

            // Build the expected response.
            // Note: The specific async response is validated against the JSON generated server side contains the task Id.
            val jsonMatch = Seq(TestData.taxSummary(Some(asyncTestRequest.controller.testSessionId)), TestData.taxCreditSummary, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

            // Execute the controller async request and poll for response.
            val task_id = s"${asyncTaskId}_${asyncTestRequest.time}"
            invokeStartupAndPollForResult(asyncTestRequest.controller, task_id, Nino("CS700100A"),
              jsonMatch, 200, "{}")(asyncTestRequest.versionRequest)
          })
        }

        eventually(Timeout(Span(95000, Milliseconds)), Interval(Span(2, Seconds))) {
          await(Future.sequence(result))
        }

        uk.gov.hmrc.play.asyncmvc.async.Throttle.current shouldBe 0
        println("Time spent processing... " + (System.currentTimeMillis() - timeStart))
      }
    }
  }

  "startup live controller authentication " should {

    "return unauthorized when authority record does not contain a NINO" in new AuthWithoutNino {
      testNoNINO(await(controller.startup(nino)(emptyRequestWithHeader)))
    }

    "return 401 result with json status detailing low CL on authority" in new AuthWithLowCL {
      testLowCL(await(controller.startup(nino)(emptyRequestWithHeader)))
    }

    "return status code 406 when the headers are invalid" in new Success {
      val result = await(controller.startup(nino)(emptyRequest))
      status(result) shouldBe 406
    }
  }

  "sandbox controller " should {

    "return the PreFlightCheckResponse response from a static resource" in new SandboxSuccess {
      val result = await(controller.preFlightCheck()(requestWithAuthSession.withBody(versionBody)))
      status(result) shouldBe 200
      val journeyIdRetrieve: String = (contentAsJson(result) \ "accounts" \ "journeyId").as[String]
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, journeyIdRetrieve)))
    }

    "return startup response from a static resource" in new SandboxSuccess {
      val result = await(controller.startup(nino)(requestWithAuthSession))//.withBody(versionBody)))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe TestData.sandboxStartupResponse
    }

    "return poll response from a static resource" in new SandboxSuccess {
      val result = await(controller.poll(nino)(requestWithAuthSession))//.withBody(versionBody)))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe TestData.sandboxPollResponse
    }
  }

  val token = "Bearer 123456789"
  def performStartup(inputBody: String, controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino) = {
    val authToken = "AuthToken" -> token
    val authHeader = "Authorization" -> token
    val body: JsValue = Json.parse(inputBody)

    val requestWithSessionKeyAndId = FakeRequest()
      .withSession(
        authToken
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        authHeader
      ).withJsonBody(body)

    await(controller.startup(nino).apply(requestWithSessionKeyAndId))
  }

  def invokeStartupAndPollForResult(controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino, response: JsValue, resultCode: Int = 200, inputBody: String = """{"token":"123456"}""")(implicit request: Request[_]) = {
    val authToken = "AuthToken" -> token
    val authHeader = "Authorization" -> token

    val requestWithSessionKeyAndIdNoBody = FakeRequest().withSession(
      controller.AsyncMVCSessionId -> controller.buildSession(controller.id, testSessionId),
      authToken
    ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        authHeader
      )

    // Perform startup request.
    val result2 = performStartup(inputBody, controller, testSessionId, nino)
    status(result2) shouldBe 200

    // Verify the Id within the session matches the expected test Id.
    val session = result2.session.get(controller.AsyncMVCSessionId)
    val jsonSession = Json.parse(session.get).as[AsyncMvcSession]
    jsonSession.id shouldBe testSessionId

    // Poll for the result.
    eventually(Timeout(Span(10, Seconds))) {
      val result3: Result = await(controller.poll(nino)(requestWithSessionKeyAndIdNoBody))

      status(result3) shouldBe resultCode
      if (resultCode!=401) {
        jsonBodyOf(result3) shouldBe response
      } else {
        if (resultCode!=401) {
          // Verify the returned cookie still has the same Id expected for the test.
          val session = result3.session.get(controller.AsyncMVCSessionId)
          val jsonSession = Json.parse(session.get).as[AsyncMvcSession]
          jsonSession.id shouldBe testSessionId
        }
      }
    }
  }

}