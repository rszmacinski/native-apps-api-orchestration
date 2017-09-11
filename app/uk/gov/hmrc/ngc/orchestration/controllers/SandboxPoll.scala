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

import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.mvc.Cookie
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.msasync.repository.{AsyncRepository, TaskCachePersist}
import uk.gov.hmrc.ngc.orchestration.config.ConfiguredCampaigns
import uk.gov.hmrc.ngc.orchestration.domain.{ExecutorResponse, OrchestrationResponse}
import uk.gov.hmrc.ngc.orchestration.services.Result
import uk.gov.hmrc.play.asyncmvc.model.TaskCache

import scala.concurrent.Future

/**
  * Stubbed Sandbox poll
  */
trait SandboxPoll extends FileResource with ConfiguredCampaigns {
  val sandboxRepository = new AsyncRepository {
    val exception = new Exception("Repo should not be called in sandbox mode!")

    override def save(expectation: TaskCache, expire: Long): Future[DatabaseUpdate[TaskCachePersist]] = Future.failed(exception)

    override def findByTaskId(id: String): Future[Option[TaskCachePersist]] = Future.failed(exception)

    override def removeById(id: String): Future[Unit] = Future.failed(exception)
  }


  def buildResponse(name: String, path: Option[String]) : Option[ExecutorResponse] = {
    if (!path.isDefined)
      None
    else
      Some(ExecutorResponse(name, Some(Json.parse(findResource(path.get).get)), failure = Some(false)))
  }

  def pollSandboxResult(nino: Nino, generics: Option[Cookie]): AsyncResponse = {

    val asyncStatusJson = JsObject(Seq("code" -> JsString("complete")))
    val asyncStatus = Result("status", asyncStatusJson)

    if (generics.isDefined) {

      val serviceResponse: Seq[ExecutorResponse] = (for {
        name ← generics.get.value.split('|')
        json = name match {
          case "deskpro-feedback" ⇒ buildResponse(name, Some("/resources/generic/poll/feedback.json"))
          case "version-check"    ⇒ buildResponse(name, Some("/resources/generic/poll/version-check.json"))
          case "survey-widget"    ⇒ buildResponse(name, Some("/resources/generic/poll/survey-widget.json"))
          case "claimant-details" ⇒ buildResponse(name, Some("/resources/generic/poll/claimant-details.json"))
          case "push-notification-get-message" ⇒ buildResponse(name, Some("/resources/generic/poll/get-message.json"))
          case "push-notification-get-current-messages" ⇒ buildResponse(name, Some("/resources/generic/poll/get-current-messages.json"))
          case _ ⇒ buildResponse(name, None)
        }
      } yield json).filter(_.isDefined).map(_.get)

      val eventResponse: Seq[ExecutorResponse] = (for {
        name ← generics.get.value.split('|')
        json = name match {
          case "ngc-audit-event" ⇒ buildResponse(name, Some("/resources/generic/poll/audit-event.json"))
          case _ ⇒ buildResponse(name, None)
        }
      } yield json).filter(_.isDefined).map(_.get)


      val response = OrchestrationResponse(if (!serviceResponse.isEmpty) Some(serviceResponse) else None,
                                           if (!eventResponse.isEmpty)   Some(eventResponse)   else None)
      AsyncResponse(Json.obj("OrchestrationResponse" → Json.toJson[OrchestrationResponse](response)) ++asyncStatusJson, nino)
    }
    else {
      if (nino.equals(Nino("AB123456C"))) {
        val json = findResource(s"/resources/generic/version-check.json").get
        AsyncResponse(Json.obj("OrchestrationResponse" -> Json.parse(json)) ++ asyncStatusJson, nino)
      }
      else {
        val resource: Option[String] = findResource(s"/resources/getsummary/${nino}_2016.json")

        val stateJson = JsObject(Seq(
          "enableRenewals" -> JsBoolean(value = true)
        ))
        // Build the results based on the above stubbed data.
        val currentTime = (new LocalDate()).toDateTimeAtStartOfDay

        val taxSummary = Result("taxSummary", Json.parse(resource.get))

        val taxCreditSummary = Json.parse(findResource(s"/resources/taxcreditsummary/${nino.value}.json").get
          .replaceAll("date1", currentTime.plusWeeks(1).getMillis.toString)
          .replaceAll("date2", currentTime.plusWeeks(2).getMillis.toString)
          .replaceAll("date3", currentTime.plusWeeks(3).getMillis.toString)
          .replaceAll("date4", currentTime.plusWeeks(4).getMillis.toString)
          .replaceAll("date5", currentTime.plusWeeks(5).getMillis.toString)
          .replaceAll("date6", currentTime.plusWeeks(6).getMillis.toString)
          .replaceAll("date7", currentTime.plusWeeks(7).getMillis.toString)
          .replaceAll("date8", currentTime.plusWeeks(8).getMillis.toString))

        val taxCreditSummaryResult = Result("taxCreditSummary", taxCreditSummary)

        val campaigns = configuredCampaigns(hasData, Json.obj("taxCreditSummary" -> taxCreditSummary))
        val state = Result("state", stateJson)

        val jsonResponseAttributes = if (!campaigns.isEmpty) {
          val confCampaigns = Result("campaigns", Json.toJson(Json.toJson(campaigns)))
          Seq(taxSummary, taxCreditSummaryResult, state, confCampaigns, asyncStatus).map(b => Json.obj(b.id -> b.jsValue))
        } else {
          Seq(taxSummary, taxCreditSummaryResult, state, asyncStatus).map(b => Json.obj(b.id -> b.jsValue))
        }
        AsyncResponse(jsonResponseAttributes.foldLeft(Json.obj())((b, a) => b ++ a), nino)
      }
    }
  }


}
