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

package uk.gov.hmrc.ngc.orchestration.controllers.action

import play.api.Logger
import play.api.libs.json.{Writes, Json}
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.ngc.orchestration.connectors._
import uk.gov.hmrc.ngc.orchestration.controllers.ErrorUnauthorizedNoNino

import scala.concurrent.Future


case class AuthenticatedRequest[A](authority: Option[Authority], request: Request[A]) extends WrappedRequest(request)

case object ErrorUnauthorizedMicroService extends ErrorResponse(401, "UNAUTHORIZED", "Unauthorized to access resource")
case object ErrorUnauthorizedWeakCredStrength extends ErrorResponse(401, "WEAK_CRED_STRENGTH", "Credential Strength on account does not allow access")

trait AccountAccessControl extends Results {

  import scala.concurrent.ExecutionContext.Implicits.global

  val authConnector: AuthConnector

  case object ErrorUnauthorized extends ErrorResponse(401, "UNAUTHORIZED", "Invalid request")

    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result], taxId:Option[Nino]) = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
        authConnector.grantAccess(taxId).flatMap {
          authority => {
            block(AuthenticatedRequest(Some(authority), request))
          }
    }.recover {
      case ex: uk.gov.hmrc.play.http.Upstream4xxResponse =>
        Logger.info("Unauthorized! Failed to grant access since 4xx response!")
        Unauthorized(Json.toJson(ErrorUnauthorizedMicroService))

      case ex: NinoNotFoundOnAccount =>
        Logger.info("Unauthorized! NINO not found on account!")
        Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case ex: FailToMatchTaxIdOnAuth =>
        Logger.info("Unauthorized! Failure to match URL NINO against Auth NINO")
        Status(ErrorUnauthorized.httpStatusCode)(Json.toJson(ErrorUnauthorized))

      case ex: AccountWithLowCL =>
        Logger.info("Unauthorized! Account with low CL!")
        Unauthorized(Json.toJson(ErrorUnauthorizedLowCL))

      case ex: AccountWithWeakCredStrength =>
        Logger.info("Unauthorized! Account with weak cred strength!")
        Unauthorized(Json.toJson(ErrorUnauthorizedWeakCredStrength))
    }
  }

}

trait AccountAccessControlWithHeaderCheck extends HeaderValidator {
  val checkAccess=true
  val accessControl:AccountAccessControl

    def validateAcceptWithAuth(rules: Option[String] => Boolean, taxId: Option[Nino]) = new ActionBuilder[AuthenticatedRequest] {

      def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]) = {
        if (rules(request.headers.get("Accept"))) {
          if (checkAccess) accessControl.invokeBlock(request, block, taxId)
          else block(AuthenticatedRequest(None,request))
        }
        else Future.successful(Status(ErrorAcceptHeaderInvalid.httpStatusCode)(Json.toJson(ErrorAcceptHeaderInvalid)))
      }
    }

}

object Auth {
  val authConnector: AuthConnector = AuthConnector
}

object AccountAccessControl extends AccountAccessControl {
  val authConnector: AuthConnector = Auth.authConnector
}

object AccountAccessControlWithHeaderCheck extends AccountAccessControlWithHeaderCheck {
  val accessControl: AccountAccessControl = AccountAccessControl
}

object AccountAccessControlOff extends AccountAccessControl {
  val authConnector: AuthConnector = new AuthConnector {
    override val serviceUrl: String = "NO SERVICE"

    override def serviceConfidenceLevel: ConfidenceLevel = ConfidenceLevel.L0

    override def http: HttpGet with HttpPost = new HttpGet with HttpPost {
      def sandboxMode = Future.failed(new IllegalArgumentException("Sandbox mode!"))

      override protected def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = Future.failed(new IllegalArgumentException("Sandbox mode!"))
      override val hooks: Seq[HttpHook] = NoneRequired

      override protected def doPost[A](url: String, body: A, headers: Seq[(String, String)])(implicit wts: Writes[A], hc: HeaderCarrier): Future[HttpResponse] = sandboxMode

      override protected def doPostString(url: String, body: String, headers: Seq[(String, String)])(implicit hc: HeaderCarrier): Future[HttpResponse] = sandboxMode

      override protected def doFormPost(url: String, body: Map[String, Seq[String]])(implicit hc: HeaderCarrier): Future[HttpResponse] = sandboxMode

      override protected def doEmptyPost[A](url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = sandboxMode
    }
  }
}

object AccountAccessControlCheckOff extends AccountAccessControlWithHeaderCheck {
  override val checkAccess=false

  val accessControl: AccountAccessControl = AccountAccessControlOff
}

