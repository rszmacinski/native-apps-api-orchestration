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

import play.api.http.HeaderNames
import play.api.libs.json.{JsSuccess, JsValue, Json}
import play.api.mvc._
import play.api.{Logger, Play, mvc}
import uk.gov.hmrc.api.controllers.{ErrorInternalServerError, ErrorNotFound}
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.msasync.repository.AsyncRepository
import uk.gov.hmrc.ngc.orchestration.config.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.{Authority, NinoNotFoundOnAccount}
import uk.gov.hmrc.ngc.orchestration.controllers.action.{AccountAccessControlCheckOff, AccountAccessControlWithHeaderCheck}
import uk.gov.hmrc.ngc.orchestration.domain.{ExecutorRequest, OrchestrationRequest}
import uk.gov.hmrc.ngc.orchestration.services.{OrchestrationService, OrchestrationServiceRequest}
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, PreFlightRequest, SandboxOrchestrationService}
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


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

      case e: Exception =>
        Logger.error(s"Native Error - $app Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(Json.toJson(ErrorInternalServerError))
    }

  }
}

trait GenericServiceCheck {
  self: NativeAppsOrchestrationController =>

  def validate(func: OrchestrationServiceRequest => Future[mvc.Result])(implicit request: Request[AnyContent], hc: HeaderCarrier) = {

    request.body.asJson.fold(throw new BadRequestException(s"Failed to build JSON payload! ${request.body}")){ json =>

      json.validate[OrchestrationRequest] match {
        case success: JsSuccess[OrchestrationRequest] => {
          val request = success.get
          if (invalid(verifyServiceName, request.serviceRequest).size > 0 || invalid(verifyEventName, request.eventRequest).size > 0) {
            Future.failed(new BadRequestException("Request not supported"))
          } else if (maxCallsExceeded(service.maxServiceCalls, request.serviceRequest) || maxCallsExceeded(service.maxEventCalls, request.eventRequest)) {
            Future.failed(new BadRequestException("Max Calls Exceeded"))
          } else if (!request.serviceRequest.isDefined && !request.eventRequest.isDefined) {
            Future.failed(new BadRequestException("Nothing to execute"))
          } else {
            func(OrchestrationServiceRequest(None, Some(success.get)))
          }
        }
        case _ => func(OrchestrationServiceRequest(Some(json), None))
      }
    }
  }

  private def maxCallsExceeded(max: Int , request: Option[Seq[ExecutorRequest]]): Boolean = {
    if(request.isDefined){
      max < request.get.size
    }
    else false
  }

  private def invalid(verify: => String => Boolean, request: Option[Seq[ExecutorRequest]]): Seq[Boolean] = {
    if(request.isDefined){
      request.get.map {
        req => if (!verify(req.name)) Some(true) else None
      }.flatten
    }
    else Seq.empty
  }

  protected def verifyServiceName(serviceName: String): Boolean = {
    Play.current.configuration.getBoolean(s"supported.generic.service.$serviceName.on").getOrElse(false)
  }

  protected def verifyEventName(eventName: String): Boolean = {
    Play.current.configuration.getBoolean(s"supported.generic.event.$eventName.on").getOrElse(false)
  }

}

trait SecurityCheck {
  def checkSecurity:Boolean
}

trait NativeAppsOrchestrationController extends AsyncController with SecurityCheck with Auditor with GenericServiceCheck {
  val service: OrchestrationService
  val accessControl: AccountAccessControlWithHeaderCheck
  val accessControlOff: AccountAccessControlWithHeaderCheck
  val maxAgeForSuccess: Long

  def preFlightCheck(journeyId:Option[String]): Action[JsValue] = accessControlOff.validateAcceptWithAuth(acceptHeaderValidationRules, None).async(BodyParsers.parse.json) {
    implicit request =>
      errorWrapper {
        implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
        implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails
        Json.toJson(request.body).asOpt[PreFlightRequest].
          fold(Future.successful(BadRequest("Failed to parse request!"))) { preFlightRequest =>

            hc.authorization match {
              case Some(auth) => service.preFlightCheck(preFlightRequest, journeyId).map(
                response => Ok(Json.toJson(response)).withSession(authToken -> auth.value)
              )

              case _ => Future.failed(new Exception("Failed to resolve authentication from HC!"))
            }
        }
      }
  }

