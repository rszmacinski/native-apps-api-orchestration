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

package uk.gov.hmrc.ngc.orchestration.controllers


import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json._
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, OrchestrationResult, PreFlightCheckResponse, RenewalReference}
import uk.gov.hmrc.ngc.orchestration.services.SandboxOrchestrationService._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}


class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  override lazy val fakeApplication = FakeApplication()

  "preFlightCheck live controller " should {

    "return the PreFlightCheckResponse" in new Success {
      val result = await(controller.preFlightCheck()(emptyRequestWithHeader))

      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, "102030394AAA")))
    }

    "return 401 result with json status detailing no nino on authority" in new AuthWithoutNino {
      testNoNINO(controller.preFlightCheck()(emptyRequestWithHeader))
    }

    "return 200 result with json status detailing low CL on authority" in new AuthWithLowCL {
      val result = await(controller.preFlightCheck()(emptyRequestWithHeader))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, true, false, "102030394AAA")))
    }

    "return 200 result with json status detailing weak cred strength on authority" in new AuthWithWeakCreds {
      val result = await(controller.preFlightCheck()(emptyRequestWithHeader))

      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, true, "102030394AAA")))
    }

    "return status code 406 when the headers are invalid" in new Success {
      val result = await(controller.preFlightCheck()(emptyRequest))
      status(result) shouldBe 406
    }

    "preFlightCheck sandbox controller " should {

      "return the PreFlightCheckResponse from a resource" in new SandboxSuccess {
        val result = await(controller.preFlightCheck()(emptyRequestWithHeader))

        status(result) shouldBe 200
        val journeyIdRetrieve: String = (contentAsJson(result) \ "accounts" \ "journeyId").as[String]
        contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, journeyIdRetrieve)))
      }

      "return status code 406 when the headers are invalid" in new Success {
        val result = await(controller.preFlightCheck()(emptyRequest))

        status(result) shouldBe 406
      }
    }

    "startup sandbox controller" should {
      "return a OrchestrationResult " in new SandboxSuccess {

        val result = await(controller.startup(nino, year=2016, RenewalReference("something"))(emptyRequestWithHeader))
        status(result) shouldBe 200

        val resource: JsValue = Json.toJson(findResource(s"/resources/getsummary/${nino.value}_2016.json").get)
        val emailPreferences  = JsObject(Seq("email" -> JsString("name@email.co.uk"), "status" -> JsString("verified")))
        val preferences: JsValue = JsObject(Seq("digital" -> JsBoolean(true), "email" -> emailPreferences))
        val person : JsValue = JsObject(Seq("firstName" -> JsString("Jeremy"), "middleName" -> JsString("Frank"),
          "lastName" -> JsString("Loops"), "initials" -> JsString("JS"), "title" -> JsString("Mr"), "sex" -> JsString("Male")))
        val taxCreditSummary: JsValue = Json.toJson(findResource(s"/resources/taxcreditsummary/${nino.value}.json").get)

        contentAsJson(result) shouldBe Json.toJson(OrchestrationResult(Option(preferences), resource, Option(taxCreditSummary)))
      }

      "return status code 406 when the headers are invalid" in new Success {

        val result = await(controller.startup(nino, 2016, RenewalReference("Something"))(emptyRequest))
        status(result) shouldBe 406
      }
    }
  }
}
