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

import java.util.UUID

import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.{Updated, DatabaseUpdate}
import uk.gov.hmrc.msasync.repository.{TaskCachePersist, AsyncRepository}
import uk.gov.hmrc.ngc.orchestration.config.{MicroserviceAuditConnector, WSHttp}
import uk.gov.hmrc.ngc.orchestration.connectors._
import uk.gov.hmrc.ngc.orchestration.controllers.action.{AccountAccessControlCheckAccessOff, AccountAccessControl, AccountAccessControlWithHeaderCheck}
import uk.gov.hmrc.ngc.orchestration.domain.Accounts
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, Mandatory, OrchestrationService, SandboxOrchestrationService}
import uk.gov.hmrc.play.asyncmvc.model.TaskCache
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

trait Setup {
  implicit val hc = HeaderCarrier()

  val journeyId = UUID.randomUUID().toString
  val emptyRequest = FakeRequest()
  val emptyRequestWithHeader = FakeRequest().withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  val nino = Nino("CS700100A")
  val testAccount = Accounts(Some(nino), None, false, false,"102030394AAA")

  val requestWithAuthSession = FakeRequest().withSession(
    "AuthToken" -> "Some Header"
  ).withHeaders(
      "Accept" -> "application/vnd.hmrc.1.0+json",
      "Authorization" -> "Some Header"
    )

