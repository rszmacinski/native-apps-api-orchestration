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

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.config.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.ngc.orchestration.executors.ExecutorFactory
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.time.TaxYear
import scala.concurrent.Future._
import scala.concurrent.{ExecutionContext, Future}


case class OrchestrationServiceRequest(requestLegacy: Option[JsValue], services: Option[OrchestrationRequest])

trait OrchestrationService extends ExecutorFactory {

  def genericConnector: GenericConnector = GenericConnector

  def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse]

  def startup(inputRequest:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject]

  def orchestrate(request: OrchestrationServiceRequest, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject]

  private def getServiceConfig(serviceName: String): Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception)
  }

  protected def getStringSeq(name:String) = Play.current.configuration.getStringSeq(name)

  protected def getConfigProperty(serviceName: String, property: String): String = {
    getServiceConfig(serviceName).getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
}

case class DeviceVersion(os : String, version : String)

object DeviceVersion {
  implicit val formats = Json.format[DeviceVersion]
}

case class PreFlightRequest(os: String, version:String, mfa:Option[MFARequest])

object PreFlightRequest {
  implicit val formats = Json.format[PreFlightRequest]
}

case class JourneyRequest(userIdentifier: String, continueUrl: String, origin: String, affinityGroup: String, context: String, serviceUrl: Option[String], scopes: Seq[String]) //registrationSkippable: Boolean)

object JourneyRequest {
  implicit val format = Json.format[JourneyRequest]
}

case class JourneyResponse(journeyId: String, userIdentifier: String, registrationId: Option[String], continueUrl: String, origin: String, affinityGroup: String, registrationSkippable: Boolean, factor: Option[String], factorUri: Option[String], status: String, createdAt: DateTime)

object JourneyResponse {
  implicit val format = Json.format[JourneyResponse]
}

case class ServiceState(state:String, func: Accounts => MFARequest => Option[String] => Future[MFAAPIResponse])

trait LiveOrchestrationService extends OrchestrationService with Auditor with MFAIntegration {

  val authConnector: AuthConnector

  def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    withAudit("preFlightCheck", Map.empty) {

      def mfaDecision(accounts:Accounts) : Future[Option[MFAAPIResponse]] = {
        def mfaNotRequired = Future.successful(Option.empty[MFAAPIResponse])

        if (!accounts.routeToTwoFactor) mfaNotRequired
        else preflightRequest.mfa.fold(mfaNotRequired) { mfa =>
          verifyMFAStatus(mfa, accounts, journeyId).map(item => Some(item))
        }
      }

      def getVersion = {
        def buildJourney = journeyId.fold(""){id => s"?journeyId=$id"}
        val device = DeviceVersion(preflightRequest.os, preflightRequest.version)

        genericConnector.doPost(Json.toJson(device), getConfigProperty("customer-profile","host"), s"/profile/native-app/version-check$buildJourney", getConfigProperty("customer-profile","port").toInt, hc)
          .map(response => (response \ "upgrade").as[Boolean]).recover {
          // Default to false - i.e. no upgrade required.
          case exception:Exception =>
            Logger.error(s"Native Error - failure with processing version check. Exception is $exception")
            false
        }
      }

      val accountsF = authConnector.accounts(journeyId)
      val versionUpdateF: Future[Boolean] = getVersion

      for {
        accounts <- accountsF
        mfaOutcome <- mfaDecision(accounts)
        versionUpdate <- versionUpdateF
      } yield {
        val mfaURI: Option[MfaURI] = mfaOutcome.fold(Option.empty[MfaURI]){ _.mfa}
        // If authority has been updated then override the original accounts response from auth.
        val returnAccounts = mfaOutcome.fold(accounts) { found =>
          if (found.authUpdated)
            accounts.copy(routeToTwoFactor = false)
          else {
            accounts.copy(routeToTwoFactor = found.routeToTwoFactor)
          }
        }

        PreFlightCheckResponse(versionUpdate, returnAccounts, mfaURI)
      }
    }
  }

  def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject] = {

    request match {
      case OrchestrationServiceRequest(None, Some(services)) =>
        buildAndExecute(services, journeyId).map(serviceResponse => new OrchestrationResponse(serviceResponse)).map(obj => Json.obj("OrchestrationResponse" -> obj))

      case OrchestrationServiceRequest(Some(legacyRequest), None) =>
        startup(legacyRequest, nino, journeyId)
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
      TaxCreditSummary(authConnector, genericConnector, journeyId),
      TaxCreditsSubmissionState(genericConnector, journeyId),
      PushRegistration(genericConnector, inputRequest, journeyId)
    ).map(item => item.execute(nino, year))

    for (results <- sequence(futuresSeq).map(_.flatten)) yield {
      results.map(b => Json.obj(b.id -> b.jsValue))
    }
  }

}


object SandboxOrchestrationService extends OrchestrationService with FileResource {

  val defaultUser = "404893573708"
  val defaultNino = "CS700100A"
  private val ninoMapping = Map(defaultUser -> defaultNino,
                                "404893573709" -> "CS700101A",
                                "404893573710" -> "CS700102A",
                                "404893573711" -> "CS700103A",
                                "404893573712" -> "CS700104A",
                                "404893573713" -> "CS700105A",
                                "404893573714" -> "CS700106A",
                                "404893573715" -> "CS700107A",
                                "404893573716" -> "CS700108A")

  def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    successful(hc.extraHeaders.find(_._1 equals "X-MOBILE-USER-ID") match {
      case  Some((_, value))  => buildPreFlightResponse(value)
      case _ => buildPreFlightResponse(defaultUser)
    })
  }

  def startup(jsValue:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject] = {
    successful(Json.obj("status" -> Json.obj("code" -> "poll")))
  }

  override def orchestrate(request: OrchestrationServiceRequest, nino: Nino, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject] = {
    request.requestLegacy.fold(Future.successful(Json.obj("status" -> Json.obj("code" -> "error"))))
    { startup(_ , nino, journeyId)}
  }

  private def buildPreFlightResponse(userId: String) : PreFlightCheckResponse = {
    val nino = Nino(ninoMapping.getOrElse(userId, defaultNino))
    PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, UUID.randomUUID().toString, "credId-1234", "Individual"))
  }

  override val maxServiceCalls: Int = 10
}

object LiveOrchestrationService extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val authConnector:AuthConnector = AuthConnector
  override val maxServiceCalls: Int = Play.current.configuration.getInt("supported.generic.service.maxNumberServices.count").getOrElse(5)
}
