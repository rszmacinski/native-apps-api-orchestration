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


import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Milliseconds, Span}
import play.api.libs.json._
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.ngc.orchestration.connectors.GenericConnector
import uk.gov.hmrc.ngc.orchestration.controllers.NativeAppsOrchestrationController
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.{BadRequestException, Upstream5xxResponse, SessionKeys, Upstream4xxResponse}
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException, ServiceUnavailableException}

//import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, OrchestrationResult, PreFlightCheckResponse}
import uk.gov.hmrc.ngc.orchestration.services.SandboxOrchestrationService._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


sealed trait ExceptionType extends Exception
case class Mandatory(id:Int) extends ExceptionType
case class Optional(id:Int) extends ExceptionType



class OrchestrationControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures with Eventually {


  override lazy val fakeApplication = FakeApplication()

//*
  //      val taxSummary = Try(doGet("personal-income", s"income/$nino/tax-summary/$year")(hc))
  //      taxSummary match {
  //        case Success(taxSummary) => {
  //          doPost("push-registration", "push/registration", JsNull)(hc)
  //          val defaultState = JsObject(Seq("shuttered" -> JsBoolean(true), "inSubmissionPeriod" -> JsBoolean(false)))
  //          for {
  //            preferences <- doGet("customer-profile", "profile/preferences")(hc)
  //            state <- doGet("personal-income", "income/tax-credits/submission/state")(hc)
  //            taxCreditSummary <- doGet("personal-income", s"income/$nino/tax-credits/tax-credits-summary")(hc)
  //            taxCreditDecision <- doGet("personal-income", s"income/$nino/tax-credits/tax-credits-decision")(hc)
  //            renewal <- doGet("personal-income", s"income/$nino/tax-credits/999999999999999/auth")(hc)




