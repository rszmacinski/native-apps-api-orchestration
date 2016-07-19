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

import play.api.libs.json.Json
import play.api.{Logger, mvc}
import uk.gov.hmrc.api.controllers.{ErrorInternalServerError, ErrorNotFound, HeaderValidator}
import uk.gov.hmrc.ngc.orchestration.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, OrchestrationService, SandboxOrchestrationService}
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ErrorHandling {
  self: BaseController =>
  val app:String

  def log(message:String) = Logger.info(s"$app $message")

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier) = {
    func.recover {
      case ex: NotFoundException =>
        log("Resource not found!")
        Status(ErrorNotFound.httpStatusCode)(Json.toJson(ErrorNotFound))

//      case ex: NinoNotFoundOnAccount =>
//        log("User has no NINO. Unauthorized!")
//        Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case e: Throwable =>
        Logger.error(s"$app Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }
  }
}

trait NativeAppsOrchestrationController extends BaseController with HeaderValidator with ErrorHandling {

  import uk.gov.hmrc.domain.Nino
  import uk.gov.hmrc.ngc.orchestration.domain.RenewalReference

  val service: OrchestrationService
  val accessControl: AccountAccessControlWithHeaderCheck

  final def preFlightCheck(journeyId: Option[String] = None) = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit request =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      errorWrapper(service.preFlightCheck().map(as => Ok(Json.toJson(as))))
  }

  final def startup(nino: Nino, year: Int, renewalReference: RenewalReference, journeyId: Option[String] = None) = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit request =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      errorWrapper(service.startup(nino, year, renewalReference, journeyId).map(result => Ok(Json.toJson(result))))
  }
}

object LiveOrchestrationController extends NativeAppsOrchestrationController {
  override val service = LiveOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val app: String = "Live-Orchestration-Controller"
}

object SandboxOrchestrationController extends NativeAppsOrchestrationController {
  override val service = SandboxOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val app: String = "Sandbox-Orchestration-Controller"
}

