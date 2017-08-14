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

import play.api.libs.json.{JsValue, Json}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.{DatabaseUpdate, Updated}
import uk.gov.hmrc.msasync.repository.{AsyncRepository, TaskCachePersist}
import uk.gov.hmrc.ngc.orchestration.config.{MicroserviceAuditConnector, WSHttp}
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.controllers.action.{AccountAccessControlCheckOff, AccountAccessControlWithHeaderCheck}
import uk.gov.hmrc.ngc.orchestration.domain.{OrchestrationRequest, OrchestrationResponse}
import uk.gov.hmrc.ngc.orchestration.executors._
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, OrchestrationService}
import uk.gov.hmrc.play.asyncmvc.model.TaskCache
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.{Audit, AuditEvent}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}

import scala.concurrent.{ExecutionContext, Future}

trait AsyncRepositorySetup {

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


trait GenericOrchestrationSetup {

  lazy val maxServiceCalls: Int = ???
  lazy val maxEventCalls: Int = ???

  lazy val testSuccessGenericConnector = new TestGenericOrchestrationConnector(Seq(GenericServiceResponse(false, TestData.upgradeRequired(false))))
  lazy val testVersionCheckExecutor = new TestVersionCheckExecutor(testSuccessGenericConnector)
  lazy val testFeedbackExecutor = new TestFeedbackExecutor(testSuccessGenericConnector)
  lazy val testPushNotificationGetMessageExecutor = new TestPushNotificationGetMessageExecutor(testSuccessGenericConnector)
  lazy val testPushNotificationGetCurrentMessageExecutor = new TestPushNotificationGetCurrentMessageExecutor(testSuccessGenericConnector)
  lazy val testClaimantDetailsServiceExecutor = new TestClaimantDetailsServiceExecutor(testSuccessGenericConnector)
  lazy val testAuditConnector = new TestAuditConnector()
  lazy val testAuditEventExecutor = new TestAuditEventExecutor(new Audit("test-app", testAuditConnector))


  lazy val testEventExecutors = Map(testAuditEventExecutor.executorName -> testAuditEventExecutor)
  lazy val testServiceExecutors = Map(testVersionCheckExecutor.executorName -> testVersionCheckExecutor,
                                      testFeedbackExecutor.executorName -> testFeedbackExecutor,
                                      testPushNotificationGetMessageExecutor.executorName -> testPushNotificationGetMessageExecutor,
                                      testPushNotificationGetCurrentMessageExecutor.executorName -> testPushNotificationGetCurrentMessageExecutor,
                                      testClaimantDetailsServiceExecutor.executorName -> testClaimantDetailsServiceExecutor
                                  )

  lazy val testExecutorFactory = new TestExecutorFactory(testServiceExecutors,testEventExecutors, maxServiceCalls,maxEventCalls)

  val maxAgeForPollSuccess = 14400

  lazy val nino = Nino("CS700100A")
  lazy val authConnector = new TestAuthConnector(Some(nino))
  lazy val testAccess = new TestAccessCheck(authConnector)
  lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)
  lazy val testAccessControlOff = AccountAccessControlCheckOff
  val servicesSuccessMap = Map("/profile/native-app/version-check" -> true)

  def testGenericConnectorFailure(mapping:Map[String, Boolean], httpResponseCode:Option[Int], exception:Option[Exception], response:JsValue): TestServiceGenericConnector =
    new TestServiceGenericConnector(mapping, response, httpResponseCode, exception)

  def testOrchestrationDecisionFailure(mapping:Map[String, Boolean], httpResponseCode:Option[Int], exception:Option[Exception], response:JsValue): (TestGenericOrchestrationService, TestServiceGenericConnector) = {
    val testConnector: TestServiceGenericConnector = testGenericConnectorFailure(mapping, httpResponseCode, exception, response)
    (new TestGenericOrchestrationService(testExecutorFactory, maxServiceCalls, maxEventCalls), testConnector)
  }
}

trait TestGenericOrchestrationController extends GenericOrchestrationSetup with AsyncRepositorySetup {

  val time = System.currentTimeMillis()
  lazy val test_id: String = ???
  val exception: Option[Exception]
  val statusCode: Option[Int]
  val mapping: Map[String, Boolean] = Map("/profile/native-app/version-check" -> true)
  val response: JsValue
  override lazy val maxServiceCalls: Int = 10
  override lazy val maxEventCalls: Int = 10
  override val servicesSuccessMap = mapping

