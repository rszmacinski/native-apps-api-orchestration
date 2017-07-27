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

package uk.gov.hmrc.ngc.orchestration.config

import play.api.Play
import play.api.libs.json.{JsObject, Json, Writes}


trait ConfiguredCampaigns {

  lazy val configs: List[Campaign] = List(
    if (Play.current.configuration.getBoolean("widget.help_to_save.enabled").getOrElse(false)) {
      def helpToSaveIntProperty(key: String) = Play.current.configuration.getInt(s"widget.help_to_save.$key").getOrElse(throw new RuntimeException(s"widget.help_to_save.$key"))
      def helpToSaveStringProperty(key: String) = Play.current.configuration.getString(s"widget.help_to_save.$key").getOrElse(throw new RuntimeException(s"widget.help_to_save.$key"))
      Campaign.enabled(campaignId = "HELP_TO_SAVE_1", helpToSaveIntProperty("min_views"), helpToSaveIntProperty("dismiss_days"), helpToSaveStringProperty("required_data"))
    } else {
      Campaign.disabled("HELP_TO_SAVE_1")
    }
  )

  def hasData(data: JsObject, campaign: Campaign): Boolean = {
    if(campaign.requiredData.isDefined){
      !data.\\(campaign.requiredData.get).isEmpty
    }
    else true
  }

  def configuredCampaigns(hasData: (JsObject, Campaign)=> Boolean, json: JsObject): List[Campaign] = {
    configs.filter(hasData(json, _))
  }
}

case class Campaign(campaignId: String, enabled: Boolean, minimumViews: Option[Int], dismissDays: Option[Int], requiredData: Option[String])

object Campaign {

  def enabled(campaignId: String, minimumViews: Int, dismissDays: Int, requiredData: String): Campaign = {
    Campaign(campaignId, enabled = true, Some(minimumViews), Some(dismissDays), Some(requiredData))
  }

  def disabled(campaignId: String): Campaign = {
    Campaign(campaignId, enabled = false, None, None, None)
  }

  implicit val writes: Writes[Campaign] = Json.writes[Campaign]
}