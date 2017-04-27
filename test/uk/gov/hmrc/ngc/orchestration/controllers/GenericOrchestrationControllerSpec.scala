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

package uk.gov.hmrc.ngc.orchestration.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import org.scalatest.concurrent.Eventually._
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import play.api.test.Helpers._
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class GenericOrchestrationControllerSpec extends UnitSpec with WithFakeApplication with FileResource {


  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()
  val token = "Bearer 123456789"

  "Request without an auth token" should {
    "fail with a 400" in new TestGenericOrchestrationController {

      override val test_id: String = "400Fail"
      override val exception: Option[Exception] = None
      override val statusCode: Option[Int] = Option(400)
      override val mapping: Map[String, Boolean] = servicesSuccessMap
      override val response: JsValue = JsNull

      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)

      val fakeRequest = FakeRequest().withSession().withHeaders("Accept" -> "application/vnd.hmrc.1.0+json",
      "Authorization" -> "Some Header").withJsonBody(request)

      val result = await(controller.orchestrate(Nino("AB123456C"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe statusCode.get
    }
  }

  "Request without an Accept header" should {
    "fail with a 406" in new TestGenericOrchestrationController {

      override val test_id: String = "406Fail"
      override val exception: Option[Exception] = None
      override val statusCode: Option[Int] = Option(406)
      override val mapping: Map[String, Boolean] = servicesSuccessMap
      override val response: JsValue = JsNull

      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)

      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val result = await(controller.orchestrate(Nino("AB123456C"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe statusCode.get
    }
  }

  "POST test generic protected service " should {
    "execute service version-check posting the post request data" in new TestGenericOrchestrationController {

      override val test_id: String = "200Success"
      override val exception: Option[Exception] = None
      override val statusCode: Option[Int] = Option(200)
      override val mapping: Map[String, Boolean] = servicesSuccessMap
      override val response: JsValue = TestData.pollResponse

      val request: JsValue = Json.parse(findResource(s"/resources/generic/version-check-request.json").get)


      val fakeRequest = FakeRequest().withSession(
        "AuthToken" -> "Some Header"
      ).withHeaders(
        "Accept" -> "application/vnd.hmrc.1.0+json",
        "Authorization" -> "Some Header"
      ).withJsonBody(request)

      val result = await(controller.orchestrate(Nino("CS700100A"), Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe statusCode.get
      contentAsJson(result) shouldBe response
    }
  }
}