  lazy val testGenericConnector = new TestGenericConnector(true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val authConnector = new TestAuthConnector(Some(nino))
  lazy val testOrchestrationService = new TestOrchestrationService(testGenericConnector, authConnector)

  lazy val testGenericConnectorFAILURE = new TestFailureGenericConnector(true, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceFAILURE = new TestOrchestrationService(testGenericConnectorFAILURE, authConnector)
  lazy val testGenericConnectorRETRY = new TestRetryGenericConnector(false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceRETRY = new TestOrchestrationService(testGenericConnectorRETRY, authConnector)

  lazy val testAccess = new TestAccessCheck(authConnector)
  lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val servicesSuccessMap = Map(
    "/profile/native-app/version-check" -> true,
    "/profile/preferences" -> true,
    "/income/CS700100A/tax-summary/2016" -> true,
    "/income/tax-credits/submission/state" -> true,
    "/income/CS700100A/tax-credits/tax-credits-summary" -> true,
    "/income/CS700100A/tax-credits/tax-credits-decision" -> true,
    "/income/CS700100A/tax-credits/999999999999999/auth" -> true)

  val servicesPreferencesFailMap = servicesSuccessMap ++ Map("/profile/preferences" -> false)
  lazy val testGenericConnectorPreferencesFAILURE = new TestServiceFailureGenericConnector(servicesPreferencesFailMap ,false, false, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServicePreferencesFAILURE = new TestOrchestrationService(testGenericConnectorPreferencesFAILURE, authConnector)

  val servicesStateFailMap = servicesSuccessMap ++ Map("/income/tax-credits/submission/state" -> false)
  lazy val testGenericConnectorStateFAILURE = new TestServiceFailureGenericConnector(servicesStateFailMap ,false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceStateFAILURE = new TestOrchestrationService(testGenericConnectorStateFAILURE, authConnector)

  val servicesAuthFailMap = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/999999999999999/auth" -> false)
  lazy val testGenericConnectorAuthFAILURE = new TestServiceFailureGenericConnector(servicesAuthFailMap ,false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceAuthFAILURE = new TestOrchestrationService(testGenericConnectorAuthFAILURE, authConnector)

  lazy val testGenericConnectorRenewalSubmissionNotActive = new TestServiceFailureGenericConnector(servicesSuccessMap ,false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testStateNotInSubmission, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceRenewalSubmissionNotActive = new TestOrchestrationService(testGenericConnectorRenewalSubmissionNotActive, authConnector)

  lazy val testGenericConnectorRenewalShutterActive = new TestServiceFailureGenericConnector(servicesSuccessMap ,false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testStateSubmissionShutterActive, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceRenewalShutterActive = new TestOrchestrationService(testGenericConnectorRenewalShutterActive, authConnector)

  lazy val testGenericConnectorExclusionTrue = new TestServiceFailureGenericConnector(servicesSuccessMap ,false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecisionNotDisplay, TestData.testAuthToken)
  lazy val testOrchestrationServiceExclusionTrue = new TestOrchestrationService(testGenericConnectorExclusionTrue, authConnector)

  val servicesOptionalDataFailMap = servicesSuccessMap ++ Map(
                                       "/profile/preferences" -> false,
                                       "/income/tax-credits/submission/state" -> false,
                                       "/income/CS700100A/tax-credits/tax-credits-summary" -> false,
                                       "/income/CS700100A/tax-credits/tax-credits-decision" -> false,
                                       "/income/CS700100A/tax-credits/999999999999999/auth" -> false)

  lazy val testGenericConnectorOptionalDataFAILURE = new TestServiceFailureGenericConnector(servicesOptionalDataFailMap ,false, true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceOptionalDataFAILURE = new TestOrchestrationService(testGenericConnectorOptionalDataFAILURE, authConnector)

  val versionBody = Json.parse("""{"os":"android", "version":"1.0.1"}""")
  val versionRequest = FakeRequest().withBody(versionBody)
    .withHeaders("Content-Type" -> "application/json", "Accept" -> "application/vnd.hmrc.1.0+json")

  val token = Json.parse("""{"token":"123456"}""")

  val pushRegRequest = FakeRequest().withBody(token)
    .withHeaders("Content-Type" -> "application/json", "Accept" -> "application/vnd.hmrc.1.0+json")

  val asyncRepository = new AsyncRepository {

    var cache:Option[TaskCache] = None

    override def findByTaskId(id: String): Future[Option[TaskCachePersist]] = {
      val update = TaskCachePersist(BSONObjectID.generate, cache.get)
      Future.successful(Some(update))
    }

    override def removeById(id: String): Future[Unit] = Future.successful({})

    override def save(expectation: TaskCache, expire: Long): Future[DatabaseUpdate[TaskCachePersist]] = {
      cache = Some(expectation)
      val update = TaskCachePersist(BSONObjectID.generate, expectation)
      Future.successful(DatabaseUpdate(null, Updated(update,update)))
    }
  }

}

trait Success extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="Success"

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "sandbox-async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "Success Orchestration Controller"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait Failure extends Setup {

  val controller = new NativeAppsOrchestrationController {

    val testSessionId="Failure"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceFAILURE
    override val app: String = "Failure"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
  def invokeCount = testGenericConnectorFAILURE.counter
}

trait PreferenceFailure extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="PreferenceFailure"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServicePreferencesFAILURE
    override val app: String = "PreferenceFailure"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait AuthenticateRenewal extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="AuthenticateRenewal"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceAuthFAILURE
    override val app: String = "AuthenticateRenewal"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait SessionChecker extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="SessionChecker"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceAuthFAILURE
    override val app: String = "SessionChecker"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }

}

trait RenewalSubmissionNotActive extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="RenewalSubmissionNotActive"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceRenewalSubmissionNotActive
    override val app: String = "RenewalSubmissionNotActive"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait RenewalSubmissionShutterActive extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="RenewalSubmissionShutterActive"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceRenewalShutterActive
    override val app: String = "RenewalSubmissionShutterActive"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait ExclusionTrue extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="ExclusionTrue"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceExclusionTrue
    override val app: String = "ExclusionTrue"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait SecurityAsyncSetup extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="SecurityAsyncSetup"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"


    lazy val authConnector = new TestAuthConnector(Some(Nino("CS722100B")))
    lazy val testAccess = new TestAccessCheck(authConnector)
    lazy val compositeAuthAction = new TestAccountAccessControlWithAccept(testAccess)

    override val accessControl: AccountAccessControlWithHeaderCheck = compositeAuthAction
    override val service: OrchestrationService = testOrchestrationServiceExclusionTrue
    override val app: String = "SecurityAsyncSetup"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
  }
}

trait OptionalDataFailure extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="OptionalDataFailure"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceOptionalDataFAILURE
    override val app: String = "OptionalDataFailure"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

    def pushRegistrationInvokeCount = testGenericConnectorOptionalDataFAILURE.countPushRegistration
  }
}

trait OptionalFirebaseToken extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="OptionalFirebaseToken"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceOptionalDataFAILURE
    override val app: String = "OptionalFirebaseToken"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

    def pushRegistrationInvokeCount = testGenericConnectorOptionalDataFAILURE.countPushRegistration
  }
}


trait FailWithRetrySuccess extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="FailWithRetrySuccess"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"


    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationServiceRETRY
    override val app: String = "Fail with Retry Success Orchestration Controller"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
  }

  def invokeCount = testGenericConnectorRETRY.counter
}

class TestOrchestrationService(testGenericConnector: GenericConnector, testAuthConnector: AuthConnector) extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val genericConnector: GenericConnector = testGenericConnector
  override val authConnector: AuthConnector = testAuthConnector
}

