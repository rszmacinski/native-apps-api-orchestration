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


import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json._
import play.api.mvc.{Request, Result}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, PreFlightCheckResponse}
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures with Eventually with StubApplicationConfiguration {

  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)


  "preFlightCheck live controller " should {

    "return the PreFlightCheckResponse" in new Success {
      val result = await(controller.preFlightCheck()(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"upgradeRequired":true,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"102030394AAA"}}""")
    }
  }

  def invokeTestNonBlockAction(controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino, response: JsValue, resultCode: Int = 200, inputBody: String = """{"token":"123456"}""")(implicit request: Request[_]) = {
    val token = "Bearer 123456789"
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

    val requestWithSessionKeyAndIdNoBody = FakeRequest().withSession(
      controller.AsyncMVCSessionId -> controller.buildSession(controller.id, testSessionId),
      authToken

    ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        authHeader
      )

    val result2: Result = await(controller.startup(nino, None).apply(requestWithSessionKeyAndId))
    status(result2) shouldBe 200

    val session = result2.session.get(controller.AsyncMVCSessionId)
    val jsonSession = Json.parse(session.get).as[AsyncMvcSession]
    jsonSession.id shouldBe testSessionId

    eventually(Timeout(Span(10, Seconds))) {
      val result3: Result = await(controller.poll(nino)(requestWithSessionKeyAndIdNoBody))

      status(result3) shouldBe resultCode
      if (resultCode!=401)
        jsonBodyOf(result3) shouldBe response
    }
  }


  "startup live controller to verify different json attribute response values" should {

    "not return taxCreditSummary attribute when 999/auth call fails" in new AuthenticateRenewal {
      val jsonMatch = Seq(TestData.taxSummary, TestData.submissionStateB, TestData.statusComplete).foldLeft(Json.obj())((a, b) => a ++ b)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-AuthenticateRenewal", Nino("CS700100A"),
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
      val jsonMatch = Seq(TestData.taxSummary, emptyTaxCreditSummary, TestData.submissionStateB, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-SecurityAsyncSetup", Nino("CS700100A"),
        jsonMatch, 401)(versionRequest)
    }

    "return taxCreditSummary attribute with no summary data when Exclusion returns true" in new ExclusionTrue {

      val emptyTaxCreditSummary = Json.obj("taxCreditSummary" -> Json.parse("""{}"""))
      val jsonMatch = Seq(TestData.taxSummary, TestData.taxCreditSummary, TestData.submissionStateB, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-ExclusionTrue", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return taxCreditSummary attribute when submission state is not active" in new RenewalSubmissionNotActive {
        val jsonMatch = Seq(TestData.taxSummary, TestData.taxCreditSummary, TestData.submissionState, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

        invokeTestNonBlockAction(controller, "async_native-apps-api-id-RenewalSubmissionNotActive", Nino("CS700100A"),
          jsonMatch)(versionRequest)
      }

    "return taxCreditSummary attribute when submission shutter is active" in new RenewalSubmissionShutterActive {
      val jsonMatch = Seq(TestData.taxSummary, TestData.taxCreditSummary, TestData.submissionState, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-RenewalSubmissionShutterActive", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "first call fails with retry Success" in new FailWithRetrySuccess {

      val jsonMatch = Seq(TestData.taxSummary, TestData.taxCreditSummary, TestData.submissionStateB, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-FailWithRetrySuccess", Nino("CS700100A"),
        jsonMatch)(versionRequest)

      invokeCount shouldBe 2
    }

    "fail when there is no Mandatory data found for tax summary" in new Failure {
      invokeTestNonBlockAction(controller, "async_native-apps-api-id-Failure", Nino("CS700100A"), TestData.statusError)(versionRequest)
    }

    "not fail when there is no data for Preferences" in new PreferenceFailure {
      val jsonMatch = Seq(TestData.taxSummary, TestData.taxCreditSummary, TestData.submissionStateB, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-PreferenceFailure", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "not fail when there is no data tax-credit-summary and preferences " in new OptionalDataFailure {
      val jsonMatch = Seq(TestData.taxSummary, TestData.submissionState, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-OptionalDataFailure", Nino("CS700100A"),
        jsonMatch)(versionRequest)

      controller.pushRegistrationInvokeCount shouldBe 1
    }

    "not invoke push-registration service when the POST payload is empty" in new OptionalFirebaseToken {
      val jsonMatch = Seq(TestData.taxSummary, TestData.submissionState, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeTestNonBlockAction(controller, "async_native-apps-api-id-OptionalFirebaseToken", Nino("CS700100A"),
        jsonMatch, 200, "{}")(versionRequest)

      controller.pushRegistrationInvokeCount shouldBe 0
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
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, journeyIdRetrieve)))
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

}