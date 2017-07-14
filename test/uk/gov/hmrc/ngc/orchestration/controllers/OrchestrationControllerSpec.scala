/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.joda.time.LocalDate
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import play.api.libs.json._
import play.api.mvc.{Request, Result}
import play.api.test.Helpers._
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, PreFlightCheckResponse}
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class ConfigCheckSpec extends UnitSpec {
  "Verify configuration loader for max-age caching" should {
    "throw an exception if the configuration cannot be loaded" in {
      val config = new ConfigLoad {
        override def getConfigForPollMaxAge: Option[Long] = None
      }

      intercept[Exception] {
        config.maxAgeForSuccess
      }
    }

    "Return the configuration when defined" in {
      val config = new ConfigLoad {
        override def getConfigForPollMaxAge: Option[Long] = Some(123456789)
      }

      config.maxAgeForSuccess shouldBe 123456789
    }
  }
}

class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures with Eventually with StubApplicationConfiguration {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override lazy val fakeApplication = FakeApplication(additionalConfiguration = config)

  "preFlightCheck live controller " should {

    "return the Pre-Flight Check Response successfully" in new Success {
      val result = await(controller.preFlightCheck(None)(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"upgradeRequired":true,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"102030394AAA"}}""")
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }

    "return the Pre-Flight Check Response successfully with the supplied journeyId" in new Success {
      val result = await(controller.preFlightCheck(Some(journeyId))(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(s"""{"upgradeRequired":true,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"$journeyId"}}""")
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }

    "return the Pre-Flight Check Response with default version when version-check fails" in new FailurePreFlight {
      val result = await(controller.preFlightCheck(None)(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"upgradeRequired":false,"accounts":{"nino":"CS700100A","routeToIV":false,"routeToTwoFactor":false,"journeyId":"102030394AAA"}}""")
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }

    "return 401 HTTP status code when calls to retrieve the auth account fail" in new AuthWithoutTaxSummary {
      val result = await(controller.preFlightCheck(None)(versionRequest.withHeaders("Authorization" -> "Bearer 123456789")))
      status(result) shouldBe 401
    }

  }

  "preFlightCheck live controller with MFA start request" should {

    "return response with MFA URIs and routeToTwoFactor equal to true when cred-strength is not strong" in new SuccessMfa {
      val result = await(controller.preFlightCheck(Some(journeyId))(versionRequestWithMFA().withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 200

      contentAsJson(result) shouldBe Json.parse(journeyStartResponse(journeyId))
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi")
    }

    "return 500 response when the MFA service fails" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "UNVERIFIED"
      override lazy val startMfaJourney = Map("/multi-factor-authentication/journey" -> false)

      val result = await(controller.preFlightCheck(Some(journeyId))(versionRequestWithMFA("invalid").withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 400
    }

    "return bad request when the MFA operation supplied is invalid" in new SuccessMfa {
        override lazy val mfaOutcomeStatus = "UNVERIFIED"

        val result = await(controller.preFlightCheck(Some(journeyId))(versionRequestWithMFA("invalid").withHeaders("Authorization" -> "Bearer 123456789")))

        status(result) shouldBe 400
      }
    }

  "preFlightCheck live controller with MFA outcome request " should {

    "return the response mfa URIs and routeToTwoFactor equal to true when MFA API returns UNVERIFIED state" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "UNVERIFIED"

      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 200

      contentAsJson(result) shouldBe Json.parse(journeyStartResponse(journeyId))
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }

    "return bad request when the mfaURI is not included in the request" in new SuccessMfa {

      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithInvalidMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 400
      contentAsJson(result) shouldBe Json.parse("""{"code":"BAD_REQUEST","message":"Invalid POST request"}""")
    }

    "return response with routeToTwoFactor set to false when MFA returns NOT_REQUIRED state" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "NOT_REQUIRED"
      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 200

      contentAsJson(result) shouldBe Json.parse(preflightResponse(journeyId))
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }

    "return response with routeToTwoFactor set to false when MFA returns SKIPPED state" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "SKIPPED"
      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 200

      contentAsJson(result) shouldBe Json.parse(preflightResponse(journeyId))
    }

    "return response with routeToTwoFactor set to false and authority updated with credStrength Strong when MFA returns VERIFIED state" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "VERIFIED"
      override lazy val generateAuthError = false

      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 200

      contentAsJson(result) shouldBe Json.parse(preflightResponse(journeyId))
      result.header.headers.get("Set-Cookie").get contains ("mdtpapi=")
    }

    "return 500 response when fail to update the authority record with strong cred-strength" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "VERIFIED"

      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))

      status(result) shouldBe 500
    }

    "return 500 response when MFA returns unknown state" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "UNKNOWN STATE!!!"

      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))


      status(result) shouldBe 500
    }

    "return 500 response when MFA fails to return outcome state" in new SuccessMfa {
      override lazy val mfaOutcomeStatus = "VERIFIED"
      override lazy val outcomeMfaJourney = Map("/multi-factor-authentication/journey/58d96846280000f7005d388e?origin=ngc" -> false)

      val result = await(
        controller.preFlightCheck(Some(
          journeyId))(versionRequestWithMFAOutcome.withHeaders("Authorization" -> "Bearer 123456789")))


      status(result) shouldBe 500
    }

  }

  "async live controller (verify different json attribute response values based on service responses)" should {

    "return 401 when the Tax Summary response NINO does match the authority NINO" in new SecurityAsyncSetup {
      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, "async_native-apps-api-id-SecurityAsyncSetup", Nino("CS700100A"),
        jsonMatch, 401)(versionRequest)
    }

    "return 401 when the poll request NINO does not match the NINO associated in the async response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new NotFoundException("controlled explosion"))
      override lazy val test_id: String = s"test_id_testOrchestrationNinoCheck_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()
      override lazy val authConnector = new TestAuthConnector(Some(Nino("CS700100A")))

      override val mapping:Map[String, Boolean] = servicesSuccessMap ++
        Map(
          "/income/CS700100A/tax-summary/2017" -> false,
          "/income/CS700100A/tax-credits/tax-credits-summary" -> false
        )

      val jsonMatch = Seq(TestData.taxSummaryEmpty, TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, "async_native-apps-api-id-"+test_id, Nino("CS700100A"),
        jsonMatch, 401, """{"token":"123456"}""", Some("max-age=14400"), Some(Nino("CS722100B")))(versionRequest)
    }

    "return taxCreditSummary attribute with no summary data when Exclusion returns false" in new ExclusionTrue {
      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).
        foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, "async_native-apps-api-id-ExclusionTrue", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return taxCreditSummary attribute when submission state is not active" in new RenewalSubmissionNotActive {
        val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummary, TestData.submissionStateOff, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

        invokeOrchestrateAndPollForResult(controller, "async_native-apps-api-id-RenewalSubmissionNotActive", Nino("CS700100A"),
          jsonMatch)(versionRequest)
      }

    "return no taxCreditSummary attribute when exclusion service throws 400 exception" in new ExclusionException {
      override val testId = "BadRequest"

      override lazy val decisionFailureException = Some(new BadRequestException("Controlled 404"))

      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$testId", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return no taxCreditSummary attribute when exclusion service throws 404 exception" in new ExclusionException {
      override val testId = "NotFound"

      override lazy val decisionFailureException = Some(new NotFoundException("Controlled 404"))

      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$testId", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return no taxCreditSummary attribute when exclusion service throws 401 exception" in new ExclusionException {
      override val testId = "Unauthorized"

      override lazy val decisionFailureException = Some(new Upstream4xxResponse("Controlled 404", 401, 401))

      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$testId", Nino("CS700100A"),
        jsonMatch)(versionRequest)

      authConnector.grantAccountCount should be >= 2
    }


    "return empty taxCreditSummary block tax-credit-decision fails with 404 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new NotFoundException("controlled explosion"))
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-decision" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return taxCreditSummary block when submission state returns a non 200 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/tax-credits/submission/state/enabled" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummary, TestData.submissionStateOff, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch)(versionRequest)
    }

    "return empty tax-credit-summary response when retrieval of tax-summary returns non 200 response and push-registration executed successfully" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-summary/2017" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummaryEmpty, TestData.taxCreditSummary, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch, 200, """{"token":"123456"}""")(versionRequest)

      pushRegistrationInvokeCount shouldBe 1
    }

    "return empty taxSummary attribute when tax-summary returns non 200 response and empty taxCreditSummary when retrieval of tax-credit-summary returns non 200 response" in new TestGenericController {
      override val statusCode: Option[Int] = None
      override val exception:Option[Exception]=Some(new BadRequestException("controlled explosion"))
      override val mapping:Map[String, Boolean] = servicesSuccessMap ++
        Map(
          "/income/CS700100A/tax-summary/2017" -> false,
          "/income/CS700100A/tax-credits/tax-credits-summary" -> false
        )

      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummaryEmpty, TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch, 200, """{"token":"123456"}""")(versionRequest)

      pushRegistrationInvokeCount shouldBe 1
    }

    "return taxCreditSummary empty JSON when the tax-credit-summary service returns a non 200 response + not invoke PushReg when payload is empty" in new TestGenericController {
      override val statusCode : Option[Int] = None
      override val exception :Option[Exception] = Some(new BadRequestException("controlled explosion"))
      override val mapping :Map[String, Boolean] = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-summary" -> false)
      override lazy val test_id: String = s"test_id_testOrchestrationDecisionFailure_$time"
      override val taxSummaryData: JsValue = TestData.taxSummaryData()

      val jsonMatch = Seq(TestData.taxSummary(), TestData.taxCreditSummaryEmpty, TestData.submissionStateOn, TestData.statusComplete).foldLeft(Json.obj())((b, a) => b ++ a)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-test_id_testOrchestrationDecisionFailure_$time", Nino("CS700100A"),
        jsonMatch, 200, "{}")(versionRequest)

      pushRegistrationInvokeCount shouldBe 0
    }

    "return throttle status response when throttle limit has been hit" in new ThrottleLimit {
      val result = performOrchestrate("{}", controller, controller.testSessionId, nino)
      status(result) shouldBe 429
      jsonBodyOf(result) shouldBe TestData.statusThrottle
    }

    "returns a success response from version-check generic service" in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "GenericSuccess"
      override val statusCode: Option[Int] = Option(200)
      override val mapping: Map[String, Boolean] = Map("/profile/native-app/version-check" -> true)
      override val exception: Option[Exception] = None
      override val response: JsValue = Json.parse(findResource(s"/resources/generic/version-check.json").get)
      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)
      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "returns a success response from deskpro-feedback generic service"  in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "GenericTokenSuccess"
      override val statusCode: Option[Int] = Option(200)

      override lazy val exception: Option[Exception] = None

      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false, TestData.responseTicket)))

      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-response.json").get)

      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
          "Accept" -> "application/vnd.hmrc.1.0+json",
          "Authorization" -> "Some Header"
        ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "returns a failure response from deskpro-feedback generic service" in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "GenericTokenFailure"
      override val statusCode: Option[Int] = Option(200)

      override lazy val exception: Option[Exception] = None

      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(true, TestData.responseTicket)))

      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-failure-response.json").get)

      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
          "Accept" -> "application/vnd.hmrc.1.0+json",
          "Authorization" -> "Some Header"
        ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "returns multiple failure responses from deskpro-feedback generic service" in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "GenericTokenMultipleFailure"
      override val statusCode: Option[Int] = Option(200)

      override lazy val exception: Option[Exception] = None

      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(
        Seq(GenericServiceResponse(true, TestData.responseTicket), GenericServiceResponse(true, TestData.responseTicket)))

      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-multiple-failure-response.json").get)

      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-multiple-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
          "Accept" -> "application/vnd.hmrc.1.0+json",
          "Authorization" -> "Some Header"
        ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "returns mixture of failure and success responses from deskpro-feedback generic service" in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "GenericTokenMultipleSuccessAndFailure"
      override val statusCode: Option[Int] = Option(200)
      override lazy val exception: Option[Exception] = None

      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(
        Seq(GenericServiceResponse(true, TestData.responseTicket), GenericServiceResponse(false, TestData.responseTicket)))
      val expectedResponse = """{"OrchestrationResponse":{"response":[{"serviceName":"deskpro-feedback","failure":true},{"serviceName":"deskpro-feedback","failure":true}]},"status":{"code":"complete"}}"""

      override val response: JsValue = Json.parse(findResource(s"/resources/generic/feedback-success-failure-response.json").get)

      val request: JsValue = Json.parse(findResource(s"/resources/generic/feedback-multiple-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
          "Accept" -> "application/vnd.hmrc.1.0+json",
          "Authorization" -> "Some Header"
        ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "returns a success response from push-notification-get-message generic service" in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "push-push-notification-get-message-success"
      override val statusCode: Option[Int] = Option(200)
      override val mapping: Map[String, Boolean] = Map("/messages/c59e6746-9cd8-454f-a4fd-c5dc42db7d99" -> true)
      override val exception: Option[Exception] = None
      override lazy val response: JsValue = Json.parse(findResource(s"/resources/generic/push-notification-get-message-response.json").get)
      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false,
        (response \\ "responseData").head)))

      val request: JsValue = Json.parse(findResource("/resources/generic/push-notification-get-message-request.json").get)
      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "returns a success response from push-notification-get-current-messages generic service" in new TestGenericOrchestrationController with FileResource {
      override lazy val test_id: String = "push-notification-get-current-messages-success"
      override val statusCode: Option[Int] = Option(200)
      override val mapping: Map[String, Boolean] = Map("/messages/current" -> true)
      override val exception: Option[Exception] = None
      override lazy val response: JsValue = Json.parse(findResource(s"/resources/generic/push-notification-get-current-message-response.json").get)
      override lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false,
        (response \\ "responseData").head)))

      val request: JsValue = Json.parse(findResource("/resources/generic/push-notification-get-current-message-request.json").get)
      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      invokeOrchestrateAndPollForResult(controller, s"async_native-apps-api-id-$test_id", Nino("CS700100A"), response , 200, Json.stringify(request))(fakeRequest)
    }

    "Simulating concurrent http requests through the async framework " should {

      "successfully process all concurrent requests and once all tasks are complete, verify the throttle value is 0" in {

        def createController(counter: Int) = {
          new TestGenericController {
            override val time = counter.toLong
            override lazy val test_id = s"test_id_concurrent_$counter"
            override val exception: Option[Exception] = None
            override val statusCode: Option[Int] = None
            override val mapping: Map[String, Boolean] = servicesSuccessMap
            override val taxSummaryData: JsValue = TestData.taxSummaryData(Some(test_id))
          }
        }

        excuteParallelAsyncTasks(createController, "async_native-apps-api-id-test_id_concurrent")
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
            invokeOrchestrateAndPollForResult(asyncTestRequest.controller, task_id, Nino("CS700100A"),
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

  "orchestrate live controller authentication " should {

    "return unauthorized when authority record does not contain a NINO" in new AuthWithoutNino {
      testNoNINO(await(controller.orchestrate(nino)(emptyRequestWithHeader)))
    }

    "return 401 result with json status detailing low CL on authority" in new AuthWithLowCL {
      testLowCL(await(controller.orchestrate(nino)(emptyRequestWithHeader)))
    }

    "return status code 406 when the headers are invalid" in new Success {
      val result = await(controller.orchestrate(nino)(emptyRequest))
      status(result) shouldBe 406
    }
  }

  "sandbox controller " should {

    "return the PreFlightCheckResponse response from a static resource" in new SandboxSuccess {
      val result = await(controller.preFlightCheck(Some(journeyId))(requestWithAuthSession.withBody(versionBody)))
      status(result) shouldBe 200
      val journeyIdRetrieve: String = (contentAsJson(result) \ "accounts" \ "journeyId").as[String]
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, journeyIdRetrieve, "someCredId", "Individual")))
    }

    "return startup response from a static resource" in new SandboxSuccess {
      val result = await(controller.orchestrate(nino)(requestWithAuthSession.withJsonBody(Json.parse("""{}"""))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe TestData.sandboxStartupResponse
    }

    "return poll response from a static resource" in new SandboxSuccess {
      val currentTime = (new LocalDate()).toDateTimeAtStartOfDay
      val result = await(controller.poll(nino)(requestWithAuthSession))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(TestData.sandboxPollResponse
        .replaceAll("date1", currentTime.plusWeeks(1).getMillis.toString)
        .replaceAll("date2", currentTime.plusWeeks(2).getMillis.toString)
        .replaceAll("date3", currentTime.plusWeeks(3).getMillis.toString)
      )
      result.header.headers.get("Cache-Control") shouldBe Some("max-age=14400")
    }
  }

  val token = "Bearer 123456789"
  def performOrchestrate(inputBody: String, controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino,
                         journeyId: Option[String] = None) = {
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

    await(controller.orchestrate(nino, journeyId).apply(requestWithSessionKeyAndId))
  }

  def invokeOrchestrateAndPollForResult(controller: NativeAppsOrchestrationController, testSessionId: String, nino: Nino, response: JsValue, resultCode: Int = 200,
                                        inputBody: String = """{"token":"123456"}""", cacheHeader : Option[String] = Some("max-age=14400"),
                                         overrideNino:Option[Nino]=None)(implicit request: Request[_]) = {
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
    val startupResponse = performOrchestrate(inputBody, controller, testSessionId, nino)
    status(startupResponse) shouldBe 200

    // Verify the Id within the session matches the expected test Id.
    val session = startupResponse.session.get(controller.AsyncMVCSessionId)

    val jsonSession = Json.parse(session.get).as[AsyncMvcSession]
    jsonSession.id shouldBe testSessionId

    // Poll for the result.
    eventually(Timeout(Span(10, Seconds))) {
      val pollResponse: Result = await(controller.poll(overrideNino.getOrElse(nino))(requestWithSessionKeyAndIdNoBody))

      status(pollResponse) shouldBe resultCode
      if (resultCode!=401) {
        jsonBodyOf(pollResponse) shouldBe response

        pollResponse.header.headers.get("Cache-Control") shouldBe cacheHeader
      }
    }
  }

}
