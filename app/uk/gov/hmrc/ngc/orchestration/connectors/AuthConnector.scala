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

package uk.gov.hmrc.ngc.orchestration.connectors

import java.util.UUID
import uk.gov.hmrc.ngc.orchestration.config.WSHttp
import uk.gov.hmrc.ngc.orchestration.domain.{CredentialStrength, Accounts}
import play.api.Play
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel


class FailToMatchTaxIdOnAuth(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)
class NinoNotFoundOnAccount(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)
class AccountWithLowCL(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)
class AccountWithWeakCredStrength(message:String) extends uk.gov.hmrc.play.http.HttpException(message, 401)

trait AuthConnector {

  val serviceUrl: String

  def http: HttpGet

  def serviceConfidenceLevel: ConfidenceLevel

  def uuid = UUID.randomUUID().toString

    def accounts(journeyIdIn:Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = {
      http.GET(s"$serviceUrl/auth/authority") map {
        resp =>
          val json = resp.json
          val accounts = json \ "accounts"
          val utr = (accounts \ "sa" \ "utr").asOpt[String]
          val nino = (accounts \ "paye" \ "nino").asOpt[String]

          val journeyId = journeyIdIn.fold(uuid) { id => if (id.length==0) uuid else id }
          Accounts(nino.map(Nino(_)), utr.map(SaUtr(_)), upliftRequired(json), twoFactorRequired(json), journeyId)
      }
    }


  def grantAccess(taxId:Option[Nino])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] = {
    http.GET(s"$serviceUrl/auth/authority") map {
      resp => {
        val json = resp.json
        confirmConfiendenceLevel(json)
          val cl = confirmConfiendenceLevel(json)
          val uri = (json \ "uri").as[String]
          val nino = (json \ "accounts" \ "paye" \ "nino").asOpt[String]


        if (nino.isEmpty)
          throw new NinoNotFoundOnAccount("The user must have a National Insurance Number")

        if (taxId.nonEmpty && !taxId.get.value.equals(nino.get))
          throw new FailToMatchTaxIdOnAuth("The nino in the URL failed to match auth!")
        Authority(Nino(nino.get), ConfidenceLevel.fromInt(cl), uri)
      }
    }
  }

  private def confirmConfiendenceLevel(jsValue : JsValue) : Int = {
    val usersCL = (jsValue \ "confidenceLevel").as[Int]
    if (serviceConfidenceLevel.level > usersCL) {
      throw new AccountWithLowCL("The user does not have sufficient CL permissions to access this service")
    }
    usersCL
  }

  private def upliftRequired(jsValue : JsValue) = {
    val usersCL = (jsValue \ "confidenceLevel").as[Int]
    serviceConfidenceLevel.level > usersCL
  }

  private def confirmCredStrength(jsValue : JsValue) =
    if (twoFactorRequired(jsValue)) {
      throw new AccountWithWeakCredStrength("The user does not have sufficient credential strength permissions to access this service")
    }

  private def twoFactorRequired(jsValue : JsValue) = {
    val credStrength = (jsValue \ "credentialStrength").as[String]
    credStrength != CredentialStrength.Strong.name
  }

}

object AuthConnector extends AuthConnector with ServicesConfig {

  import play.api.Play.current

  val serviceUrl = baseUrl("auth")
  val http = WSHttp
  val serviceConfidenceLevel: ConfidenceLevel = ConfidenceLevel.fromInt(Play.configuration.getInt("controllers.confidenceLevel")
    .getOrElse(throw new RuntimeException("The service has not been configured with a confidence level")))
}

case class Authority(nino:Nino, cl:ConfidenceLevel, authId:String)
