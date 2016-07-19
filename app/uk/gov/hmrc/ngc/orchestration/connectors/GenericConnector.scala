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

import play.api.libs.json._
import uk.gov.hmrc.ngc.orchestration.StubWsHttp
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPost}

import scala.concurrent.Future


trait GenericConnector {

  def http: HttpPost with HttpGet

  private def addAPIHeaders(hc:HeaderCarrier) = hc
  private def buildUrl(host:String, port:Int, path:String) =  s"""http://$host:$port$path"""

  def doGet(host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    http.GET[JsValue](buildUrl(host, port, path))
  }

  def doPost(json:JsValue, host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    implicit val hcHeaders = addAPIHeaders(hc)
    http.POST[JsValue, JsValue](buildUrl(host, port, path), json)
  }
}

object GenericConnector extends GenericConnector {
  override def http = StubWsHttp
}