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

package uk.gov.hmrc.ngc.orchestration.services

import play.api.{Logger, Configuration, Play}
import play.api.libs.json._
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.controllers.ResponseCode
import uk.gov.hmrc.play.http.{Upstream4xxResponse, HeaderCarrier}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait Executor {

  implicit val hc = HeaderCarrier()
  val id: String
  val serviceName: String
  lazy val host: String = getConfigProperty("host")
  lazy val port: Int = getConfigProperty("port").toInt

  def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]]

  def logJourneyId(journeyId: Option[String]) = s"Native Error - ${journeyId.fold("no Journey id supplied")(id => id)}"

  def buildJourneyQueryParam(journeyId: Option[String]) = journeyId.fold("")(id => s"?journeyId=$id")

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
        // An empty JSON object indicates failed to retrieve the tax-summary.
        Some(Result(id, Json.obj()))
    }
  }
}

case class TaxCreditsSubmissionState(connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id = "state"
  override val serviceName = "personal-income"
  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/tax-credits/submission/state/enabled${buildJourneyQueryParam(journeyId)}", port, hc).map(res =>
      Some(Result(id, JsObject(Seq("enableRenewals" -> JsBoolean(res.\("submissionState").as[Boolean]))))))
      .recover {
        case ex: Exception =>
          // Return a default state which indicates renewals are disabled.
          Logger.error(s"${logJourneyId(journeyId)} - Failed to retrieve TaxCreditsSubmissionState and exception is ${ex.getMessage}! Default of enabled state is false!")
          Some(Result(id, JsObject(Seq("enableRenewals" -> JsBoolean(value = false)))))
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

case class TaxCreditSummary(authConnector:AuthConnector, connector: GenericConnector, journeyId: Option[String]) extends Executor {
  override val id: String = "taxCreditSummary"
  override val serviceName: String = "personal-income"

  def decision(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Boolean]] = {

    def taxCreditDecision(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
      Logger.warn(s"decision: HC received is ${hc.authorization} for Journey Id $journeyId")

      connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-decision${buildJourneyQueryParam(journeyId)}", port, hc).map(res => {
        Some(Result("decision", res))
      }).recover {
        case ex: uk.gov.hmrc.play.http.NotFoundException =>
          Logger.warn(s"${logJourneyId(journeyId)} - 404 returned for tax-credits-decision.")
          throw ex

        case ex: Upstream4xxResponse =>
          handle4xxException(ex)
          throw ex

        case ex: Exception =>
          Logger.warn(s"${logJourneyId(journeyId)} - Failed to retrieve tax-credits-decision and exception is ${ex.getMessage}!")
          throw ex
      }
    }

    taxCreditDecision(nino).map {
      case Some(result) => Some((result.jsValue \ "showData").as[Boolean])
      case _ => None
    }
  }

  def getTaxCreditSummary(nino: String)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    connector.doGet(host, s"/income/$nino/tax-credits/tax-credits-summary${buildJourneyQueryParam(journeyId)}", port, hc).map(r => {
      Some(Result(id, r))
    }).recover {

      case ex: Upstream4xxResponse =>
        handle4xxException(ex)
        None

      case ex: Exception =>
        Logger.warn(s"${logJourneyId(journeyId)} - Failed to retrieve tax-credits/tax-credits-summary and exception is ${ex.getMessage}!")
        None
    }
  }

  private def handle4xxException(ex:Upstream4xxResponse): Unit = {
    if (ex.upstreamResponseCode==401) {
      Logger.warn(s"${logJourneyId(journeyId)} - 401 returned for tax-credits-decision!")
      logAuth(journeyId)
    } else {
      Logger.warn(s"${logJourneyId(journeyId)} - ${ex.upstreamResponseCode} returned for tax-credits-decision.")
    }
  }

  private def logAuth(journeyId: Option[String]) = {
    authConnector.grantAccess().map(res => {
      Logger.warn(s"${logJourneyId(journeyId)} Authority record is good! - ")
    }).recover {
      case ex: Exception => Logger.error(s"Failed to verify the authority record! Exception is ${ex.getMessage} for journey ID $journeyId")
    }
  }

  override def execute(nino: String, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[Result]] = {
    val customerEligibleTaxCreditsDefault = Some(Result(id, Json.obj())) // TODO ... CHANGE NAME!!!

    def getSummaryData(decision:Option[Boolean]) : Future[Option[Result]] = {
      decision match {
        case Some(value:Boolean) if value =>
          // OK to retrieve the tax-credit-summary, default on failure.
          getTaxCreditSummary(nino).map( res => res.fold(customerEligibleTaxCreditsDefault){ summary => Some(summary) })

        case Some(value:Boolean) if !value =>
          // Tax-Credit-Summary tab can be displayed but no summary data returned.
          Future.successful(customerEligibleTaxCreditsDefault)

        case _ =>  Future.successful(None) // No data to return
      }
    }

    val decisionF = decision(nino)

    (for {
          decision <- decisionF
          taxCreditSummary <- getSummaryData(decision)
        } yield taxCreditSummary)
    .recover {
        case ex: Exception =>
          Logger.warn(s"${logJourneyId(journeyId)} - Failed to orchestrate TaxCreditSummary. Exception is ${ex.getMessage}!")
          None
    }
  }
}

case class Mandatory() extends Exception

case class Result(id: String, jsValue: JsValue)
