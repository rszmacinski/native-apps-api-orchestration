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
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, OrchestrationService, SandboxOrchestrationService}
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
  val testAccount = Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false,"102030394AAA")

  val requestWithAuthSession = FakeRequest().withSession(
    "AuthToken" -> "Some Header"
  ).withHeaders(
      "Accept" -> "application/vnd.hmrc.1.0+json",
      "Authorization" -> "Some Header"
    )

  val servicesSuccessMap = Map(
    "/profile/native-app/version-check" -> true,
    "/income/CS700100A/tax-summary/2016" -> true,
    "/income/tax-credits/submission/state/enabled" -> true,
    "/income/CS700100A/tax-credits/tax-credits-summary" -> true,
    "/income/CS700100A/tax-credits/tax-credits-decision" -> true)

  val maxAgeForPollSuccess = 14400

  lazy val testGenericConnector  = new TestServiceFailureGenericConnector(servicesSuccessMap ,true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val authConnector = new TestAuthConnector(Some(nino))
  lazy val testOrchestrationService = new TestOrchestrationService(testGenericConnector, authConnector)

  lazy val decisionFailureException:Option[Exception] = None
  val decisionFailure = servicesSuccessMap ++ Map("/income/CS700100A/tax-credits/tax-credits-decision" -> false)
  lazy val testGenericConnectorDecisionFailure  = new TestServiceFailureGenericConnector(decisionFailure,
    true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken, None, decisionFailureException)

  lazy val testOrchestrationDecisionFailure = new TestOrchestrationService(testGenericConnectorDecisionFailure, authConnector)

  def testGenericConnectorFailure(mapping:Map[String, Boolean], exceptionControl:Boolean, httpResponseCode:Option[Int], exception:Option[Exception], taxSummaryData:JsValue): TestServiceFailureGenericConnector =
    new TestServiceFailureGenericConnector(mapping ,exceptionControl, testAccount, TestData.testPushReg, TestData.testPreferences, taxSummaryData,
      TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken, httpResponseCode, exception)

  def testOrchestrationDecisionFailure(mapping:Map[String, Boolean], exceptionControl:Boolean, httpResponseCode:Option[Int], exception:Option[Exception], taxSummaryData:JsValue): (TestOrchestrationService, TestServiceFailureGenericConnector) = {
    val testConnector: TestServiceFailureGenericConnector = testGenericConnectorFailure(mapping, exceptionControl, httpResponseCode, exception, taxSummaryData)
    (new TestOrchestrationService(testConnector, authConnector), testConnector)
  }

  lazy val testAccess = new TestAccessCheck(authConnector)
  lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)
  lazy val testAccessControlOff = AccountAccessControlCheckAccessOff

  lazy val servicesStateFailMap = servicesSuccessMap ++ Map("/income/tax-credits/submission/state/enabled" -> false)
  lazy val testGenericConnectorStateFAILURE = new TestServiceFailureGenericConnector(servicesStateFailMap ,true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceStateFAILURE = new TestOrchestrationService(testGenericConnectorStateFAILURE, authConnector)

  lazy val testGenericConnectorAuthFAILURE = new TestServiceFailureGenericConnector(servicesSuccessMap ,true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceAuthFAILURE = new TestOrchestrationService(testGenericConnectorAuthFAILURE, authConnector)

  lazy val testGenericConnectorRenewalSubmissionNotActive = new TestServiceFailureGenericConnector(servicesSuccessMap ,true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testStateNotInSubmission, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
  lazy val testOrchestrationServiceRenewalSubmissionNotActive = new TestOrchestrationService(testGenericConnectorRenewalSubmissionNotActive, authConnector)

  lazy val testGenericConnectorExclusionTrue = new TestServiceFailureGenericConnector(servicesSuccessMap ,true, testAccount, TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecisionNotDisplay, TestData.testAuthToken)
  lazy val testOrchestrationServiceExclusionTrue = new TestOrchestrationService(testGenericConnectorExclusionTrue, authConnector)

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
    val testSessionId = "Success"
    override val actorName = s"async_native-apps-api-actor_" + testSessionId
    override def id = "sandbox-async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "Success Orchestration Controller"
    override val repository: AsyncRepository = asyncRepository

    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait FailurePreFlight extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val testSessionId = "Failure"

    override val actorName = s"async_native-apps-api-actor_" + testSessionId
    override def id = "sandbox-async_native-apps-api-id_failure_preflight"

    lazy val testGenericConnector  = new TestServiceFailureGenericConnector(
      servicesSuccessMap ++ Map("/profile/native-app/version-check" -> false) ,true, testAccount,
      TestData.testPushReg, TestData.testPreferences, TestData.taxSummaryData(), TestData.testState,
      TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
    lazy val authConnector = new TestAuthConnector(Some(nino))
    lazy val testOrchestrationService = new TestOrchestrationService(testGenericConnector, authConnector)

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "Success Orchestration Controller"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait SessionChecker extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val testSessionId="SessionChecker"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationServiceAuthFAILURE
    override val app: String = "SessionChecker"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }

}

trait RenewalSubmissionNotActive extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val testSessionId="RenewalSubmissionNotActive"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationServiceRenewalSubmissionNotActive
    override val app: String = "RenewalSubmissionNotActive"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait ExclusionTrue extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val testSessionId = "ExclusionTrue"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationServiceExclusionTrue
    override val app: String = "ExclusionTrue"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait ThrottleLimit extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val testSessionId = "ThrottleLimit"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    override val accessControl : AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff : AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service : OrchestrationService = testOrchestrationServiceExclusionTrue
    override val app : String = "ThrottleLimit"
    override val repository : AsyncRepository = asyncRepository
    override def checkSecurity : Boolean = true
    override def throttleLimit : Long = 0
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait SecurityAsyncSetup extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val testSessionId = "SecurityAsyncSetup"
    override def buildUniqueId() = testSessionId

    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"

    lazy val authConnector = new TestAuthConnector(Some(Nino("CS722100B")))
    lazy val testAccess = new TestAccessCheck(authConnector)
    lazy val compositeAuthAction = new TestAccountAccessControlWithAccept(testAccess)

    override val accessControl: AccountAccessControlWithHeaderCheck = compositeAuthAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationServiceExclusionTrue
    override val app: String = "SecurityAsyncSetup"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait ExclusionException extends Setup {

  val testId:String

  lazy val controller = new NativeAppsOrchestrationController {
    lazy val testSessionId=testId // "ExclusionException"
    override def buildUniqueId() = testSessionId
    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationDecisionFailure
    override val app: String = "Fail with Retry Success Orchestration Controller"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait TestGenericController extends Setup {

  val time = System.currentTimeMillis()
  val test_id:String
  val exception:Option[Exception]
  val statusCode:Option[Int]
  val mapping:Map[String, Boolean]
  val taxSummaryData:JsValue

  val controller = new NativeAppsOrchestrationController {
    lazy val testSessionId=test_id
    override def buildUniqueId() = testSessionId
    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    lazy val testServiceAndConnector: (TestOrchestrationService, TestServiceFailureGenericConnector) = testOrchestrationDecisionFailure(mapping, exceptionControl = false, statusCode, exception, taxSummaryData)
    override lazy val service: OrchestrationService = testServiceAndConnector._1
    override val app: String = "Test Generic Controller"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }

  def pushRegistrationInvokeCount = controller.testServiceAndConnector._2.countPushRegistration
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
  var grantAccountCount = 0

  override val serviceUrl: String = "someUrl"

  override def serviceConfidenceLevel: ConfidenceLevel = throw new Exception("Must not be invoked")

  override def http: HttpGet = throw new Exception("Must not be invoked")

  override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.successful(Accounts(nino, None, routeToIV = false, routeToTwoFactor = false, "102030394AAA"))

  override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = {
    grantAccountCount = grantAccountCount + 1
    Future.successful(Authority(nino.getOrElse(Nino("CS700100A")), ConfidenceLevel.L200, "Some Auth-Id"))
  }
}

class TestServiceFailureGenericConnector(pathFailMap: Map[String, Boolean], upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue,
                                  preferences: JsValue, taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue,
                                  taxCreditDecision: JsValue, auth: JsValue, httpResponseCode:Option[Int]=None, exception:Option[Exception]=None) extends GenericConnector {

  var countPushRegistration = 0

  override def http: HttpPost with HttpGet = WSHttp

  override def doPost(json:JsValue, host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "/profile/native-app/version-check" =>
        passFail(Json.parse(s"""{"upgrade":$upgradeRequired}"""), isSuccess(path))

      case "/push/registration" =>
        countPushRegistration = countPushRegistration + 1
        Future.successful(JsNull)
    }
  }

  private def passFail(value: JsValue, success: Boolean): Future[JsValue] = {
    if (!success) {
      val result = exception.fold(new Exception("Controlled explosion!")){ ex => ex}
      Future.failed(result)
    } else Future.successful(Json.toJson(value))
  }

  def isSuccess(key: String): Boolean = pathFailMap.getOrElse(key,false)

  override def doGet(host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = {
    val result = path match {
      case "/income/CS700100A/tax-summary/2016" => passFail(taxSummary, isSuccess(path))
      case "/income/tax-credits/submission/state/enabled" => passFail(state, isSuccess(path))
      case "/income/CS700100A/tax-credits/tax-credits-summary" => passFail(taxCreditSummary, isSuccess(path))
      case "/income/CS700100A/tax-credits/tax-credits-decision" =>
          passFail(taxCreditDecision, isSuccess(path))

        case _ => Future.failed(new Exception(s"Test Scenario Error! The path $path is not defined!"))
      }
    result
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
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    lazy val testCustomerProfileGenericConnector = new TestServiceFailureGenericConnector(servicesSuccessMap ,true, testAccount, TestData.testPushReg, TestData.testPreferences, JsNull, TestData.testState, TestData.taxCreditSummaryData, TestData.testTaxCreditDecision, TestData.testAuthToken)
    override val service: OrchestrationService = new TestOrchestrationService(testCustomerProfileGenericConnector, authConnector)
    override val app: String = "AuthWithoutNino Native Apps Orchestration"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
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
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "AuthWithoutNino Native Apps Orchestration"
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
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
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
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
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff
    override val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = true
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
  }
}

trait SandboxSuccess extends Setup {
  val controller = new SandboxOrchestrationController {
    val testSessionId="SandboxSuccess"
    override val actorName = s"async_native-apps-api-actor_"+testSessionId
    override def id = "async_native-apps-api-id"
    override val app = "Sandbox Native Apps Orchestration"
    override val service: OrchestrationService = SandboxOrchestrationService
    override val accessControl = AccountAccessControlCheckAccessOff
    override lazy val repository: AsyncRepository = asyncRepository
    override def checkSecurity: Boolean = false
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess
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
