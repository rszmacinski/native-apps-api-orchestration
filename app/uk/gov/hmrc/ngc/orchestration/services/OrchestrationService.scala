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
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthExchangeResponse, AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.controllers.BadRequestException
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._
import scala.concurrent.{ExecutionContext, Future}

trait OrchestrationService {

  def genericConnector: GenericConnector = GenericConnector

  def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse]

  def startup(inputRequest:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject]

  private def getServiceConfig(serviceName: String): Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception)
  }

  protected def getStringSeq(name:String) = Play.current.configuration.getStringSeq(name)

  protected def getConfigProperty(serviceName: String, property: String): String = {
    getServiceConfig(serviceName).getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
}

// TODO...MOVE!!!

case class MFARequest(operation:String, apiURL:Option[String])

case class DeviceVersion(os : String, version : String)

case class PreFlightRequest(os: String, version:String, mfa:Option[MFARequest])

case class MFAAPIResponse(routeToTwoFactor:Boolean, mfa:Option[MfaURI], authUpdated:Boolean)

object MFARequest {
  implicit val formats = Json.format[MFARequest]
}

object PreFlightRequest {
  implicit val formats = Json.format[PreFlightRequest]
}

object DeviceVersion {
  implicit val formats = Json.format[DeviceVersion]
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


trait MFAIntegration extends OrchestrationService {
self:LiveOrchestrationService =>

  final val VALIDATE_URL = "/validate"
  final val NGC_APPLICATION = "NGC"

  def uuid = UUID.randomUUID().toString

  def states(implicit hc:HeaderCarrier) = List(ServiceState("start", start), ServiceState("outcome", outcome))

  def verifyMFAStatus(mfa:MFARequest, accounts:Accounts, journeyId:Option[String])(implicit hc:HeaderCarrier): Future[MFAAPIResponse] = {

    def search(searchState:String): PartialFunction[ServiceState, ServiceState] = {
      case found@ServiceState(state, _ ) if (state == searchState) => found
    }

    states.collectFirst(search(mfa.operation)).map(_.func(accounts)(mfa)(journeyId))
      .getOrElse(Future.failed(new BadRequestException(s"Failed to resolve state !")))
  }

  def mfaStart(accounts:Accounts, journeyId:Option[String])(implicit hc:HeaderCarrier) = {
    val scopes = getStringSeq("scopes").getOrElse(throw new IllegalArgumentException("Failed to resolve scopes!"))
    val journeyRequest = JourneyRequest(uuid, VALIDATE_URL, NGC_APPLICATION, accounts.affinityGroup, "api", Some("service_URI"), scopes)

    genericConnector.doPost(Json.toJson(journeyRequest), getConfigProperty("mfa","host"), "/multi-factor-authentication/journey", getConfigProperty("mfa","port").toInt, hc)
      .map { response =>
      val mfaApi = response.asOpt[MfaURI].getOrElse(throw new IllegalArgumentException(s"Failed to build MfaURI $response! for $journeyId"))
      if (mfaApi.apiURI.isEmpty || mfaApi.webURI.isEmpty) throw new IllegalArgumentException(s"URLs found to be empty $response for $journeyId!")
      mfaApi
    }
  }
  
  def mfaValidateOutcome(accounts:Accounts, path:String, journeyId:Option[String])(implicit hc:HeaderCarrier): Future[MFAAPIResponse] = {
    // Invoke MFA using path supplied from client.
    mfaAPI(path).flatMap { response =>
      Logger.info(s"Received status ${response.status} for $journeyId.")

      def updateMainAuthority(auth: AuthExchangeResponse): Future[Unit] = {
        val mainAuthority = hc.copy(authorization = Some(Authorization(auth.access_token.authToken)))
        authConnector.updateCredStrength()(mainAuthority)
      }

      response.status match {
        case "VERIFIED" =>
          (for {
            _ <- authConnector.updateCredStrength()(hc)
            bearerToken <- authConnector.exchangeForBearer(accounts.credId)
            _ <- updateMainAuthority(bearerToken)
          } yield (MFAAPIResponse(false, None, true)))

        case "UNVERIFIED" =>
            mfaStart(accounts, journeyId).map(resp => MFAAPIResponse(true, Some(resp), false))

        case "NOT_REQUIRED" | "SKIPPED" =>
          Future.successful(MFAAPIResponse(false, None, false))

        case _ =>
          val error = s"Received unknown status code ${response.status} from MFA. Journey id $journeyId"
          Logger.error(error)
          throw new IllegalArgumentException(error)
      }
    }
  }

  def mfaAPI(path:String)(implicit hc:HeaderCarrier) = {
    genericConnector.doGet(getConfigProperty("mfa","host"), path, getConfigProperty("mfa","port").toInt, hc)
      .map { response =>
        response.asOpt[JourneyResponse].getOrElse(throw new IllegalArgumentException("Failed to build MfaURI"));
    }
  }

  def start(accounts:Accounts)(mfa:MFARequest)(journeyId:Option[String])(implicit hc:HeaderCarrier): Future[MFAAPIResponse] = {
      mfaStart(accounts, journeyId).map {
        mfaResponse =>
          MFAAPIResponse(true, Some(mfaResponse), false)
      }
  }

  def outcome(accounts:Accounts)(mfa:MFARequest)(journeyId:Option[String])(implicit hc:HeaderCarrier) = {
    mfaValidateOutcome(accounts, mfa.apiURL.getOrElse(throw new IllegalArgumentException("Failed to obtain URI!")), journeyId)
  }

}
// TODO...MOVE ABOVE TO ANOTHER FILE


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
          case e:Exception =>
            Logger.error(s"Native Error - failure with processing version check. Exception is $ex")
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
  private val nino = Nino("CS700100A")
  private val preFlightResponse = PreFlightCheckResponse(upgradeRequired = false, Accounts(Some(nino), None, routeToIV = false, routeToTwoFactor = false, UUID.randomUUID().toString, "credId-1234", "Individual"))

  def preFlightCheck(preflightRequest:PreFlightRequest, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    successful(preFlightResponse)
  }

  def startup(jsValue:JsValue, nino: uk.gov.hmrc.domain.Nino, journeyId: Option[String]) (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[JsObject] = {
    successful(Json.obj("status" -> Json.obj("code" -> "poll")))
  }

}

object LiveOrchestrationService extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val authConnector:AuthConnector = AuthConnector
}