class TestAccountAccessControlWithAccept(testAccessCheck: AccountAccessControl) extends AccountAccessControlWithHeaderCheck {
  override val accessControl: AccountAccessControl = testAccessCheck
}

class TestAccessCheck(testAuthConnector: TestAuthConnector) extends AccountAccessControl {
  override val authConnector: AuthConnector = testAuthConnector
}

class TestAuthConnector(nino: Option[Nino]) extends AuthConnector {
  override val serviceUrl: String = "someUrl"

  override def serviceConfidenceLevel: ConfidenceLevel = ???

  override def http: HttpGet = ???

  override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future(Accounts(nino, None, false, false, "102030394AAA"))

  override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = Future(Authority(nino.getOrElse(Nino("CS700100A")), ConfidenceLevel.L200, "Some Auth-Id"))
}

class TestGenericConnector(upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue, preferences: JsValue,
                           taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue, taxCreditDecision: JsValue,
                           auth: JsValue) extends GenericConnector {
  var countPushRegistration = 0

  override def http: HttpPost with HttpGet = WSHttp

  override def doPost(json:JsValue, host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "/profile/native-app/version-check" => Future.successful(Json.parse(s"""{"upgrade":$upgradeRequired}"""))

      case "/push/registration" => {
        countPushRegistration = countPushRegistration + 1
        Future.successful(JsNull)
      }
    }
  }

  override def doGet(host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "/profile/preferences" => Future.successful(preferences)
      case "/income/CS700100A/tax-summary/2016" => Future.successful(taxSummary)
      case "/income/tax-credits/submission/state" => Future.successful(state)
      case "/income/CS700100A/tax-credits/tax-credits-summary" => Future.successful(taxCreditSummary)
      case "/income/CS700100A/tax-credits/tax-credits-decision" => Future.successful(taxCreditDecision)
    }
  }

  override def doGetRaw(host:String, path:String, port:Int, hc: HeaderCarrier): Future[HttpResponse] = {
    path match {
      case "/income/CS700100A/tax-credits/999999999999999/auth" => Future.successful(HttpResponse(200,Option(auth),Map(), Option("")))
    }
  }
}

class TestRetryGenericConnector(exceptionControl: Boolean, upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue,
                                preferences: JsValue, taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue,
                                taxCreditDecision: JsValue, auth: JsValue) extends TestGenericConnector(upgradeRequired: Boolean,
                                accounts: Accounts, pushRegResult: JsValue, preferences: JsValue, taxSummary: JsValue, state: JsValue,
                                taxCreditSummary: JsValue, taxCreditDecision: JsValue, auth: JsValue) {

  var counter=0

  override def doGet(host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "/profile/preferences" => Future.successful(preferences)
      case "/income/CS700100A/tax-summary/2016" => Future.successful(taxSummary)
      case "/income/tax-credits/submission/state" => Future.successful(state)
      case "/income/CS700100A/tax-credits/tax-credits-summary" => Future.successful(taxCreditSummary)
      case "/income/CS700100A/tax-credits/tax-credits-decision" => {
        val res = if (exceptionControl==true) Future.failed(new ServiceUnavailableException("FAILED"))
        else {
          if (counter == 0) Future.failed(new ServiceUnavailableException("FAILED"))
          else {
            Future.successful(taxCreditDecision)
          }
        }
        counter = counter + 1
        res
      }
    }
  }
}

class TestFailureGenericConnector(exceptionControl: Boolean, upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue,
                                   preferences: JsValue, taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue,
                                   taxCreditDecision: JsValue, auth: JsValue) extends TestGenericConnector(upgradeRequired: Boolean,
  accounts: Accounts, pushRegResult: JsValue, preferences: JsValue, taxSummary: JsValue, state: JsValue,
  taxCreditSummary: JsValue, taxCreditDecision: JsValue, auth: JsValue) {

  var counter=0

  override def doGet(host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "/profile/preferences" => Future.successful(preferences)
      case "/income/CS700100A/tax-summary/2016" =>
        if(exceptionControl == true) Future.failed(Mandatory()) else Future.successful(taxSummary)
      case "/income/tax-credits/submission/state" => Future.successful(state)
      case "/income/CS700100A/tax-credits/tax-credits-summary" => Future.successful(taxCreditSummary)
      case "/income/CS700100A/tax-credits/tax-credits-decision" => Future.successful(taxCreditDecision)
    }
  }
}

