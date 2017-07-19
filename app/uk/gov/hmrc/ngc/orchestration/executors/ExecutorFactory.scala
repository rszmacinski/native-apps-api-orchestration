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

import play.api.libs.json.{JsValue, Json}
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.ngc.orchestration.config.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.domain._
import uk.gov.hmrc.play.audit.model.{Audit, DataEvent}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


sealed trait Executor[T >: ExecutorResponse] {
  val executionType: String
  val executorName:String
  val cacheTime: Option[Long]
  def execute(cacheTime: Option[Long], data: Option[JsValue], nino: Option[String], journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[T]]
}

trait ServiceExecutor extends Executor[ExecutorResponse] {
  val serviceName: String
  val POST = "POST"
  val GET = "GET"
  val cacheTime: Option[Long]
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def connector: GenericConnector
  def path(journeyId: Option[String], nino: Option[String], data: Option[JsValue]): String

  override def execute(cacheTime: Option[Long], data: Option[JsValue], nino :Option[String], journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ExecutorResponse]] = {
    executionType.toUpperCase match {
      case POST =>
        val postData = data.getOrElse(throw new Exception("No Post Data Provided!"))
        val result = connector.doPost(postData, host, path(journeyId, nino, data), port, hc)
        result.map { response =>
          Some(ExecutorResponse(executorName, Option(response), cacheTime))
        }

      case GET =>
        connector.doGet(host, path(journeyId, nino, None), port, hc).map {
          response => {
            Some(ExecutorResponse(executorName, Option(response), cacheTime))
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

trait EventExecutor extends Executor[ExecutorResponse]

trait ExecutorFactory {

  val feedback = DeskProFeedbackExecutor()
  val versionCheck = VersionCheckExecutor()
  val pushNotificationGetMessageExecutor = PushNotificationGetMessageExecutor()
  val pushNotificationGetCurrentMessagesExecutor = PushNotificationGetCurrentMessagesExecutor()
  val nativeAppSurveyWidget = WidgetSurveyDataServiceExecutor()

  val auditEventExecutor = AuditEventExecutor()

  val maxServiceCalls: Int
  val maxEventCalls: Int

  val serviceExecutors: Map[String, ServiceExecutor] = Map(
    versionCheck.executorName -> versionCheck,
    feedback.executorName      -> feedback,
    pushNotificationGetMessageExecutor.executorName -> pushNotificationGetMessageExecutor,
    pushNotificationGetCurrentMessagesExecutor.executorName -> pushNotificationGetCurrentMessagesExecutor,
    nativeAppSurveyWidget.executorName -> nativeAppSurveyWidget)

  val eventExecutors: Map[String, EventExecutor] = Map(auditEventExecutor.executorName -> auditEventExecutor)

  def buildAndExecute(orchestrationRequest: OrchestrationRequest, nino: Option[String] = None, journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[OrchestrationResponse] = {
    for{
      serviceResponse <- {
        if (orchestrationRequest.serviceRequest.isDefined) {
          execute[ExecutorResponse](orchestrationRequest.serviceRequest.get, nino, journeyId, serviceExecutors).map(Option(_))
        }
        else Future(None)
      }
      eventResponse <- {
        if(orchestrationRequest.eventRequest.isDefined) {
          execute[ExecutorResponse](orchestrationRequest.eventRequest.get, nino, journeyId, eventExecutors).map(Option(_))
        }
        else Future(None)
      }
    } yield (OrchestrationResponse(serviceResponse, eventResponse))
  }

  private def execute[T >: ExecutorResponse](request: Seq[ExecutorRequest], nino: Option[String], journeyId: Option[String], executors: Map[String, Executor[T]])(implicit hc: HeaderCarrier, ex: ExecutionContext) : Future[Seq[T]] = {
    val futuresSeq: Seq[Future[Option[T]]] = request.map {
      request => (executors.get(request.name), request.data)
    }.map(item => item._1.get.execute(item._1.get.cacheTime, item._2, nino, journeyId)
      .recover {
        case ex:Exception =>
          Logger.error(s"Failed to execute ${item._1.get.executorName} with exception ${ex.getMessage}!")
          Some(ExecutorResponse(item._1.get.executorName, None, None, Some(true)))
      })
    Future.sequence(futuresSeq).map(item => item.flatten)
  }

}

case class VersionCheckExecutor() extends ServiceExecutor {
  override val executorName: String = "version-check"

  override val executionType: String = POST
  override val serviceName: String = "customer-profile"
  override def path(journeyId: Option[String], nino: Option[String], data: Option[JsValue]) = "/profile/native-app/version-check"

  override def connector: GenericConnector = GenericConnector

  override val cacheTime: Option[Long] = None
}

case class DeskProFeedbackExecutor() extends ServiceExecutor {
  override val executorName: String = "deskpro-feedback"

  override val executionType: String = POST
  override val serviceName: String = "deskpro-feedback"
  override def path(journeyId: Option[String], nino: Option[String], data: Option[JsValue]) = "/deskpro/feedback"

  override val cacheTime: Option[Long] = None

  override def connector: GenericConnector = GenericConnector
}

case class PushNotificationGetMessageExecutor() extends ServiceExecutor {
  override val executorName: String = "push-notification-get-message"

  override val executionType: String = POST
  override val serviceName: String = "push-notification"
  override def path(journeyId: Option[String], nino: Option[String], data: Option[JsValue]) = {
    val messageId = data.flatMap(json => (json \ "messageId").asOpt[String]).getOrElse(throw new Exception("No messageId provided"))

    s"/messages/$messageId${buildJourneyQueryParam(journeyId)}"
  }

  override val cacheTime: Option[Long] = None

  override def connector: GenericConnector = GenericConnector
}

case class PushNotificationGetCurrentMessagesExecutor() extends ServiceExecutor {
  override val executorName: String = "push-notification-get-current-messages"

  override val executionType: String = GET
  override val serviceName: String = "push-notification"
  override def path(journeyId: Option[String], nino: Option[String], data: Option[JsValue]) = "/messages/current"

  override val cacheTime: Option[Long] = None

  override def connector: GenericConnector = GenericConnector
}

case class AuditEventExecutor() extends EventExecutor {

  override val executorName: String = "ngc-audit-event"
  override val executionType: String = "EVENT"
  override val cacheTime: Option[Long] = None

  val audit: Audit = new Audit("native-apps", MicroserviceAuditConnector)

  override def execute(cacheTime: Option[Long], data: Option[JsValue], nino: Option[String], journeyId: Option[String])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ExecutorResponse]] = {
    val nino: Option[String] = data.flatMap(json => (json \ "nino").asOpt[String])
    val auditType: Option[String] = data.flatMap(json => (json \ "auditType").asOpt[String])
    val valid = auditType.isDefined && nino.isDefined
    val responseData = if(valid) {
      val dataEvent = DataEvent("native-apps", auditType.get, tags = hc.toAuditTags("explicitAuditEvent", auditType.get),
                      detail = hc.toAuditDetails("nino" -> nino.get, "auditType" -> auditType.get))
      audit.sendDataEvent(dataEvent)
      None
    } else {
      Option(Json.parse("""{"error": "Bad Request"}"""))
    }
    Future(Option(ExecutorResponse(executorName, responseData = responseData, failure = Some(!valid))))
  }
}

case class WidgetSurveyDataServiceExecutor() extends ServiceExecutor {
  override val serviceName: String = "native-app-widget"
  override val cacheTime: Option[Long] = None
  override val executionType: String = POST
  override val executorName: String = "survey-widget"
  override def connector: GenericConnector = GenericConnector
  override def path(journeyId: Option[String], nino: Option[String], data: Option[JsValue]): String = s"/native-app-widget/${nino.get}/widget-data"

}