  def orchestrate(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] = accessControl.validateAcceptWithAuth(acceptHeaderValidationRules, Some(nino)).async {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
      implicit val req = authenticated.request
      implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails

      errorWrapper {
        validate { validatedRequest =>

          // Do not allow more than one task to be executing - if task is running then poll status will be returned.
          asyncActionWrapper.async(callbackWithStatus) {
            flag =>

              // Async function wrapper responsible for executing below code onto a background queue.
              asyncWrapper(callbackWithStatus) {
                headerCarrier =>
                  Logger.info(s"Background HC: ${hc.authorization.fold("not found")(_.value)} for Journey Id $journeyId")

                  service.orchestrate(validatedRequest, nino, journeyId).map { response =>
                    AsyncResponse(response ++ buildResponseCode(ResponseStatus.complete), nino)
                  }
              }
          }
        }
      }
  }

  /**
   * Invoke the library poll function to determine the response to the client.
   */
  def poll(nino: Nino, journeyId: Option[String] = None) = accessControl.validateAcceptWithAuth(acceptHeaderValidationRules, Some(nino)).async {
    implicit authenticated =>
      withAudit("poll", Map("nino" -> nino.value)) {
        errorWrapper {

          implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
          implicit val req = authenticated.request
          implicit val authority = authenticated.authority
          implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails

          val session: Option[AsyncMvcSession] = getSessionObject
          def withASyncSession(data:Map[String,String]): Map[String, String] = {
            data - AsyncMVCSessionId + (AsyncMVCSessionId -> Json.stringify(Json.toJson(session)))
          }

          // Make a request to understand the status of the async task. Please note the async library will update the session and remove the task id from session once the task completes.
          implicit val ninoInRequest = nino
          val response = pollTask(Call("GET", "/notaskrunning"), callbackWithSuccessResponse, callbackWithStatus)
          // Convert 303 response to 404. The 303 is generated (with URL "notaskrunning") when no task Id exists in the users session!
          response.map(resp => {
            resp.header.status match {
              case 303 =>
                val now = DateTimeUtils.now.getMillis
                session match {
                  case Some(s) =>
                    Logger.info(s"Native - Poll Task not in cache! Client start request time ${s.start-getClientTimeout} - Client timeout ${s.start} - Current time $now. Journey Id $journeyId")

                  case None =>
                    Logger.info(s"Native - Poll - no session object found! Journey Id $journeyId")
                }
                NotFound

              case _ =>
                // Add the task Id back into session. This allows the user to re-call the poll service once complete.
                resp.withSession(resp.session.copy(data = withASyncSession(resp.session.data)))
            }
          })
        }
      }
  }

  def addCacheHeader(maxAge:Long, result:Result):Result = {
    result.withHeaders(HeaderNames.CACHE_CONTROL -> s"max-age=$maxAge")
  }

  /**
   *  Callback from async framework to generate the successful Result. The off-line has task completed successfully.
   */
  val nino_compare_length = 8
  def callbackWithSuccessResponse(response:AsyncResponse)(id:String)(implicit request:Request[AnyContent], authority:Option[Authority], requestNino:Nino) : Future[Result] = {

    def noAuthority = throw new Exception("Failed to resolve authority")
    def success = addCacheHeader(maxAgeForSuccess, Ok(response.value))

    val responseNinoTaxSummary = (response.value \ "taxSummary" \ "taxSummaryDetails" \ "nino").asOpt[String]
    val responseNinoCreditSummary = (response.value \ "taxCreditSummary" \ "personalDetails" \ "nino").asOpt[String]

    val ninoCheck = (responseNinoTaxSummary, responseNinoCreditSummary) match {
      case (None, None) => Some(response.nino.value)
      case (taxSummaryNino, None) => taxSummaryNino
      case (None, taxCreditSummaryNino) => taxCreditSummaryNino
      case (taxSummaryNino, taxCreditSummaryNino) => taxSummaryNino
    }

    val result = if (checkSecurity && ninoCheck.isDefined) {
      val nino = ninoCheck.getOrElse("No NINO found in response!").take(nino_compare_length)
      val authNino = authority.getOrElse(noAuthority).nino.value.take(nino_compare_length)

      // Check request nino matches the authority record.
      if (!requestNino.value.take(nino_compare_length).equals(authNino)) {
        Logger.error(s"Native Error - Request NINO $requestNino does not match authority NINO $authNino! Response is ${response.value}")
        Unauthorized
      } else if (!nino.equals(authNino) || !requestNino.value.take(nino_compare_length).equals(nino)) {
        Logger.error(s"Native Error - Failed to match tax summary response NINO $ninoCheck with authority NINO $authNino! Response is ${response.value}")
        Unauthorized
      } else success
    } else success

    Future.successful(result)
  }
}

