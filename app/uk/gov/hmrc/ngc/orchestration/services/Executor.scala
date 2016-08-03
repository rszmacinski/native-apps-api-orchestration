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

package uk.gov.hmrc.ngc.orchestration.services

import play.api.{Logger, Configuration, Play}
import play.api.libs.json._
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.ResponseCode
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait Executor {

  implicit val hc = HeaderCarrier()
  val id: String
  val serviceName: String
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]]

  def retry(default: Option[Result] = None)(func: => Future[Option[Result]]) = {
    def retry: Future[Option[Result]] = {
      TimedEvent.delayedSuccess(2000,
        func.recover {
          case _ => default
        })
    }.flatMap(r => r)

    func.recover {
      case _ => None
    }.flatMap(r => r.fold(retry){ a => Future.successful(Some(a)) })
  }

  private def getServiceConfig(): Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception("No micro services configured."))
  }
  private def getConfigProperty(property: String): String = {
    getServiceConfig().getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
}

case class TaxSummary(connector: GenericConnector) extends Executor with ResponseCode {
  override val id  = "taxSummary"
  override val serviceName: String = "personal-income"

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/$nino/tax-summary/$year" , port, hc).map(res => Some(Result(id, res))).recover {
      case ex: Exception => {
        Logger.error("Failed to retrieve the tax-summary data!")
        throw new Mandatory
      }
    }
  }
}

case class State(connector: GenericConnector) extends Executor {
  override val id = "state"
  override val serviceName = "personal-income"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, "/income/tax-credits/submission/state", port, hc).map(res => Some(Result(id, res))).recover{
      case ex: Exception => Some(Result(id, JsObject(Seq("shuttered" -> JsBoolean(true), "inSubmissionPeriod" -> JsBoolean(false)))))
    }
  }
}

case class PushRegistration(connector: GenericConnector, inputRequest:JsValue) extends Executor {
  override val id = "pushRegistration"
  override val serviceName = "push-registration"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {

    if ((inputRequest \ "token").asOpt[String] == None) {
      Logger.info("No token supplied!")
    } else {
      connector.doPost(inputRequest, host, "/push/registration", port, hc)
    }
    Future.successful(None)
  }
}

case class TaxCreditSummary(connector: GenericConnector) extends Executor {
  override val id: String = "taxCreditSummary"
  override val serviceName: String = "personal-income"

  def authenticateRenewal(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Boolean] = {
    connector.doGetRaw(host, s"/income/$nino/tax-credits/999999999999999/auth", port, hc).map(r => r.status match {
      case 200 => true
      case _ => false
    })
  }

  def decision(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Boolean] = {
    def taxCreditDecision(nino:String): Future[Option[Result]] = {
      val defaultDecision = Option(Result(id, Json.parse("""{"showData":true}""")))
      retry(defaultDecision) {
        connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-decision", port, hc).map(res => {
          Some(Result("decision", res))
        })
      }
    }

    taxCreditDecision(nino) map {
      case Some(result) => (result.jsValue \ "showData").as[Boolean]
      case _ => false
    }
  }

  def getSummary(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Some[Result]] = {
    connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-summary", port, hc).map(r => Some(Result(id, r)))
  }

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    val authenticateRenewalF = authenticateRenewal(nino)
    val decisionF = decision(nino)

    val taxCreditSummaryOutcome: Future[(Boolean, Option[Result])] = for {
      isAuthenticateRenewal <- authenticateRenewalF
      isDecision <- decisionF

      b <- if (isAuthenticateRenewal) getSummary(nino) else Future.successful(None)
      // Store the active status and the optional tax credit summary.
    } yield (isAuthenticateRenewal, b)

    taxCreditSummaryOutcome.map {
      case (true, None) => Some(Result(id, Json.obj())) // Tax-Credit-Summary tab can be displayed but no summary data returned.
      case (false, _) => None
      case (_, result) => result
    }
  }
}

case class Mandatory() extends Exception

case class Result(id: String, jsValue: JsValue)
