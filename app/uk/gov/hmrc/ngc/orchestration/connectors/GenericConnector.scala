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

import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.ngc.orchestration.config.WSHttp
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost, HttpResponse}

import scala.concurrent.Future


trait GenericConnector {

  def http: HttpPost with HttpGet

  private def addAPIHeaders(hc:HeaderCarrier) = hc.withExtraHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  private def buildUrl(host:String, port:Int, path:String) =  s"""http://$host:$port$path"""

  def logHC(hc: HeaderCarrier, path:String) =   Logger.info(s"transport: HC received is ${hc.authorization} for path $path")


  def doGet(host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    logHC(hc, s"transport: HC received is ${hc.authorization} for path $path")
    http.GET[JsValue](buildUrl(host, port, path))
  }

  def doPost(json:JsValue, host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    logHC(hc, s"transport: HC received is ${hc.authorization} for path $path")
    http.POST[JsValue, JsValue](buildUrl(host, port, path), json)
  }

  def doGetRaw(host:String, path:String, port:Int, hc: HeaderCarrier): Future[HttpResponse] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    http.GET(buildUrl(host, port, path))
  }

}

object GenericConnector extends GenericConnector {
  override def http = WSHttp
}
