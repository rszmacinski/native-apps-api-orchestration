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

package uk.gov.hmrc.ngc.orchestration.services

import java.util.UUID

import play.api.{Configuration, Play}
import uk.gov.hmrc.api.service.Auditor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.MicroserviceAuditConnector
import uk.gov.hmrc.ngc.orchestration.connectors.{AuthConnector, GenericConnector}
import uk.gov.hmrc.ngc.orchestration.domain.{Accounts, PreFlightCheckResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait OrchestrationService {

  def preFlightCheck() (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse]

  def theRest() (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[_]

  def getServiceConfig(serviceName: String): Configuration = {
    Play.current.configuration.getConfig(s"microservice.services.$serviceName").getOrElse(throw new Exception)
  }

  def getConfigProperty(serviceName: String, property: String): String = {
    getServiceConfig(serviceName).getString(property).getOrElse(throw new Exception(s"No service configuration found for $serviceName"))
  }
}

trait LiveOrchestrationService extends OrchestrationService with Auditor {

  def genericConnector: GenericConnector = GenericConnector
  def authConnector: AuthConnector = AuthConnector

  def preFlightCheck() (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    withAudit("preFlightCheck", Map.empty) {
      for {
      accounts <- authConnector.accounts()
      versionUpdate <- genericConnector.doGet(getConfigProperty("customer-profile","host"), "profile/native-app/version-check", getConfigProperty("customer-profile","port") toInt, hc)
      } yield PreFlightCheckResponse(true, accounts)
    }
  }

  def theRest()(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[_] = ???
}

object SandboxOrchestrationService extends OrchestrationService {

  private val nino = Nino("CS700100A")

  private val preFlightResponse = PreFlightCheckResponse(true, Accounts(Some(nino), None, false, false, UUID.randomUUID().toString))

  def preFlightCheck()(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[PreFlightCheckResponse] = {
    Future.successful(preFlightResponse)
  }

  def theRest()(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[_] = ???
}

object LiveOrchestrationService extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
}