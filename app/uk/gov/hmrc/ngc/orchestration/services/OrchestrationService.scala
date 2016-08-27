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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.{Calendar, UUID}

import play.api.libs.json._
import play.api.{Configuration, Play}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.ngc.orchestration.config.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.controllers.{AsyncResponse, LiveOrchestrationController, MandatoryResponse}
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

trait OrchestrationService {

  def genericConnector: GenericConnector = GenericConnector

  def preFlightCheck(inputRequest:JsValue) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse]

  def startup(inputRequest:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject]

  private def getServiceConfig(serviceName: String): Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception)
  }

  protected def getConfigProperty(serviceName: String, property: String): String = {
    getServiceConfig(serviceName).getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
}

trait LiveOrchestrationService extends OrchestrationService with Auditor {

  def controller = LiveOrchestrationController

  def authConnector: AuthConnector = AuthConnector

  def preFlightCheck(inputRequest:JsValue) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    withAudit("preFlightCheck", Map.empty) {

      def getVersion = {
        genericConnector.doPost(inputRequest, getConfigProperty("customer-profile","host"), "/profile/native-app/version-check", getConfigProperty("customer-profile","port") toInt, hc)
          .map(response => (response \ "upgrade").as[Boolean])
      }

      val accountsF = authConnector.accounts()
      val versionUpdateF: Future[Boolean] = getVersion

      for {
        accounts <- accountsF
        versionUpdate <- versionUpdateF
      } yield PreFlightCheckResponse(versionUpdate, accounts)
    }
  }

  def startup(inputRequest:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject]= {
    withAudit("startup", Map("nino" -> nino.value)) {
      val year = Calendar.getInstance().get(Calendar.YEAR)

      val result = run(inputRequest:JsValue, nino.value, year).map(item => item).map(r => r.foldLeft(Json.obj())((b, a) => b ++ a))
      result.recover {
        case ex:Exception => MandatoryResponse
      }
      result
    }
  }

  private def run(inputRequest:JsValue, nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext) : Future[Seq[JsObject]] = {
    val futuresSeq = Seq(
      TaxSummary(genericConnector),
      TaxCreditSummary(genericConnector),
      State(genericConnector),
      PushRegistration(genericConnector, inputRequest)
    ).map(item => item.execute(nino, year))

    // Drop off results which returned None.
    val res: Future[Seq[Result]] = Future.sequence(futuresSeq).map(item => item.flatMap(a => a))
    // Combine the json results from each of the functions to generate the result.
    res.map(r => r.map(b => Json.obj(b.id -> b.jsValue)))
  }
}

object SandboxOrchestrationService extends OrchestrationService with FileResource {

  private val nino = Nino("CS700100A")
  private val email = EmailAddress("name@email.co.uk")
  private val preFlightResponse = PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, UUID.randomUUID().toString))

  def preFlightCheck(jsValue:JsValue)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    Future.successful(preFlightResponse)
  }

  def startup(jsValue:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject] = {

    Future.successful(Json.obj("status" -> Json.parse("""{"code":"poll"}""")))
  }

  def poll(): Future[AsyncResponse] =  {
    val resource: Option[String] = findResource(s"/resources/getsummary/${nino.value}_2016.json")
    val taxSummary = Result("taxSummary",Json.parse(resource.get))
    val emailPreferences  = JsObject(Seq("email" -> JsString(email), "status" -> JsString("verified")))
    val preferences = Result("preference", JsObject(Seq("digital" -> JsBoolean(true), "email" -> emailPreferences)))
    val taxCreditSummary = Result("taxCreditSummary", Json.parse(findResource(s"/resources/taxcreditsummary/${nino.value}.json").get))
    val res = Future.successful(Seq(taxSummary, preferences, taxCreditSummary).map(b => Json.obj(b.id -> b.jsValue)))
    res.map(r => AsyncResponse(r.foldLeft(Json.obj())((b, a) => b ++ a)))
  }
}

object LiveOrchestrationService extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
}