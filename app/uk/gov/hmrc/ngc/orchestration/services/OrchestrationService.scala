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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.UUID

import play.api.libs.json._
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.config.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.controllers.LiveOrchestrationController
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.{ExecutionContext, Future}

trait OrchestrationService {

  def genericConnector: GenericConnector = GenericConnector

  def preFlightCheck(inputRequest:JsValue)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse]

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

  def preFlightCheck(inputRequest:JsValue)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    withAudit("preFlightCheck", Map.empty) {

      def getVersion = {
        genericConnector.doPost(inputRequest, getConfigProperty("customer-profile","host"), s"/profile/native-app/version-check", getConfigProperty("customer-profile","port").toInt, hc)
          .map(response => (response \ "upgrade").as[Boolean]).recover {
          // Default to false - i.e. no upgrade required.
          case e:Exception =>
            Logger.error(s"Native Error - failure with processing /profile/native-app/version-check. Exception is $ex")
            false
        }
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
      val year = TaxYear.current.currentYear

      buildResponse(inputRequest:JsValue, nino.value, year, journeyId).map(item => item).map(r => r.foldLeft(Json.obj())((b, a) => b ++ a)).recover {
        case ex:Exception =>
          Logger.error(s"Native Error - failure with processing startup for $journeyId. Exception is $ex")
          throw ex
      }
    }
  }

  private def buildResponse(inputRequest:JsValue, nino: String, year: Int, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext) : Future[Seq[JsObject]] = {
    val futuresSeq: Seq[Future[Option[Result]]] = Seq(
      TaxSummary(genericConnector, journeyId),
      TaxCreditSummary(genericConnector, journeyId),
      TaxCreditsSubmissionState(genericConnector, journeyId),
      PushRegistration(genericConnector, inputRequest, journeyId)
    ).map(item => item.execute(nino, year))

    // Drop off Result's which returned None.
    val res: Future[Seq[Result]] = Future.sequence(futuresSeq).map(item => item.flatMap(a => a))
    // Combine the JSON results from each of the functions to generate the final JSON result.
    res.map(r => r.map(b => Json.obj(b.id -> b.jsValue)))
  }
}

object SandboxOrchestrationService extends OrchestrationService with FileResource {
  private val nino = Nino("CS700100A")
  private val preFlightResponse = PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, UUID.randomUUID().toString))

  def preFlightCheck(jsValue:JsValue)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    Future.successful(preFlightResponse)
  }

  def startup(jsValue:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject] = {
    Future.successful(Json.obj("status" -> Json.obj("code" -> "poll")))
  }

}

object LiveOrchestrationService extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
}
