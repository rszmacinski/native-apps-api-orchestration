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

import play.api.libs.json.{JsBoolean, JsObject, JsString, Json}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.msasync.repository.{AsyncRepository, TaskCachePersist}
import uk.gov.hmrc.ngc.orchestration.services.Result
import uk.gov.hmrc.play.asyncmvc.model.TaskCache

import scala.concurrent.Future

/**
 * Stubbed Sandbox poll
 */
trait SandboxPoll extends FileResource {

  val sandboxRepository = new AsyncRepository {
    val exception = new Exception("Repo should not be called in sandbox mode!")

    override def save(expectation: TaskCache, expire: Long): Future[DatabaseUpdate[TaskCachePersist]] = Future.failed(exception)

    override def findByTaskId(id: String): Future[Option[TaskCachePersist]] = Future.failed(exception)

    override def removeById(id: String): Future[Unit] = Future.failed(exception)
  }

  def pollSandboxResult(nino:Nino): AsyncResponse =  {
    val asyncStatusJson = JsObject(Seq("code" -> JsString("complete")))
    if (nino.equals(Nino("AB123456C"))) {
      val json = findResource(s"/resources/generic/version-check.json").get
      AsyncResponse(Json.obj("OrchestrationResponse" -> Json.parse(json)) ++ asyncStatusJson, nino)
    }
    else {
      val resource: Option[String] = findResource(s"/resources/getsummary/${nino.value}_2016.json")
      val stateJson = JsObject(Seq("enableRenewals" -> JsBoolean(value = true)))

      // Build the results based on the above stubbed data.
      val taxSummary = Result("taxSummary",Json.parse(resource.get))
      val taxCreditSummary = Result("taxCreditSummary", Json.parse(findResource(s"/resources/taxcreditsummary/${nino.value}.json").get))
      val state = Result("state", stateJson)
      val asyncStatus = Result("status", asyncStatusJson)

      val jsonResponseAttributes = Seq(taxSummary, taxCreditSummary, state, asyncStatus).map(b => Json.obj(b.id -> b.jsValue))
      AsyncResponse(jsonResponseAttributes.foldLeft(Json.obj())((b, a) => b ++ a), nino)
    }
  }

}