  "TODO " should {


//==================================

    case class Mandatory(id:Int) extends Exception
    case class Result(id:String, jsValue: JsValue)

    sealed trait Execution {
      val id:String
      implicit val hc = HeaderCarrier()

      def execute(nino:String, year:Int):Future[Option[Result]]

      def retry(func: => Future[Option[Result]]) = {

        def retry: Future[Option[Result]] = TimedEvent.delayedSuccess(3000, func).flatMap(res => res)

        // Retry on failure. -TODO...WHAT ARE THE FAILURES? DON'T WANT TO RETRY FOR ALL?
        func.map { res => res }.recover {
          case _ =>  None
        }.fold(retry){ Some(_) }
      }
    }
//==================================

    // Define the execution objects.

    case class TaxSummary(controller:NativeAppsOrchestrationController) extends Execution {
      override val id = "TaxSummary"

      def execute(nino:String, year:Int): Future[Option[Result]] = {
        retry {
          controller.service.genericConnector.doGet("personal-income", s"income/$nino/tax-summary/$year", 1234, hc).map(res => Some(Result(id, res)))
            .recover {

// TODO: COULD CAPTURE SPECIFIC EXCEPTIONS ARE MARK IF RETRY SHOULD BE PERFORMED!!! NOT ALL EXCEPTIONS SHOULD BE A RETRY!
            case ex: Exception => throw new Mandatory(1)  // This is a mandatory service. Throw exception.
          }
        }
      }
    }

    // TODO...PASS CONNECTOR AND NOT CONTROLLER!!!
    case class Preferences(controller:NativeAppsOrchestrationController) extends Execution {
      override val id = "Preferences"

      def execute(nino:String, year:Int): Future[Option[Result]] = {
        controller.service.genericConnector.doGet("customer-profile", "profile/preferences",3456,hc).map(res => Some(Result(id,res))) recover {
          // TODO...ADD METRICS!!! Swollow exception!
          // Optional data - return none when error occurs.
          case ex:Exception => None
        }
      }
    }


//==================================
//==================================
//==================================


trait ExampleRUNNER extends TestController {

  // EXAMPLE SERVICE LAYER CODE...

  // FIRES OFF ALL THE SERVICES
  def runner: Future[Seq[JsObject]] = {

    // List of functions to execute. Common signature - pass NINO and tax year!
    val futuresSeq = Seq(TaxSummary(controller), Preferences(controller)).map(item => item.execute("CS700100A", 2016))

    // Drop the None responses for optional functions which returned nothing.
    val res: Future[Seq[Result]] = Future.sequence(futuresSeq).map(item => item.flatMap(a => a))

    // Build result.
    for {
      a <- res
      b <- a.seq
    } yield (Json.obj(b.id -> b.jsValue))

  }

  def service(): JsObject = {
    // Calls functions and build response. Handling any errors where the attribute name if defined!
    val result = runner.map(item => item).recover {
      // TODO: RETURN AN ERROR ATTRIBUTE! always return 200 with structure - OPTIONAL AND INDICATES ERROR!!!
      case ex: Mandatory => Seq(Json.obj("ERROR" -> "MANDATORY"))
      case _ => Seq(Json.obj("ERROR" -> "FATAL ERROR"))
    }

    // BUILD THE RESULT JSON DYNAMICALLY!!!
    val res: JsObject = result.foldLeft(Json.obj())((b,a) => b ++ a)
    // DEBUG...
    res.map(resp => println(" RESP IS " + resp))
    res
  }

  def test = {
    val TESTING: Future[JsObject] = { service }

    // RETURNS FAILURE...
    eventually(Timeout(Span(95000, Milliseconds)), Interval(Span(2, Seconds))) {
      await(TESTING)
    }
  }

}

trait TestFailure extends ExampleRUNNER with TODO
trait TestWithOneFailureAndOneSuccess extends ExampleRUNNER with TODOFailWithRetrySuccess

    "One Failure Second Success" in new TestFailure {
      val result: JsObject = test
      // CALL COUJNT iS 2 AND SECOND IS SUCCESS!!!
      invokeCount shouldBe 2
      // check failure response
      result shouldBe Json.parse("""{"ERROR":"MANDATORY"}""")
    }

    "FAILURE" in new TestWithOneFailureAndOneSuccess {
      test
      // CALL COUNT iS 2 AND SECOND IS SUCCESS!!!
      invokeCount shouldBe 2
      // check success response
    }


    //val ex = Example {
//  override def controller = this.controller
//}

//      def ex = Future.successful({println(" EXECUTED") ;ExceptionA})
//      def exA = Future.successful({println(" EXECUTED A") ;ExceptionA})

//    }

  }



//  "preFlightCheck live controller " should {
//
//    "return the PreFlightCheckResponse" in new Success {
//      val result = await(controller.preFlightCheck()(emptyRequestWithHeader))
//
//      status(result) shouldBe 200
//      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, "102030394AAA")))
//    }
//
//    "return 401 result with json status detailing no nino on authority" in new AuthWithoutNino {
//      testNoNINO(controller.preFlightCheck()(emptyRequestWithHeader))
//    }
//
//    "return 200 result with json status detailing low CL on authority" in new AuthWithLowCL {
//      val result = await(controller.preFlightCheck()(emptyRequestWithHeader))
//      status(result) shouldBe 200
//      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, true, false, "102030394AAA")))
//    }
//
//    "return 200 result with json status detailing weak cred strength on authority" in new AuthWithWeakCreds {
//      val result = await(controller.preFlightCheck()(emptyRequestWithHeader))
//
//      status(result) shouldBe 200
//      contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, true, "102030394AAA")))
//    }
//
//    "return status code 406 when the headers are invalid" in new Success {
//      val result = await(controller.preFlightCheck()(emptyRequest))
//      status(result) shouldBe 406
//    }
//
//    "startup live controller " should {
//
//      "return the OrchestrationResult" in new Success {
//        val result = await(controller.startup(nino)(emptyRequestWithHeader))
//
//        val preferences = JsObject(Seq("digital" -> JsBoolean(true), "email" -> JsObject(Seq("email" -> JsString("name@email.co.uk"), "status" -> JsString("verified")))))
//        val taxSummary = Json.parse(findResource(s"/resources/getsummary/${nino.value}_2016.json").get)
//        val state = JsObject(Seq("shuttered" -> JsBoolean(true), "inSubmissionPeriod" -> JsBoolean(false)))
//        val taxCreditSummary = Json.parse(findResource(s"/resources/taxcreditsummary/${nino.value}.json").get)
//
//        status(result) shouldBe 200
//        contentAsJson(result) shouldBe Json.toJson(OrchestrationResult(preferences.asOpt[JsValue], state , taxSummary, taxCreditSummary.asOpt[JsValue]))
//      }
//
//      "return 401 result with json status detailing no nino on authority" in new AuthWithoutNino {
//        testNoNINO(controller.startup(nino)(emptyRequestWithHeader))
//      }
//
//      "throw an error when there is no taxSummary" in new Success {
//
//        val result = await(controller.startup(nino)(emptyRequestWithHeader))
//      }
//    }
//
//    "preFlightCheck sandbox controller " should {
//
//      "return the PreFlightCheckResponse from a resource" in new SandboxSuccess {
//        val result = await(controller.preFlightCheck()(emptyRequestWithHeader))
//
//        status(result) shouldBe 200
//        val journeyIdRetrieve: String = (contentAsJson(result) \ "accounts" \ "journeyId").as[String]
//        contentAsJson(result) shouldBe Json.toJson(PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, journeyIdRetrieve)))
//      }
//
//      "return status code 406 when the headers are invalid" in new Success {
//        val result = await(controller.preFlightCheck()(emptyRequest))
//
//        status(result) shouldBe 406
//      }
//    }
//
//    "startup sandbox controller" should {
//      "return a OrchestrationResult " in new SandboxSuccess {
//
//        val result = await(controller.startup(nino)(emptyRequestWithHeader))
//        status(result) shouldBe 200
//
//        val resource: JsValue = Json.toJson(findResource(s"/resources/getsummary/${nino.value}_2016.json").get)
//        val emailPreferences  = JsObject(Seq("email" -> JsString("name@email.co.uk"), "status" -> JsString("verified")))
//        val preferences: JsValue = JsObject(Seq("digital" -> JsBoolean(true), "email" -> emailPreferences))
//        val person : JsValue = JsObject(Seq("firstName" -> JsString("Jeremy"), "middleName" -> JsString("Frank"),
//          "lastName" -> JsString("Loops"), "initials" -> JsString("JS"), "title" -> JsString("Mr"), "sex" -> JsString("Male")))
//        val taxCreditSummary: JsValue = Json.toJson(findResource(s"/resources/taxcreditsummary/${nino.value}.json").get)
//        val defaultState = JsObject(Seq("shuttered" -> JsBoolean(true), "inSubmissionPeriod" -> JsBoolean(false)))
//        contentAsJson(result) shouldBe Json.toJson(OrchestrationResult(Option(preferences), defaultState, resource, Option(taxCreditSummary)))
//      }
//
//      "return status code 406 when the headers are invalid" in new Success {
//
//        val result = await(controller.startup(nino)(emptyRequest))
//        status(result) shouldBe 406
//      }
//    }
//  }
}
