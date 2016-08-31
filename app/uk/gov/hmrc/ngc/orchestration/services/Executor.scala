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

  def logJourneyId(journeyId: Option[String]) = s"Native Error - ${journeyId.fold("no id")(id => id)}"

  def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]]

  def buildJourneyQueryParam(journeyId: Option[String]) = journeyId.fold("")(id => s"?journeyId=$id")

  def retry(default: Option[Result] = None)(func: => Future[Option[Result]])(implicit hc: HeaderCarrier, ex: ExecutionContext) = {
    def retry: Future[Option[Result]] = {
      TimedEvent.delayedSuccess(2000,{
          Logger.info(" Retry is being executed.")
          func.recover {
          case _ => default
        }})
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

case class TaxSummary(connector: GenericConnector, journeyId: Option[String]) extends Executor with ResponseCode {
  override val id  = "taxSummary"
  override val serviceName: String = "personal-income"

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/$nino/tax-summary/$year${buildJourneyQueryParam(journeyId)}" , port, hc).map(res => Some(Result(id, res))).recover {
      case ex: Exception => {
        Logger.error(s"Async - Failed to retrieve the tax-summary data and exception is ${ex}!")
        throw new Mandatory
      }
    }
  }
}

case class State(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id = "state"
  override val serviceName = "personal-income"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/tax-credits/submission/state/enabled${buildJourneyQueryParam(journeyId)}", port, hc).map(res =>  Some(Result(id, JsObject(Seq("enableRenewals" -> Json.toJson(res).\("submissionState")))))).recover{
      case ex: Exception => {
        Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve state and exception is ${ex}!")
        Some(Result(id, JsObject(Seq("enableRenewals" -> JsBoolean(false)))))
        }
    }
  }
}

case class PushRegistration(connector: GenericConnector, inputRequest:JsValue, journeyId: Option[String]) extends Executor {
  override val id = "pushRegistration"
  override val serviceName = "push-registration"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {

    if ((inputRequest \ "token").asOpt[String] == None) {
      Logger.info(s"${logJourneyId(journeyId)} - No token supplied!")
    } else {
      connector.doPost(inputRequest, host, s"/push/registration${buildJourneyQueryParam(journeyId)}", port, hc)
    }
    Future.successful(None)
  }
}

case class TaxCreditSummary(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id: String = "taxCreditSummary"
  override val serviceName: String = "personal-income"

  def authenticateRenewal(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Boolean] = {
    connector.doGetRaw(host, s"/income/$nino/tax-credits/999999999999999/auth${buildJourneyQueryParam(journeyId)}", port, hc).map(r => r.status match {
      case 200 => true
      case _ => false
    })
  }

  def decision(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Boolean] = {
    def taxCreditDecision(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
      val defaultDecision = Option(Result(id, Json.parse("""{"showData":false}""")))
      retry(defaultDecision) {
        connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-decision${buildJourneyQueryParam(journeyId)}", port, hc).map(res => {
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
    connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-summary${buildJourneyQueryParam(journeyId)}", port, hc).map(r => Some(Result(id, r)))
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
    }.recover {
      case ex:Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to orchestrate TaxCreditSummary and exception is ${ex}!")
        throw ex
    }
  }
}

case class Mandatory() extends Exception

case class Result(id: String, jsValue: JsValue)
