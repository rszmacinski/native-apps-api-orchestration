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

package uk.gov.hmrc.ngc.orchestration.connectors

import java.util.UUID

import play.api.Play
import play.api.libs.json.JsValue
import uk.gov.hmrc.ngc.orchestration.config.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{ForbiddenException, HeaderCarrier, HttpGet}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.ngc.orchestration.domain.Accounts
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

import scala.concurrent.{ExecutionContext, Future}


class NinoNotFoundOnAccount(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)
class AccountWithLowCL(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)
class AccountWithWeakCredStrength(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)

case class Authority(nino:Nino, cl:ConfidenceLevel, authId:String)

trait AuthConnector {

  import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel

  val serviceUrl: String

  def http: HttpGet

  def serviceConfidenceLevel: ConfidenceLevel

  val credStrengthStrong = "strong"

  def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = {
    http.GET(s"$serviceUrl/auth/authority") map {
      resp =>
        val json = resp.json
        val accounts = json \ "accounts"
        val utr = (accounts \ "sa" \ "utr").asOpt[String]
        val nino = (accounts \ "paye" \ "nino").asOpt[String]
        val journeyId = UUID.randomUUID().toString

        Accounts(nino.map(Nino(_)), utr.map(SaUtr(_)), upliftRequired(json), twoFactorRequired(json), journeyId)
    }
  }

  def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = {
    http.GET(s"$serviceUrl/auth/authority") map {
      resp => {
        val json = resp.json
        val cl = confirmConfiendenceLevel(json)
        val uri = (json \ "uri").as[String]
        val nino = (json \ "accounts" \ "paye" \ "nino").asOpt[String]

        if((json \ "accounts" \ "paye" \ "nino").asOpt[String].isEmpty)
          throw new NinoNotFoundOnAccount("The user must have a National Insurance Number")
        Authority(Nino(nino.get), ConfidenceLevel.fromInt(cl), uri)
      }
    }
  }

  private def confirmConfiendenceLevel(jsValue : JsValue) : Int = {
    val usersCL = (jsValue \ "confidenceLevel").as[Int]
    if (serviceConfidenceLevel.level > usersCL) {
      throw new ForbiddenException("The user does not have sufficient permissions to access this service")
    }
    usersCL
  }

  private def upliftRequired(jsValue : JsValue) = {
    val usersCL = (jsValue \ "confidenceLevel").as[Int]
    serviceConfidenceLevel.level > usersCL
  }

  private def twoFactorRequired(jsValue : JsValue) = {
    val credStrength = (jsValue \ "credentialStrength").as[String]
    credStrength != credStrengthStrong
  }


}

object AuthConnector extends AuthConnector with ServicesConfig {

  import play.api.Play.current

  val serviceUrl = baseUrl("auth")
  val http = WSHttp
  val serviceConfidenceLevel: ConfidenceLevel = ConfidenceLevel.fromInt(Play.configuration.getInt("controllers.confidenceLevel")
    .getOrElse(throw new RuntimeException("The service has not been configured with a confidence level")))
}
