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

import play.api.libs.json.{JsObject, Json}
import play.api.{Logger, mvc}
import uk.gov.hmrc.api.controllers.{ErrorInternalServerError, ErrorNotFound}
import uk.gov.hmrc.msasync.repository.AsyncRepository
import uk.gov.hmrc.ngc.orchestration.connectors.NinoNotFoundOnAccount
import uk.gov.hmrc.ngc.orchestration.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, Mandatory, OrchestrationService, SandboxOrchestrationService}
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

      case ex: NinoNotFoundOnAccount =>
        log("User has no NINO. Unauthorized!")
        Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case ex: Mandatory =>
        log("Mandatory Data not found")
        Status(MandatoryResponse.httpStatusCode)(Json.toJson(MandatoryResponse))

      case e: Throwable =>
        Logger.error(s"$app Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }
  }
}

trait NativeAppsOrchestrationController extends AsyncController {

  import uk.gov.hmrc.domain.Nino

  val service: OrchestrationService
  val accessControl: AccountAccessControlWithHeaderCheck

  final def preFlightCheck(journeyId: Option[String] = None) = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      service.preFlightCheck()
      Future.successful(Ok.withSession("AppKey" -> "NativeApp"))
  }

  final def startup(nino: Nino, journeyId: Option[String] = None) = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
      implicit val req = authenticated.request
      withAsyncSession {

        // Do not allow more than one task to be executing - if task running then poll page will be returned.
        asyncActionWrapper.async(callbackWithStatus) {
          flag =>

            // Async function wrapper responsible for executing below code onto a background queue.
            asyncWrapper(callbackWithStatus) {
              implicit hc =>
                // Your code which returns a Future is place here!
                service.startup(nino, journeyId).map { response =>
                  // Build a response with the value that was supplied to the action.
                  val b: JsObject = response
                  AsyncResponse(response)
                }

                // NOTE: This is just an example to incur a delay before sending the response. This MUST not be copied into your code!
//                TimedEvent.delayedSuccess(5000, 0).map { _ => response }
            }
        }
      }
  }
//
//
//
//
//
//
//
//
//    implicit request =>
//      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
//      errorWrapper(service.startup(nino, journeyId).map{
//        case result => Ok(Json.toJson(result))
//        case _ => NotFound
//      })
//  }


}

object LiveOrchestrationController extends NativeAppsOrchestrationController {
  override val service = LiveOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val app: String = "Live-Orchestration-Controller"
  override lazy val repository:AsyncRepository = AsyncRepository()
}

object SandboxOrchestrationController extends NativeAppsOrchestrationController {
  override val service = SandboxOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val app: String = "Sandbox-Orchestration-Controller"
  override lazy val repository:AsyncRepository = AsyncRepository()
}