class TestServiceFailureGenericConnector(pathFailMap: Map[String, Boolean], exceptionControl: Boolean, upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue,
                                  preferences: JsValue, taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue,
                                  taxCreditDecision: JsValue, auth: JsValue) extends TestGenericConnector(upgradeRequired: Boolean,
  accounts: Accounts, pushRegResult: JsValue, preferences: JsValue, taxSummary: JsValue, state: JsValue,
  taxCreditSummary: JsValue, taxCreditDecision: JsValue, auth: JsValue) {

  private def passFail(value: JsValue, success: Boolean): Future[JsValue] = {
    if (!success) Future.failed(new Exception()) else Future.successful(Json.toJson(value))
  }

  override def doGet(host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = {

    def isSuccess(key: String): Boolean = pathFailMap.get(key).getOrElse(false)

    val result = path match {
        case "/profile/preferences" => passFail(preferences, isSuccess(path))
        case "/income/CS700100A/tax-summary/2016" => {
          if (exceptionControl) Future.failed(Mandatory()) else Future.successful(taxSummary)
        }
        case "/income/tax-credits/submission/state" => passFail(state, isSuccess(path))
        case "/income/CS700100A/tax-credits/tax-credits-summary" => passFail(taxCreditSummary, isSuccess(path))
        case "/income/CS700100A/tax-credits/tax-credits-decision" => passFail(taxCreditDecision, isSuccess(path))
        case _ => Future.failed(new Exception("Test Scenario Error"))
      }
    result
  }

  override def doGetRaw(host:String, path:String, port:Int, hc: HeaderCarrier): Future[HttpResponse] = {
    def isSuccess(key: String): Boolean = pathFailMap.get(key).getOrElse(false)

    path match {
      case "/income/CS700100A/tax-credits/999999999999999/auth" => {
        val res = if (!isSuccess(path)) 401 else 200
        Future.successful(HttpResponse(res, Option(auth), Map.empty, Option("")))
      }
    }
  }
}

trait AuthWithoutTaxSummary extends Setup with AuthorityTest {

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new NinoNotFoundOnAccount("The user must have a National Insurance Number")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.failed(exception)
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    val testCustomerProfileGenericConnector = new TestGenericConnector(true, testAccount, TestData.testPushReg, TestData.testPreferences, JsNull, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
    override val service: OrchestrationService = new TestOrchestrationService(testCustomerProfileGenericConnector, authConnector)
    override val app: String = "AuthWithoutNino Native Apps Orchestration"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
  }
}

trait AuthWithoutNino extends Setup with AuthorityTest {

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new NinoNotFoundOnAccount("The user must have a National Insurance Number")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.failed(exception)
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "AuthWithoutNino Native Apps Orchestration"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
  }
}

trait AuthWithLowCL extends Setup with AuthorityTest {
  val routeToIv=true
  val routeToTwoFactor=false

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new AccountWithLowCL("Forbidden to access since low CL")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.successful(Accounts(Some(nino), None, routeToIv, routeToTwoFactor, "102030394AAA"))
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    val app = "AuthWithLowCL Native Apps Orchestration"
    override val service: LiveOrchestrationService = new TestOrchestrationService(testGenericConnector,authConnector)
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true

  }
}

trait AuthWithWeakCreds extends Setup with AuthorityTest {
  val routeToIv=false
  val routeToTwoFactor=true

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new AccountWithWeakCredStrength("Forbidden to access since weak cred strength")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.successful(Accounts(Some(nino), None, routeToIv, routeToTwoFactor, "102030394AAA"))
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    val app = "AuthWithWeakCreds Native Apps Orchestration"
    override val service: LiveOrchestrationService = new TestOrchestrationService(testGenericConnector,authConnector)
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
  }
}

trait SandboxSuccess extends Setup {
  val controller = new NativeAppsOrchestrationController {

    val testSessionId="SandboxSuccess"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"


    val app = "Sandbox Native Apps Orchestration"
    override val service: OrchestrationService = SandboxOrchestrationService
    override val accessControl: AccountAccessControlWithHeaderCheck = AccountAccessControlCheckAccessOff
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = false

  }
}

trait AuthorityTest extends UnitSpec {
  self: Setup =>

  def testNoNINO(func: => play.api.mvc.Result) = {
    val result: play.api.mvc.Result = func

    status(result) shouldBe 401
    contentAsJson(result) shouldBe TestData.noNinoOnAccount
  }

  def testLowCL(func: => play.api.mvc.Result) = {
    val result: play.api.mvc.Result = func

    status(result) shouldBe 401
    contentAsJson(result) shouldBe TestData.lowCL
  }

  def testWeakCredStrength(func: => play.api.mvc.Result) = {
    val result: play.api.mvc.Result = func

    status(result) shouldBe 401
    contentAsJson(result) shouldBe TestData.weakCredStrength
  }
}