trait ConfigLoad {
  val pollMaxAge = "poll.success.maxAge"
  def getConfigForPollMaxAge:Option[Long]

  lazy val maxAgeForSuccess: Long = getConfigForPollMaxAge
    .getOrElse(throw new Exception(s"Failed to resolve config key $pollMaxAge"))
}


object LiveOrchestrationController extends NativeAppsOrchestrationController with ConfigLoad {
  override val service = LiveOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val accessControlOff = AccountAccessControlCheckOff
  override val app: String = "Live-Orchestration-Controller"
  override lazy val repository:AsyncRepository = AsyncRepository()
  override def checkSecurity: Boolean = true
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override def getConfigForPollMaxAge = Play.current.configuration.getLong(pollMaxAge)
}

object SandboxOrchestrationController extends SandboxOrchestrationController {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val maxAgeForSuccess: Long = 3600

}

trait SandboxOrchestrationController extends NativeAppsOrchestrationController with SandboxPoll {
  override val actorName = "sandbox-async_native-apps-api-actor"
  override def id = "sandbox-async_native-apps-api-id"

  override val service: OrchestrationService = SandboxOrchestrationService
  override val accessControl = AccountAccessControlWithHeaderCheck
  override val accessControlOff = AccountAccessControlCheckOff
  override val app: String = "Sandbox-Orchestration-Controller"
  override lazy val repository:AsyncRepository = sandboxRepository
  override def checkSecurity: Boolean = false

  override def preFlightCheck(journeyId:Option[String]): Action[JsValue] = accessControlOff.validateAcceptWithAuth(acceptHeaderValidationRules, None).async(BodyParsers.parse.json) {
    implicit request =>
      errorWrapper {
        implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None).withExtraHeaders("X-MOBILE-USER-ID" -> request.headers.get("X-MOBILE-USER-ID").getOrElse("404893573708"))
        implicit val context: ExecutionContext = MdcLoggingExecutionContext.fromLoggingDetails

        Json.toJson(request.body).asOpt[PreFlightRequest].
          fold(Future.successful(BadRequest("Failed to parse request!"))) { preFlightRequest =>
            hc.authorization match {
              case Some(auth) => service.preFlightCheck(preFlightRequest, journeyId).map(
                response => Ok(Json.toJson(response)).withSession(authToken -> auth.value)
              )

              case _ => Future.failed(new Exception("Failed to resolve authentication from HC!"))
            }
          }
      }
  }

  // Must override the startup call since live controller talks to a queue.
  override def orchestrate(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] = accessControlOff.validateAcceptWithAuth(acceptHeaderValidationRules, Some(nino)).async {
    implicit authenticated =>
      implicit val hc = HeaderCarrier.fromHeadersAndSession(authenticated.request.headers, None)
      implicit val req = authenticated.request

      errorWrapper {
        validate { validatedRequest =>
          service.orchestrate(validatedRequest, nino, journeyId).map(resp => Ok(resp).withCookies(Cookie("mdtpapi", buildRequestsCookie(journeyId))))
        }
      }
  }

  def buildRequestsCookie(journeyId: Option[String]): String = {
    SandboxOrchestrationService.getGenericExecutions(journeyId).getOrElse("")
  }

  // Override the poll and return static resource.
   override def poll(nino: Nino, journeyId: Option[String] = None) = accessControlOff.validateAccept(acceptHeaderValidationRules).async {
    implicit authenticated =>
      errorWrapper {
        authenticated.cookies.get("mdtpapi").get.value
        Future.successful(addCacheHeader(maxAgeForSuccess, Ok(pollSandboxResult(nino, authenticated.cookies.get("mdtpapi")).value)))
      }
  }
}
