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

import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.{Logger, mvc}
import uk.gov.hmrc.api.controllers.{ErrorInternalServerError, ErrorNotFound}
import uk.gov.hmrc.msasync.repository.AsyncRepository
import uk.gov.hmrc.ngc.orchestration.connectors.{Authority, NinoNotFoundOnAccount}
import uk.gov.hmrc.ngc.orchestration.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, OrchestrationService, SandboxOrchestrationService}
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class BadRequestException(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 400)

trait ErrorHandling {
  self: BaseController =>
  val app:String

  def log(message:String) = Logger.info(s"$app $message")

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier) = {
    func.recover {
      case ex: NotFoundException =>
        log("Resource not found!")
        Status(ErrorNotFound.httpStatusCode)(Json.toJson(ErrorNotFound))

      case ex:BadRequestException =>
        log("BadRequest!")
        Status(ErrorBadRequest.httpStatusCode)(Json.toJson(ErrorBadRequest))

      case ex: NinoNotFoundOnAccount =>
        log("User has no NINO. Unauthorized!")
        Unauthorized(Json.toJson(ErrorUnauthorizedNoNino))

      case e: Throwable =>
        Logger.error(s"$app Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }
  }
}

trait SecurityCheck {
  def checkSecurity:Boolean
}

trait NativeAppsOrchestrationController extends AsyncController with SecurityCheck {

  import uk.gov.hmrc.domain.Nino

  val service: OrchestrationService
  val accessControl: AccountAccessControlWithHeaderCheck

  final def preFlightCheck(journeyId: Option[String] = None): Action[JsValue] = accessControl.validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
    implicit request =>
      errorWrapper {
        implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)

        hc.authorization.fold(throw new Exception("No auth token found for gateway request!?")) { t =>
          service.preFlightCheck(request.body).map(response => Ok(Json.toJson(response))
            .withSession(authToken -> t.value)
          )
        }
      }
  }

  final def startup(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
      implicit val req = authenticated.request

      errorWrapper {
        // Only 1 task to be running per session. If session contains Id then request routed to poll response.
        withAsyncSession {
          // This code will return an AsyncResponse. The actual Result is controlled through the callbacks. Please see poll().
          req.body.asJson.fold(throw new BadRequestException(s"Failed to build JSON payload! ${req.body}")) { json =>

            // Do not allow more than one task to be executing - if task is running then poll status will be returned.
            asyncActionWrapper.async(callbackWithStatus) {
              flag =>
                // Async function wrapper responsible for executing below code onto a background queue.
                asyncWrapper(callbackWithStatus) {
                  headerCarrier =>
                    service.startup(json, nino, journeyId).map { response =>
                      AsyncResponse(response ++ buildResponseCode(ResponseStatus.complete))
                    }
                }
            }
          }
        }
      }
  }

  /**
   * Invoke the library poll function to determine the response to the client.
   */
  def poll(nino: Nino, journeyId: Option[String] = None) = accessControl.validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      errorWrapper {
        withAsyncSession {
          implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
          implicit val req = authenticated.request
          implicit val authority = authenticated.authority

          val response = pollTask(Call("GET", "/notaskrunning"), callbackWithSuccessResponse, callbackWithStatus)
          // Convert 303 response to 404. The 303 is generated (with URL "notaskrunning") when no task exists in the users session!
          response.map(resp => {
            resp.header.status match {
              case 303 => NotFound
              case _ => resp
            }
          })
        }
      }
  }

  /**
   *  Callback from async framework to generate the successful Result. The off-line has task completed successfully.
   */
  def callbackWithSuccessResponse(response:AsyncResponse)(id:String)(implicit request:Request[AnyContent], authority:Option[Authority]) : Future[Result] = {
    removeTaskFromCache(Some(id)) {
      def noAuthority = throw new Exception("Failed to resolve authority")
      def success = Ok(response.value)

      // Verify the nino from the users authority matches the nino returned in the tax summary  response.
      val responseNino = (response.value \ "taxSummary" \ "taxSummaryDetails" \ "nino").asOpt[String]
      val result = if (checkSecurity) {
        val nino = responseNino.getOrElse("No NINO found in response!").take(8)
        val authNino = authority.getOrElse(noAuthority).nino.value.take(8)
        if (!nino.equals(authNino)) {
          Logger.error("Failed to match tax summary response NINO with authority NINO!")
          Unauthorized
        } else success
      } else success
      Future.successful(result)
    }
  }

}

object LiveOrchestrationController extends NativeAppsOrchestrationController {
  override val service = LiveOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val app: String = "Live-Orchestration-Controller"
  override lazy val repository:AsyncRepository = AsyncRepository()
  override def checkSecurity: Boolean = true
}

object SandboxOrchestrationController extends NativeAppsOrchestrationController {
  override val actorName = "sandbox-async_native-apps-api-actor"
  override def id = "sandbox-async_native-apps-api-id"

  override val service = SandboxOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val app: String = "Sandbox-Orchestration-Controller"
  override lazy val repository:AsyncRepository = AsyncRepository()
  override def checkSecurity: Boolean = false
}

