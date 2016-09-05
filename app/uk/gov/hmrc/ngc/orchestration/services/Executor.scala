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

trait Journey {
  def logJourneyId(journeyId: Option[String]) = s"Native Error - ${journeyId.fold("no Journey id supplied")(id => id)}"
  def buildJourneyQueryParam(journeyId: Option[String]) = journeyId.fold("")(id => s"?journeyId=$id")
}

trait Executor extends Journey {

  implicit val hc = HeaderCarrier()
  val id: String
  val serviceName: String
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]]

  def retry(default: Option[Result] = None)(func: => Future[Option[Result]])(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {

    def retry: Future[Option[Result]] = {
      TimedEvent.delayedSuccess(2000, {
          Logger.info("Retry is being executed.")
          func.recover {
          case _ => default
        }})
    }.flatMap(r => r )

    func.map(res => Right(res)).recover {
      case ex:Exception => Left(Unit)
    }.flatMap {
      case Right(value: Option[Result]) => Future.successful(value)
      case Left(_) => retry
    }
  }

  private def getServiceConfig: Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception("No micro services configured."))
  }
  private def getConfigProperty(property: String): String = {
    getServiceConfig.getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
}

case class TaxSummary(connector: GenericConnector, journeyId: Option[String]) extends Executor with ResponseCode {
  override val id  = "taxSummary"
  override val serviceName: String = "personal-income"

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/$nino/tax-summary/$year${buildJourneyQueryParam(journeyId)}" , port, hc).map(res => {
      Some(Result(id, res))
    }).recover {
      case ex: Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve the tax-summary data and exception is ${ex.getMessage}!")
        throw ex
    }
  }
}

case class TaxCreditsSubmissionState(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id = "state"
  override val serviceName = "personal-income"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/tax-credits/submission/state/enabled${buildJourneyQueryParam(journeyId)}", port, hc).map(res =>
      Some(Result(id, JsObject(Seq("enableRenewals" -> Json.toJson(res).\("submissionState"))))))
      .recover {
        case ex: Exception =>
          // Return a default state which indicates renewals are disabled.
          Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve TaxCreditsSubmissionState and exception is ${ex.getMessage}! Default of enabled state is false!")
          Some(Result(id, JsObject(Seq("enableRenewals" -> JsBoolean(false)))))
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
      // Note: Fire and forget!
      connector.doPost(inputRequest, host, s"/push/registration${buildJourneyQueryParam(journeyId)}", port, hc)
    }
    Future.successful(None)
  }
}

case class TaxCreditSummary(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id: String = "taxCreditSummary"
  override val serviceName: String = "personal-income"

  def authenticateRenewal(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Boolean] = {
    connector.doGetRaw(host, s"/income/$nino/tax-credits/999999999999999/auth${buildJourneyQueryParam(journeyId)}", port, hc).map(r => r.status match {
      case 200 => true
      case _ => false
    }).recover {
      case ex: uk.gov.hmrc.play.http.NotFoundException => false
      case ex: Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve authenticateRenewal and exception is ${ex.getMessage}!")
        false
    }
  }

  def decision(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Boolean] = {

    def taxCreditDecision(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
      val defaultDecision = Option(Result(id, Json.parse( """{"showData":false}""")))
      retry(defaultDecision) {
        connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-decision${buildJourneyQueryParam(journeyId)}", port, hc).map(res => {
          Some(Result("decision", res))
        }).recover {
          case ex: uk.gov.hmrc.play.http.NotFoundException => Logger.error(s"${logJourneyId(journeyId)} - 404 returned for tax-credits-decision."); None
          case ex: Exception =>
            Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve tax-credits-decision and exception is ${ex.getMessage}!")
            // Note: Retry function will re-try exceptions.
            throw ex
        }
      }
    }

    taxCreditDecision(nino).map {
      case Some(result) => (result.jsValue \ "showData").as[Boolean]
      case _ => false
    }
  }

  def getTaxCreditSummary(nino:String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-summary${buildJourneyQueryParam(journeyId)}", port, hc).map(r => {
      Some(Result(id, r))
    }).recover {
      case ex:Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve tax-credits/tax-credits-summary and exception is ${ex.getMessage}!")
        None
    }
  }

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    val authenticateRenewalF = authenticateRenewal(nino)
    val decisionF = decision(nino)

    val taxCreditSummaryOutcome: Future[(Boolean, Option[Result])] = for {
      isAuthenticateRenewal <- authenticateRenewalF
      isDecision <- decisionF

      // IF isAuthenticateRenewal is true and isDecision is true, then can display tax-credits.
      // IF isAuthenticateRenewal is true and isDecision is false, then can display tax-credits but without summary data.
      // IF isAuthenticateRenewal is false do not display tax-credit-summary

      b <- if (isAuthenticateRenewal && isDecision) {
        getTaxCreditSummary(nino)
      } else {
        Future.successful(None)
      }
    } yield (isAuthenticateRenewal, b)

    taxCreditSummaryOutcome.map {
      case (true, None) => Some(Result(id, Json.obj())) // Tax-Credit-Summary tab can be displayed but no summary data returned.
      case (false, _) => None
      case (_, result) => result
    }.recover {
      case ex:Exception =>
        Logger.error(s"${logJourneyId(journeyId)} - Failed to orchestrate TaxCreditSummary. Exception is ${ex.getMessage}!")
        throw ex
    }
  }
}

case class Mandatory() extends Exception

case class Result(id: String, jsValue: JsValue)
