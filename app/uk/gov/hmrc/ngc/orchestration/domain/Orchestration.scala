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

package uk.gov.hmrc.ngc.orchestration.domain

import play.api.libs.functional.syntax._
import play.api.libs.json._


case class OrchestrationRequest(serviceRequest: Option[Seq[ExecutorRequest]] = None, eventRequest: Option[Seq[ExecutorRequest]] = None)

case class ExecutorRequest(name: String, data: Option[JsValue] = None)

case class OrchestrationResponse(serviceResponse: Option[Seq[ExecutorResponse]] = None, eventResponse: Option[Seq[ExecutorResponse]] = None)

case class ExecutorResponse(name: String, responseData: Option[JsValue] = None, cacheTime: Option[Long] = None, failure: Option[Boolean] = None)

case class OrchestrationResult(preference: Option[JsValue], state: JsValue, taxSummary: JsValue, taxCreditSummary: Option[JsValue])

object OrchestrationRequest {


  implicit val requestFormat = Json.format[ExecutorRequest]

  implicit val OrchestrationRequestReads: Reads[OrchestrationRequest] = onlyFields("serviceRequest", "eventRequest") andThen (
      (__ \ 'serviceRequest).readNullable[Seq[ExecutorRequest]] and
      (__ \ 'eventRequest).readNullable[Seq[ExecutorRequest]]
    )(OrchestrationRequest.apply _)

  private def onlyFields(allowed: String*): Reads[JsObject] =
    Reads.verifying(json => {!json.keys.isEmpty && json.keys.forall(allowed.contains)})

}



object ExecutorRequest {
  implicit val format = Json.format[ExecutorRequest]
}

object OrchestrationResponse {
  implicit val responseFormat = Json.format[ExecutorResponse]
  implicit val format = Json.format[OrchestrationResponse]
}

object ExecutorResponse {
  implicit val format = Json.format[ExecutorResponse]
}


object OrchestrationResult {
  implicit val format = Json.format[OrchestrationResult]
}
