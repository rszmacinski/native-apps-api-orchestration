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

import play.api.libs.json.{JsNull, JsValue}
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.domain.{OrchestrationRequest, ServiceResponse}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


sealed trait Executor {
  val id: String
  val serviceName: String
  val executionType: String
  val executorName:String
  val path: String
  val POST = "POST"
  val GET = "GET"
  def connector: GenericConnector
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def execute(data: Option[JsValue])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[ServiceResponse]] = {
    executionType.toUpperCase match {
      case POST => {
        val postData = data.getOrElse(throw new Exception("No Post Data Provided!"))
        connector.doPost(postData, host, path, port, hc).map { response =>
          Some(ServiceResponse(id, executorName, Option(response), Option(1000)))
        }.recover {
          case ex: Exception =>
            Logger.error(s"Failed to execute service $serviceName with exception ${ex.getMessage}!")
            Some(ServiceResponse(id, executorName, Option(JsNull), Option(0)))
        }
      }
      case GET => {
        connector.doGet(host, path, port, hc).map {
          response => {
            Some(ServiceResponse(id, serviceName, Option(response), Option(1000)))
          }
        }
      }
      case _ => throw new Exception("Method not supported : " + executionType)
    }
  }

  private def getServiceConfig: Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception("No micro services configured."))
  }
  private def getConfigProperty(property: String): String = {
    getServiceConfig.getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }

  def buildJourneyQueryParam(journeyId: Option[String]) = journeyId.fold("")(id => s"?journeyId=$id")

}

trait ExecutorFactory {

  val executors: Map[String, Executor] = Map("version-check" -> VersionCheckExecutor())
  def buildAndExecute(orchestrationRequest: OrchestrationRequest)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Seq[ServiceResponse]] = {

    val futuresSeq: Seq[Future[Option[ServiceResponse]]] = orchestrationRequest.request.map {
      request => {
        if(!verifyServiceName(request.serviceName)) throw new Exception("Service is not supported!")
        (executors.get(request.serviceName), request.postRequest)
      }
    }.map(item => item._1.get.execute(item._2))

    // Drop off Result's which returned None.
    Future.sequence(futuresSeq).map(item => item.flatten)
  }

  protected def verifyServiceName(serviceName: String): Boolean = {
    !Play.current.configuration.getConfig(s"supported.generic.service.$serviceName").isEmpty
  }
}

case class VersionCheckExecutor() extends Executor {

  override val id: String = "Version Check"
  override val serviceName: String = "customer-profile"

  override val executionType: String = POST
  override val path: String = "/profile/native-app/version-check"

  override def connector: GenericConnector = GenericConnector

  override val executorName: String = "version-check"
}