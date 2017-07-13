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

package uk.gov.hmrc.ngc.orchestration.executors

import play.api.libs.json.JsValue
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.domain.{OrchestrationRequest, ServiceResponse}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


sealed trait Executor {
  val serviceName: String
  val executionType: String
  val executorName:String
  def path(journeyId: Option[String], data: Option[JsValue]): String
  val POST = "POST"
  val GET = "GET"
  val cacheTime: Option[Long]
  def connector: GenericConnector
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def execute(cacheTime: Option[Long], data: Option[JsValue], journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ServiceResponse]] = {
    executionType.toUpperCase match {
      case POST =>
        val postData = data.getOrElse(throw new Exception("No Post Data Provided!"))
        connector.doPost(postData, host, path(journeyId, data), port, hc).map { response =>

          Some(ServiceResponse(executorName, Option(response), cacheTime))
        }

      case GET =>
        connector.doGet(host, path(journeyId, None), port, hc).map {
          response => {
            Some(ServiceResponse(executorName, Option(response), cacheTime))
          }
        }

      case _ => throw new Exception("Method not supported : " + executionType)
    }
  }

  private def getServiceConfig: Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception(s"No micro services configured for $serviceName."))
  }
  private def getConfigProperty(property: String): String = {
    getServiceConfig.getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }

  def buildJourneyQueryParam(journeyId: Option[String]) = journeyId.fold("")(id => s"?journeyId=$id")

}

trait ExecutorFactory {

  val feedback = DeskProFeedbackExecutor()
  val versionCheck = VersionCheckExecutor()
  val pushNotificationGetMessageExecutor = PushNotificationGetMessageExecutor()
  val pushNotificationGetCurrentMessagesExecutor = PushNotificationGetCurrentMessagesExecutor()

  val maxServiceCalls: Int

  val executors: Map[String, Executor] = Map(
    versionCheck.executionType -> versionCheck,
    feedback.executorName      -> feedback,
    pushNotificationGetMessageExecutor.executorName -> pushNotificationGetMessageExecutor,
    pushNotificationGetCurrentMessagesExecutor.executorName -> pushNotificationGetCurrentMessagesExecutor)

  def buildAndExecute(orchestrationRequest: OrchestrationRequest, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Seq[ServiceResponse]] = {
    val futuresSeq: Seq[Future[Option[ServiceResponse]]] = orchestrationRequest.request.map {
      request => (executors.get(request.serviceName), request.postRequest)
    }.map(item => item._1.get.execute(item._1.get.cacheTime, item._2, journeyId)
      .recover {
      case ex:Exception =>
        Logger.error(s"Failed to execute service ${item._1.get.executorName} with exception ${ex.getMessage}!")
        Some(ServiceResponse(item._1.get.executorName, None, None, Some(true)))
    })

    // Drop off Result's which returned None.
    Future.sequence(futuresSeq).map(item => item.flatten)
  }

}

case class VersionCheckExecutor() extends Executor {
  override val executorName: String = "version-check"

  override val executionType: String = POST
  override val serviceName: String = "customer-profile"
  override def path(journeyId: Option[String], data: Option[JsValue]) = "/profile/native-app/version-check"

  override def connector: GenericConnector = GenericConnector

  override val cacheTime: Option[Long] = None
}

case class DeskProFeedbackExecutor() extends Executor {
  override val executorName: String = "deskpro-feedback"

  override val executionType: String = POST
  override val serviceName: String = "deskpro-feedback"
  override def path(journeyId: Option[String], data: Option[JsValue]) = "/deskpro/feedback"

  override val cacheTime: Option[Long] = None

  override def connector: GenericConnector = GenericConnector
}

case class PushNotificationGetMessageExecutor() extends Executor {
  override val executorName: String = "push-notification-get-message"

  override val executionType: String = POST
  override val serviceName: String = "push-notification"
  override def path(journeyId: Option[String], data: Option[JsValue]) = {
    val messageId = data.flatMap(json => (json \ "messageId").asOpt[String]).getOrElse(throw new Exception("No messageId provided"))

    s"/messages/$messageId${buildJourneyQueryParam(journeyId)}"
  }

  override val cacheTime: Option[Long] = None

  override def connector: GenericConnector = GenericConnector
}

case class PushNotificationGetCurrentMessagesExecutor() extends Executor {
  override val executorName: String = "push-notification-get-current-messages"

  override val executionType: String = GET
  override val serviceName: String = "push-notification"
  override def path(journeyId: Option[String], data: Option[JsValue]) = "/messages/current"

  override val cacheTime: Option[Long] = None

  override def connector: GenericConnector = GenericConnector
}