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

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.ngc.orchestration.connectors.AuthExchangeResponse
import uk.gov.hmrc.ngc.orchestration.controllers.BadRequestException
import uk.gov.hmrc.ngc.orchestration.domain.{MfaURI, Accounts}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.Authorization

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try,Success,Failure}

case class MFAAPIResponse(routeToTwoFactor:Boolean, mfa:Option[MfaURI], authUpdated:Boolean)

case class MFARequest(operation:String, apiURI:Option[String])

object MFARequest {
  final val   START = "start"
  final val OUTCOME = "outcome"

  val allowedValues = Seq(START, OUTCOME)

  implicit val reads: Reads[MFARequest] = new Reads[MFARequest] {
    override def reads(json: JsValue): JsResult[MFARequest] = {
      val a = (json \ "operation").as[String]
      val b = (json \ "apiURI").asOpt[String]
      if (allowedValues.contains(a)) JsSuccess(MFARequest(a, b)) else JsError("supplied operation is invalid!")
    }
  }

  implicit val writes: Writes[MFARequest] = (
    (JsPath \ "operation").write[String] and
      (JsPath \ "apiURI").write[Option[String]]
    )(unlift(MFARequest.unapply))

  implicit val formats = Format(MFARequest.reads, MFARequest.writes)
}

trait MFAIntegration extends OrchestrationService {
  self:LiveOrchestrationService =>

  final val VALIDATE_URL = "/validateMFAoutcome"  // The URL returned from MFA web journeys which indicates the trigger to end wen journey and validate outcome.
  final val NGC_APPLICATION = "NGC"

  def states(implicit hc:HeaderCarrier) = List(ServiceState(MFARequest.START, start), ServiceState(MFARequest.OUTCOME, outcome))

  def verifyMFAStatus(mfa:MFARequest, accounts:Accounts, journeyId:Option[String])(implicit hc:HeaderCarrier): Future[MFAAPIResponse] = {

    def search(searchState:String): PartialFunction[ServiceState, ServiceState] = {
      case found@ServiceState(state, _ ) if state == searchState => found
    }

    def unknownState = Future.failed(new BadRequestException(s"Failed to resolve state!"))

    Try(states.collectFirst(search(mfa.operation)).map(_.func(accounts)(mfa)(journeyId))
      .getOrElse(unknownState)) match {
      case Success(x) => x
      case Failure(f) =>
        Future.failed(new BadRequestException(f.getMessage))
    }
  }

  def mfaStart(accounts:Accounts, journeyId:Option[String])(implicit hc:HeaderCarrier) = {

    val scopes = getStringSeq("scopes").getOrElse(throw new IllegalArgumentException("Failed to resolve scopes!"))
    val journeyRequest = JourneyRequest(accounts.credId, VALIDATE_URL, NGC_APPLICATION, accounts.affinityGroup, "api",
      Some(VALIDATE_URL), scopes)

    genericConnector.doPost(Json.toJson(journeyRequest), getHost, "/multi-factor-authentication/authenticatedJourney",
      getPort, hc)
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
          for {
            _ <- authConnector.updateCredStrength()(hc)
            bearerToken <- authConnector.exchangeForBearer(accounts.credId)
            _ <- updateMainAuthority(bearerToken)
          } yield MFAAPIResponse(routeToTwoFactor = false, None, authUpdated = true)

        case "UNVERIFIED" =>
          mfaStart(accounts, journeyId).map(resp => MFAAPIResponse(routeToTwoFactor = true, Some(resp), authUpdated = false))

        case "NOT_REQUIRED" | "SKIPPED" =>
          Future.successful(MFAAPIResponse(routeToTwoFactor = false, None, authUpdated = false))

        case _ =>
          val error = s"Received unknown status code ${response.status} from MFA. Journey id $journeyId"
          Logger.error(error)
          throw new IllegalArgumentException(error)
      }
    }
  }

  def mfaAPI(path:String)(implicit hc:HeaderCarrier) = {
    genericConnector.doGet(getHost, path, getPort, hc)
      .map { response =>
      response.asOpt[JourneyResponse].getOrElse(throw new IllegalArgumentException("Failed to build MfaURI"));
    }
  }

  def start(accounts:Accounts)(mfa:MFARequest)(journeyId:Option[String])(implicit hc:HeaderCarrier): Future[MFAAPIResponse] = {
    mfaStart(accounts, journeyId).map { mfaResponse => MFAAPIResponse(routeToTwoFactor = true, Some(mfaResponse), authUpdated = false)}
  }

  def outcome(accounts:Accounts)(mfa:MFARequest)(journeyId:Option[String])(implicit hc:HeaderCarrier) = {
    mfaValidateOutcome(accounts, mfa.apiURI.getOrElse(throw new IllegalArgumentException("Failed to obtain URI!")), journeyId)
  }

  def getHost = getConfigProperty("multi-factor-authentication","host")

  def getPort = getConfigProperty("multi-factor-authentication","port").toInt
}