  val controller = new NativeAppsOrchestrationController {

    lazy val testSessionId = test_id
    override def buildUniqueId() = testSessionId

    lazy val testServiceAndConnector: (TestGenericOrchestrationService, TestServiceGenericConnector) = testOrchestrationDecisionFailure(mapping, statusCode, exception, response)
    override val service: OrchestrationService = testServiceAndConnector._1
    override val actorName = s"async_native-apps-api-actor_$testSessionId"
    override def id = "async_native-apps-api-id"

    override val app: String = "Test Generic Orchestration Controller"
    override val repository: AsyncRepository = asyncRepository
    override val auditConnector: AuditConnector = MicroserviceAuditConnector
    override val maxAgeForSuccess: Long = maxAgeForPollSuccess

    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val accessControlOff: AccountAccessControlWithHeaderCheck = testAccessControlOff

    override def checkSecurity: Boolean = true
  }

  def invokeCount = controller.testServiceAndConnector._2.count
}

class TestGenericOrchestrationService(testExecutorFactory: ExecutorFactory, override val maxServiceCalls: Int, override val maxEventCalls: Int) extends LiveOrchestrationService {
  override def buildAndExecute(orchestrationRequest: OrchestrationRequest, nino: String, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[OrchestrationResponse] = testExecutorFactory.buildAndExecute(orchestrationRequest, nino, journeyId)
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val authConnector: AuthConnector = AuthConnector
}

class TestVersionCheckExecutor(testGenericConnector: GenericConnector) extends VersionCheckExecutor {
  override def connector: GenericConnector = testGenericConnector
}

class TestFeedbackExecutor(testGenericConnector: GenericConnector) extends DeskProFeedbackExecutor {
  override def connector: GenericConnector = testGenericConnector
}

class TestAuditEventExecutor(testAudit: Audit) extends AuditEventExecutor {
  override val audit = testAudit
}

class TestPushNotificationGetMessageExecutor(testGenericConnector: GenericConnector) extends PushNotificationGetMessageExecutor {
  override def connector: GenericConnector = testGenericConnector
}

class TestPushNotificationGetCurrentMessageExecutor(testGenericConnector: GenericConnector) extends PushNotificationGetCurrentMessagesExecutor {
  override def connector: GenericConnector = testGenericConnector
}

class TestClaimantDetailsServiceExecutor(testGenericConnector: GenericConnector) extends ClaimantDetailsServiceExecutor {
  override def connector: GenericConnector = testGenericConnector
}

class TestExecutorFactory(override val serviceExecutors: Map[String, ServiceExecutor], override val eventExecutors: Map[String, EventExecutor], maxServiceCallsParam: Int, maxEventCallsParam: Int) extends ExecutorFactory {
  override val maxServiceCalls: Int = maxServiceCallsParam
  override val maxEventCalls: Int = maxEventCallsParam
}

case class GenericServiceResponse(failure:Boolean, data: JsValue)

class TestGenericOrchestrationConnector(response:Seq[GenericServiceResponse]) extends GenericConnector {
  override def http: HttpPost with HttpGet = WSHttp
  var pos=0

  override def doPost(json: JsValue, host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = respond()

  override def doGet(host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = respond()

  def respond(): Future[JsValue] = {
    val testResponse: GenericServiceResponse = response(pos)
    pos = pos + 1

    if (!testResponse.failure)
      Future.successful(testResponse.data)
    else
      Future.failed(new Exception("Controlled explosion!"))
  }
}

class TestAuditConnector extends AuditConnector {
  override def auditingConfig: AuditingConfig = new AuditingConfig(None, false, false)

  override def sendEvent(event: AuditEvent)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AuditResult] = Future.successful(AuditResult.Success)
}

class TestServiceGenericConnector(pathFailMap: Map[String, Boolean], response: JsValue, httpResponseCode:Option[Int]=None, exception:Option[Exception]=None) extends GenericConnector {
  var count = 0

  override def http: HttpPost with HttpGet = WSHttp

  override def doPost(json:JsValue, host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "/profile/native-app/version-check" =>
        passFail(response, isSuccess(path))
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
    Future.failed(new Exception(s"Test Scenario Error! The path $path is not defined!"))
  }
}
