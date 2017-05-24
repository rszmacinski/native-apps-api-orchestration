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

import play.api.libs.json.{JsValue, Json}

case class OrchestrationRequest(request: Seq[ServiceRequest])

case class ServiceRequest(serviceName: String, postRequest: Option[JsValue])

case class OrchestrationResponse(response: Seq[ServiceResponse])

case class ServiceResponse(serviceName: String, responseData: Option[JsValue], cacheTime: Option[Long], failure: Option[Boolean]=None)

case class OrchestrationResult(preference: Option[JsValue], state: JsValue, taxSummary: JsValue, taxCreditSummary: Option[JsValue])

object OrchestrationRequest {
  implicit val requestFormat = Json.format[ServiceRequest]
  implicit val format = Json.format[OrchestrationRequest]
}

object ServiceRequest {
  implicit val format = Json.format[ServiceRequest]
}

object OrchestrationResponse {
  implicit val responseFormat = Json.format[ServiceResponse]
  implicit val format = Json.format[OrchestrationResponse]
}

object ServiceResponse {
  implicit val format = Json.format[ServiceResponse]
}


object OrchestrationResult {
  implicit val format = Json.format[OrchestrationResult]
}
