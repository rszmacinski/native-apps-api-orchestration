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

import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.msasync.repository.AsyncRepository
import uk.gov.hmrc.ngc.orchestration.controllers.action.AccountAccessControlWithHeaderCheck
import uk.gov.hmrc.play.asyncmvc.async.Cache
import uk.gov.hmrc.play.asyncmvc.model.{TaskCache, ViewCodes}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class AsyncResponse(value:JsObject)
object AsyncResponse {
  implicit val format = Json.format[AsyncResponse]
}

object ResponseStatus {
  val timeout = "timeout" // Timed out waiting for task to complete.
  val error = "error" // Offline task generated a failure.
  val poll = "poll" // Poll status. Task not complete.
  val throttle = "throttle" // Throttle limit hit. Too many tasks executing.
  val complete = "complete" // Success.
}

case class AsyncStatusResponse(code:String, message:Option[String]=None)

object AsyncStatusResponse {
  implicit val format = Json.format[AsyncStatusResponse]
}

trait ResponseCode {
  def buildResponseCode(code:String) = {
    Json.obj("status" -> Json.toJson(AsyncStatusResponse(code)))
  }
}

trait AsyncController extends BaseController with HeaderValidator with ErrorHandling with AsyncMvcIntegration with ResponseCode {

  val authToken = "AuthToken"

  val accessControl: AccountAccessControlWithHeaderCheck
  val repository:AsyncRepository

  // Function wrapper verifies the session exists before proceeding to call an async function.
  protected def withAsyncSession(func: => Future[Result])(implicit request:Request[AnyContent]) : Future[Result] = {
    request.session.get(authToken).fold(Future.successful(BadRequest("Invalid request"))) { token => {
        if (!hc.authorization.get.value.equals(token)) {
          Logger.error("HC bearer token does not match session token!")
          Future.failed(new BadRequestException("Invalid request - HC bearer token does not match session!"))
        } else {
          func
        }
      }
    }
  }

  /**
   * Remove the task from cache since sending reply to client.
   */
  protected def removeTaskFromCache(id:Option[String])(func: => Future[Result])(implicit request:Request[AnyContent]) : Future[Result] = {
    id.fold(func){ taskId => repository.removeById(taskId).flatMap(_ => func)}
  }

  /**
   * Callback from async framework to process 'status'. Build client reply.
   */
  def callbackWithStatus(status:Int)(id:Option[String])(implicit request:Request[AnyContent]) : Future[Result] = {
    val res = status match {
      case ViewCodes.Timeout => Ok(buildResponseCode(ResponseStatus.timeout))
      case ViewCodes.Polling => Ok(buildResponseCode(ResponseStatus.poll))
      case ViewCodes.ThrottleReached => TooManyRequests(buildResponseCode(ResponseStatus.throttle))
      case ViewCodes.Error | _ => Ok(buildResponseCode(ResponseStatus.error))
    }
    Future.successful(res)
  }

  override def taskCache: Cache[TaskCache] = new Cache[TaskCache] {

    val expire = 300000L // Note: The expire time must be greater than the client timeout. 5 mins. Client timeout is 2 minutes.

    override def put(id: String, value: TaskCache)(implicit hc: HeaderCarrier): Future[Unit] = {
      repository.save(value, expire).map(_ => ())
    }

    override def get(id: String)(implicit hc: HeaderCarrier): Future[Option[TaskCache]] = {
      repository.findByTaskId(id).map {
        case Some(found) => Some(found.task)
        case _ => None
      }
    }
  }
}

object LiveAsyncController extends AsyncController {
  override val accessControl: AccountAccessControlWithHeaderCheck = AccountAccessControlWithHeaderCheck
  override val repository: AsyncRepository = AsyncRepository()
  override val app: String = "Live Async Controller"
}