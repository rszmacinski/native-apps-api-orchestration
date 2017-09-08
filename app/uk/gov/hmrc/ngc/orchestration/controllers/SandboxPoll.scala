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
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.msasync.repository.{AsyncRepository, TaskCachePersist}
import uk.gov.hmrc.ngc.orchestration.config.{Campaign, ConfiguredCampaigns}
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


  def pollSandboxResult(nino: Nino, generics: Option[Seq[String]]): AsyncResponse = {

    val asyncStatusJson = JsObject(Seq("code" -> JsString("complete")))
    val asyncStatus = Result("status", asyncStatusJson)

    if (generics.isDefined) {

      val servicesJson = Json.obj("serviceResponse" → generics.get.map { res ⇒ res.in
        res match {
          case "deskpro-feedback" ⇒ Json.parse(findResource("/resources/generic/poll/feedback.json").get)
          case "version-check"    ⇒ Json.parse(findResource("/resources/generic/poll/version-check.json").get)
          case "survey-widget"    ⇒ Json.parse(findResource("/resources/generic/poll/survey-widget.json").get)
          case "claimant-details" ⇒ Json.parse(findResource("/resources/generic/poll/claimant-details.json").get)
          case "push-notification-get-message" ⇒ Json.parse(findResource("/resources/generic/poll/get-message.json").get)
          case "push-notification-get-current-messages" ⇒ Json.parse(findResource("/resources/generic/poll/get-current-messages.json").get)
        }
      })
      val eventsJson = generics.get.map {
        _ match {
          case "ngc-audit-event"  ⇒ Json.parse(findResource("/resources/generic/audit-event.json").get)
        }
      }
      AsyncResponse(Json.obj("OrchestrationResponse" → servicesJson), nino)
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
