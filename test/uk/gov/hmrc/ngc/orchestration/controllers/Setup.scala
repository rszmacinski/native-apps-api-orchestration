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

import java.util.UUID

import play.api.libs.json._
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.mvc.Results.Status
import uk.gov.hmrc.api.controllers.ErrorNotFound
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.ngc.orchestration.{MicroserviceAuditConnector, StubWsHttp}
import uk.gov.hmrc.ngc.orchestration.connectors._
import uk.gov.hmrc.ngc.orchestration.controllers.action.{AccountAccessControl, AccountAccessControlCheckOff, AccountAccessControlWithHeaderCheck}
import uk.gov.hmrc.ngc.orchestration.domain.Accounts
import uk.gov.hmrc.ngc.orchestration.services.{LiveOrchestrationService, OrchestrationService, SandboxOrchestrationService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.ConfidenceLevel
import uk.gov.hmrc.play.http.{ServiceUnavailableException, HeaderCarrier, HttpGet, HttpPost}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

trait Setup {
  implicit val hc = HeaderCarrier()

  val journeyId = UUID.randomUUID().toString
  val emptyRequest = FakeRequest()
  val emptyRequestWithHeader = FakeRequest().withHeaders("Accept" -> "application/vnd.hmrc.1.0+json")

  val nino = Nino("CS700100A")
  val testAccount = Accounts(Some(nino), None, false, false,"102030394AAA")
  val testTaxSummary = Json.parse("""{
                                    |  "taxSummaryDetails": {
                                    |    "nino": "CS700100A",
                                    |    "version": 154,
                                    |    "increasesTax": {
                                    |      "incomes": {
                                    |        "taxCodeIncomes": {
                                    |          "employments": {
                                    |            "taxCodeIncomes": [
                                    |              {
                                    |                "name": "Sainsburys",
                                    |                "taxCode": "1100L",
                                    |                "employmentId": 2,
                                    |                "employmentPayeRef": "BT456",
                                    |                "employmentType": 1,
                                    |                "incomeType": 0,
                                    |                "employmentStatus": 1,
                                    |                "tax": {
                                    |                  "totalIncome": 18900,
                                    |                  "totalTaxableIncome": 7900,
                                    |                  "totalTax": 1580,
                                    |                  "potentialUnderpayment": 0,
                                    |                  "taxBands": [
                                    |                    {
                                    |                      "income": 7900,
                                    |                      "tax": 1580,
                                    |                      "lowerBand": 0,
                                    |                      "upperBand": 32000,
                                    |                      "rate": 20.00
                                    |                    },
                                    |                    {
                                    |                      "income": 0,
                                    |                      "tax": 0,
                                    |                      "lowerBand": 32000,
                                    |                      "upperBand": 150000,
                                    |                      "rate": 40.00
                                    |                    },
                                    |                    {
                                    |                      "income": 0,
                                    |                      "tax": 0,
                                    |                      "lowerBand": 150000,
                                    |                      "upperBand": 0,
                                    |                      "rate": 45.00
                                    |                    }
                                    |                  ],
                                    |                  "allowReliefDeducts": 179.00
                                    |                },
                                    |                "worksNumber": "1",
                                    |                "jobTitle": " ",
                                    |                "startDate": "2008-04-06",
                                    |                "income": 7900,
                                    |                "otherIncomeSourceIndicator": false,
                                    |                "isEditable": true,
                                    |                "isLive": true,
                                    |                "isOccupationalPension": false,
                                    |                "isPrimary": true
                                    |              }
                                    |            ],
                                    |            "totalIncome": 18900,
                                    |            "totalTax": 1580,
                                    |            "totalTaxableIncome": 7900
                                    |          },
                                    |          "hasDuplicateEmploymentNames": false,
                                    |          "totalIncome": 18900,
                                    |          "totalTaxableIncome": 7900,
                                    |          "totalTax": 1580
                                    |        },
                                    |        "noneTaxCodeIncomes": {
                                    |          "totalIncome": 0
                                    |        },
                                    |        "total": 18900
                                    |      },
                                    |      "total": 18900
                                    |    },
                                    |    "decreasesTax": {
                                    |      "personalAllowance": 11000,
                                    |      "personalAllowanceSourceAmount": 11000,
                                    |      "paTapered": false,
                                    |      "total": 11000
                                    |    },
                                    |    "totalLiability": {
                                    |      "nonSavings": {
                                    |        "totalIncome": 18900,
                                    |        "totalTaxableIncome": 7900,
                                    |        "totalTax": 1580,
                                    |        "taxBands": [
                                    |          {
                                    |            "income": 7900,
                                    |            "tax": 1580,
                                    |            "lowerBand": 0,
                                    |            "upperBand": 32000,
                                    |            "rate": 20.00
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 32000,
                                    |            "upperBand": 150000,
                                    |            "rate": 40.00
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 150000,
                                    |            "upperBand": 0,
                                    |            "rate": 45.00
                                    |          }
                                    |        ],
                                    |        "allowReliefDeducts": 11000
                                    |      },
                                    |      "mergedIncomes": {
                                    |        "totalIncome": 18900,
                                    |        "totalTaxableIncome": 7900,
                                    |        "totalTax": 1580,
                                    |        "taxBands": [
                                    |          {
                                    |            "income": 93,
                                    |            "tax": 0.00,
                                    |            "lowerBand": 0,
                                    |            "upperBand": 5000,
                                    |            "rate": 0.00
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 5000,
                                    |            "upperBand": 32000,
                                    |            "rate": 7.50
                                    |          },
                                    |          {
                                    |            "income": 7900,
                                    |            "tax": 1580,
                                    |            "lowerBand": 0,
                                    |            "upperBand": 32000,
                                    |            "rate": 20.00
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 32000,
                                    |            "upperBand": 150000,
                                    |            "rate": 32.50
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 150000,
                                    |            "upperBand": 0,
                                    |            "rate": 38.10
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 32000,
                                    |            "upperBand": 150000,
                                    |            "rate": 40.00
                                    |          },
                                    |          {
                                    |            "income": 0,
                                    |            "tax": 0,
                                    |            "lowerBand": 150000,
                                    |            "upperBand": 0,
                                    |            "rate": 45.00
                                    |          }
                                    |        ],
                                    |        "allowReliefDeducts": 11000
                                    |      },
                                    |      "totalLiability": 7900,
                                    |      "totalTax": 1580,
                                    |      "totalTaxOnIncome": 1580,
                                    |      "underpaymentPreviousYear": 0,
                                    |      "outstandingDebt": 0,
                                    |      "childBenefitAmount": 0,
                                    |      "childBenefitTaxDue": 0,
                                    |      "liabilityReductions": {
                                    |        "enterpriseInvestmentSchemeRelief": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        },
                                    |        "concessionalRelief": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        },
                                    |        "maintenancePayments": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        },
                                    |        "doubleTaxationRelief": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        }
                                    |      },
                                    |      "liabilityAdditions": {
                                    |        "excessGiftAidTax": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        },
                                    |        "excessWidowsAndOrphans": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        },
                                    |        "pensionPaymentsAdjustment": {
                                    |          "codingAmount": 0,
                                    |          "amountInTermsOfTax": 0
                                    |        }
                                    |      }
                                    |    },
                                    |    "extensionReliefs": {
                                    |      "giftAid": {
                                    |        "sourceAmount": 0,
                                    |        "reliefAmount": 0
                                    |      },
                                    |      "personalPension": {
                                    |        "sourceAmount": 0,
                                    |        "reliefAmount": 0
                                    |      }
                                    |    },
                                    |    "taxCodeDetails": {
                                    |      "employment": [
                                    |        {
                                    |          "id": 2,
                                    |          "name": "Sainsburys",
                                    |          "taxCode": "1100L"
                                    |        }
                                    |      ],
                                    |      "taxCode": [
                                    |        {
                                    |          "taxCode": "L"
                                    |        }
                                    |      ],
                                    |      "taxCodeDescriptions": [
                                    |        {
                                    |          "taxCode": "1100L",
                                    |          "name": "Sainsburys",
                                    |          "taxCodeDescriptors": [
                                    |            {
                                    |              "taxCode": "L"
                                    |            }
                                    |          ]
                                    |        }
                                    |      ],
                                    |      "deductions": [
                                    |
                                    |      ],
                                    |      "allowances": [
                                    |        {
                                    |          "description": "Tax Free Amount",
                                    |          "amount": 11000,
                                    |          "componentType": 0
                                    |        }
                                    |      ],
                                    |      "splitAllowances": false,
                                    |      "total": 0
                                    |    }
                                    |  },
                                    |  "baseViewModel": {
                                    |    "estimatedIncomeTax": 1580,
                                    |    "taxableIncome": 7900,
                                    |    "taxFree": 11000,
                                    |    "personalAllowance": 11000,
                                    |    "hasTamc": false,
                                    |    "taxCodesList": [
                                    |      "1100L"
                                    |    ],
                                    |    "hasChanges": false
                                    |  },
                                    |  "estimatedIncomeWrapper": {
                                    |    "estimatedIncome": {
                                    |      "increasesTax": true,
                                    |      "incomeTaxEstimate": 1580,
                                    |      "incomeEstimate": 18900,
                                    |      "taxFreeEstimate": 11000,
                                    |      "taxRelief": false,
                                    |      "taxCodes": [
                                    |        "1100L"
                                    |      ],
                                    |      "potentialUnderpayment": false,
                                    |      "additionalTaxTable": [
                                    |
                                    |      ],
                                    |      "additionalTaxTableTotal": "0.00",
                                    |      "reductionsTable": [
                                    |
                                    |      ],
                                    |      "reductionsTableTotal": "-0.00",
                                    |      "graph": {
                                    |        "id": "taxGraph",
                                    |        "bands": [
                                    |          {
                                    |            "colour": "TaxFree",
                                    |            "barPercentage": 58.21,
                                    |            "tablePercentage": "0",
                                    |            "income": 11000,
                                    |            "tax": 0
                                    |          },
                                    |          {
                                    |            "colour": "Band1",
                                    |            "barPercentage": 41.79,
                                    |            "tablePercentage": "20",
                                    |            "income": 7900,
                                    |            "tax": 1580
                                    |          }
                                    |        ],
                                    |        "minBand": 0,
                                    |        "nextBand": 18900,
                                    |        "incomeTotal": 18900,
                                    |        "incomeAsPercentage": 100.00,
                                    |        "taxTotal": 1580
                                    |      },
                                    |      "hasChanges": false
                                    |    }
                                    |  },
                                    |  "taxableIncome": {
                                    |    "taxFreeAmount": 11000,
                                    |    "incomeTax": 1580,
                                    |    "income": 18900,
                                    |    "taxCodeList": [
                                    |      "1100L"
                                    |    ],
                                    |    "increasesTax": {
                                    |      "incomes": {
                                    |        "taxCodeIncomes": {
                                    |          "employments": {
                                    |            "taxCodeIncomes": [
                                    |              {
                                    |                "name": "Sainsburys",
                                    |                "taxCode": "1100L",
                                    |                "employmentId": 2,
                                    |                "employmentPayeRef": "BT456",
                                    |                "employmentType": 1,
                                    |                "incomeType": 0,
                                    |                "employmentStatus": 1,
                                    |                "tax": {
                                    |                  "totalIncome": 18900,
                                    |                  "totalTaxableIncome": 7900,
                                    |                  "totalTax": 1580,
                                    |                  "potentialUnderpayment": 0,
                                    |                  "taxBands": [
                                    |                    {
                                    |                      "income": 7900,
                                    |                      "tax": 1580,
                                    |                      "lowerBand": 0,
                                    |                      "upperBand": 32000,
                                    |                      "rate": 20.00
                                    |                    },
                                    |                    {
                                    |                      "income": 0,
                                    |                      "tax": 0,
                                    |                      "lowerBand": 32000,
                                    |                      "upperBand": 150000,
                                    |                      "rate": 40.00
                                    |                    },
                                    |                    {
                                    |                      "income": 0,
                                    |                      "tax": 0,
                                    |                      "lowerBand": 150000,
                                    |                      "upperBand": 0,
                                    |                      "rate": 45.00
                                    |                    }
                                    |                  ],
                                    |                  "allowReliefDeducts": 179.00
                                    |                },
                                    |                "worksNumber": "1",
                                    |                "jobTitle": " ",
                                    |                "startDate": "2008-04-06",
                                    |                "income": 7900,
                                    |                "otherIncomeSourceIndicator": false,
                                    |                "isEditable": true,
                                    |                "isLive": true,
                                    |                "isOccupationalPension": false,
                                    |                "isPrimary": true
                                    |              }
                                    |            ],
                                    |            "totalIncome": 18900,
                                    |            "totalTax": 1580,
                                    |            "totalTaxableIncome": 7900
                                    |          },
                                    |          "hasDuplicateEmploymentNames": false,
                                    |          "totalIncome": 18900,
                                    |          "totalTaxableIncome": 7900,
                                    |          "totalTax": 1580
                                    |        },
                                    |        "noneTaxCodeIncomes": {
                                    |          "totalIncome": 0
                                    |        },
                                    |        "total": 18900
                                    |      },
                                    |      "total": 18900
                                    |    },
                                    |    "employmentPension": {
                                    |      "taxCodeIncomes": {
                                    |        "employments": {
                                    |          "taxCodeIncomes": [
                                    |            {
                                    |              "name": "Sainsburys",
                                    |              "taxCode": "1100L",
                                    |              "employmentId": 2,
                                    |              "employmentPayeRef": "BT456",
                                    |              "employmentType": 1,
                                    |              "incomeType": 0,
                                    |              "employmentStatus": 1,
                                    |              "tax": {
                                    |                "totalIncome": 18900,
                                    |                "totalTaxableIncome": 7900,
                                    |                "totalTax": 1580,
                                    |                "potentialUnderpayment": 0,
                                    |                "taxBands": [
                                    |                  {
                                    |                    "income": 7900,
                                    |                    "tax": 1580,
                                    |                    "lowerBand": 0,
                                    |                    "upperBand": 32000,
                                    |                    "rate": 20.00
                                    |                  },
                                    |                  {
                                    |                    "income": 0,
                                    |                    "tax": 0,
                                    |                    "lowerBand": 32000,
                                    |                    "upperBand": 150000,
                                    |                    "rate": 40.00
                                    |                  },
                                    |                  {
                                    |                    "income": 0,
                                    |                    "tax": 0,
                                    |                    "lowerBand": 150000,
                                    |                    "upperBand": 0,
                                    |                    "rate": 45.00
                                    |                  }
                                    |                ],
                                    |                "allowReliefDeducts": 179.00
                                    |              },
                                    |              "worksNumber": "1",
                                    |              "jobTitle": " ",
                                    |              "startDate": "2008-04-06",
                                    |              "income": 7354,
                                    |              "otherIncomeSourceIndicator": false,
                                    |              "isEditable": true,
                                    |              "isLive": true,
                                    |              "isOccupationalPension": false,
                                    |              "isPrimary": true
                                    |            }
                                    |          ],
                                    |          "totalIncome": 18900,
                                    |          "totalTax": 1580,
                                    |          "totalTaxableIncome": 7900
                                    |        },
                                    |        "hasDuplicateEmploymentNames": false,
                                    |        "totalIncome": 18900,
                                    |        "totalTaxableIncome": 7900,
                                    |        "totalTax": 1580
                                    |      },
                                    |      "totalEmploymentPensionAmt": 18900,
                                    |      "hasEmployment": true,
                                    |      "isOccupationalPension": false
                                    |    },
                                    |    "investmentIncomeData": [
                                    |
                                    |    ],
                                    |    "investmentIncomeTotal": 0,
                                    |    "otherIncomeData": [
                                    |
                                    |    ],
                                    |    "otherIncomeTotal": 0,
                                    |    "benefitsData": [
                                    |
                                    |    ],
                                    |    "benefitsTotal": 0,
                                    |    "taxableBenefitsData": [
                                    |
                                    |    ],
                                    |    "taxableBenefitsTotal": 0,
                                    |    "hasChanges": false
                                    |  }
                                    |}""".stripMargin)
  val testPreferences = Json.parse("""{"digital":true,"email":{"email":"name@email.co.uk","status":"verified"}}""")
  val testState = Json.parse("""{"shuttered":true,"inSubmissionPeriod":false}""")
  val testTaxCreditSummary = Json.parse("""{
                                          |  "paymentSummary": {
                                          |    "workingTaxCredit": {
                                          |      "amount": 86.63,
                                          |      "paymentDate": 1437004800000,
                                          |      "paymentFrequency": "WEEKLY"
                                          |    },
                                          |    "childTaxCredit": {
                                          |      "amount": 51.76,
                                          |      "paymentDate": 1437004800000,
                                          |      "paymentFrequency": "WEEKLY"
                                          |    }
                                          |  },
                                          |  "personalDetails": {
                                          |    "forename": "Nuala",
                                          |    "surname": "O'Shea",
                                          |    "nino": "CS700100A",
                                          |    "address": {
                                          |      "addressLine1": "19 Bushey Hall Road",
                                          |      "addressLine2": "Bushey",
                                          |      "addressLine3": "Watford",
                                          |      "addressLine4": "Hertfordshire",
                                          |      "postCode": "WD23 2EE"
                                          |    }
                                          |  },
                                          |  "partnerDetails": {
                                          |    "forename": "Frederick",
                                          |    "otherForenames": "Tarquin",
                                          |    "surname": "Hunter-Smith",
                                          |    "nino": "CS700100A",
                                          |    "address": {
                                          |      "addressLine1": "19 Bushey Hall Road",
                                          |      "addressLine2": "Bushey",
                                          |      "addressLine3": "Watford",
                                          |      "addressLine4": "Hertfordshire",
                                          |      "postCode": "WD23 2EE"
                                          |    }
                                          |  },
                                          |  "children": {
                                          |    "child": [
                                          |      {
                                          |        "firstNames": "Sarah",
                                          |        "surname": "Smith",
                                          |        "dateOfBirth": 936057600000,
                                          |        "hasFTNAE": false,
                                          |        "hasConnexions": false,
                                          |        "isActive": true
                                          |      },
                                          |      {
                                          |        "firstNames": "Joseph",
                                          |        "surname": "Smith",
                                          |        "dateOfBirth": 884304000000,
                                          |        "hasFTNAE": false,
                                          |        "hasConnexions": false,
                                          |        "isActive": true
                                          |      },
                                          |      {
                                          |        "firstNames": "Mary",
                                          |        "surname": "Smith",
                                          |        "dateOfBirth": 852768000000,
                                          |        "hasFTNAE": false,
                                          |        "hasConnexions": false,
                                          |        "isActive": true
                                          |      }
                                          |    ]
                                          |  },
                                          |  "showData": true
                                          |}""".stripMargin)
  val testAuthToken = JsString("someTestAuthToken")
  val testTaxCreditDecision = JsBoolean(false)
  val testPushReg = JsNull

  val noNinoOnAccount = Json.parse("""{"code":"UNAUTHORIZED","message":"NINO does not exist on account"}""")
  val lowCL = Json.parse("""{"code":"LOW_CONFIDENCE_LEVEL","message":"Confidence Level on account does not allow access"}""")
  val weakCredStrength = Json.parse("""{"code":"WEAK_CRED_STRENGTH","message":"Credential Strength on account does not allow access"}""")

  lazy val testCustomerProfileConnector = new TestCustomerProfileGenericConnector(true, testAccount, testPushReg, testPreferences, testTaxSummary, testState, testTaxCreditSummary, testTaxCreditDecision, testAuthToken)
  lazy val authConnector = new TestAuthConnector(Some(nino))
  lazy val testOrchestrationService = new TestOrchestrationService(testCustomerProfileConnector, authConnector)

  lazy val TODOtestCustomerProfileConnectorFAILURE = new TODOTestCustomerProfileGenericConnector(true, true, testAccount, testPushReg, testPreferences, testTaxSummary, testState, testTaxCreditSummary, testTaxCreditDecision, testAuthToken)
  lazy val TODOtestOrchestrationService = new TestOrchestrationService(TODOtestCustomerProfileConnectorFAILURE, authConnector)

  lazy val TODOtestCustomerProfileConnectorRetry = new TODOTestCustomerProfileGenericConnector(false, true, testAccount, testPushReg, testPreferences, testTaxSummary, testState, testTaxCreditSummary, testTaxCreditDecision, testAuthToken)
  lazy val TODOtestOrchestrationServiceRetry = new TestOrchestrationService(TODOtestCustomerProfileConnectorRetry, authConnector)


  lazy val testAccess = new TestAccessCheck(authConnector)
  lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

}

trait Success extends Setup {
  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "Success Orchestration Controller"
  }
}

trait TestController {
  val controller : NativeAppsOrchestrationController

}

trait TODO extends Setup with TestController {
  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = TODOtestOrchestrationService
    override val app: String = "Success Orchestration Controller"
  }
  def invokeCount = TODOtestCustomerProfileConnectorFAILURE.counter
}

trait TODOFailWithRetrySuccess extends Setup with TestController {
  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = TODOtestOrchestrationServiceRetry
    override val app: String = "Success Orchestration Controller"
  }


  def invokeCount = TODOtestCustomerProfileConnectorRetry.counter
}

class TestOrchestrationService(testGenericConnector: GenericConnector, testAuthConnector: AuthConnector) extends LiveOrchestrationService {
  override val auditConnector: AuditConnector = MicroserviceAuditConnector
  override val genericConnector: GenericConnector = testGenericConnector
  override val authConnector: AuthConnector = testAuthConnector
}

class TestAccountAccessControlWithAccept(testAccessCheck: AccountAccessControl) extends AccountAccessControlWithHeaderCheck {
  override val accessControl: AccountAccessControl = testAccessCheck
}

class TestAccessCheck(testAuthConnector: TestAuthConnector) extends AccountAccessControl {
  override val authConnector: AuthConnector = testAuthConnector
}

class TestAuthConnector(nino: Option[Nino]) extends AuthConnector {
  override val serviceUrl: String = "someUrl"

  override def serviceConfidenceLevel: ConfidenceLevel = ???

  override def http: HttpGet = ???

  override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future(Accounts(nino, None, false, false, "102030394AAA"))

  override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = Future(Unit)
}

class TestCustomerProfileGenericConnector(upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue, preferences: JsValue,
                                          taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue, taxCreditDecision: JsBoolean,
                                          auth: JsValue) extends GenericConnector {

  override def http: HttpPost with HttpGet = StubWsHttp

  override def doPost(json:JsValue, host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "push/registration" => Future.successful(JsNull)
    }
  }

  override def doGet(host:String, path:String, port:Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "profile/native-app/version-check" => Future.successful(Json.toJson(upgradeRequired))
      case "profile/preferences" => Future.successful(preferences)
      case "income/CS700100A/tax-summary/2016" => {
        if (taxSummary != JsNull) {
          Future.successful(taxSummary)
        }
        else {
          Future.failed(new Exception(""))
        }
      }
      case "income/tax-credits/submission/state" => Future.successful(state)
      case "income/CS700100A/tax-credits/tax-credits-summary" => Future.successful(taxCreditSummary)
      case "income/CS700100A/tax-credits/tax-credits-decision" => Future.successful(taxCreditDecision)
      case "income/CS700100A/tax-credits/999999999999999/auth" => Future.successful(auth)


// ADD DEFAULT AND THROW EXCEPTION!
    }
  }
}


class TODOTestCustomerProfileGenericConnector(exceptionControl:Boolean, upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue, preferences: JsValue,
                                              taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue, taxCreditDecision: JsBoolean,
                                              auth: JsValue) extends TestCustomerProfileGenericConnector(upgradeRequired: Boolean, accounts: Accounts, pushRegResult: JsValue, preferences: JsValue,
  taxSummary: JsValue, state: JsValue, taxCreditSummary: JsValue, taxCreditDecision: JsBoolean,
  auth: JsValue) {

  var counter=0

  override def doGet(host: String, path: String, port: Int, hc: HeaderCarrier): Future[JsValue] = {
    path match {
      case "profile/native-app/version-check" => Future.successful(Json.toJson(upgradeRequired))
      case "profile/preferences" => Future.successful(preferences)
      case "income/CS700100A/tax-summary/2016" => {
        //        if (taxSummary != JsNull) {
        //          Future.successful(taxSummary)
        //        }
        //        else {
        //          Future.failed(new Exception(""))
        //        }


// >>>>>>>>>>>>>>>>

        //
        val res = if (exceptionControl==true) Future.failed(new ServiceUnavailableException("FAILED"))
        else {
          if (counter == 0) Future.failed(new ServiceUnavailableException("FAILED"))
          else {
            Future.successful(taxSummary)
          }
        }
        counter = counter + 1
        res
      }
      case "income/tax-credits/submission/state" => Future.successful(state)
      case "income/CS700100A/tax-credits/tax-credits-summary" => Future.successful(taxCreditSummary)
      case "income/CS700100A/tax-credits/tax-credits-decision" => Future.successful(taxCreditDecision)
      case "income/CS700100A/tax-credits/999999999999999/auth" => Future.successful(auth)


      // ADD DEFAULT AND THROW EXCEPTION!
    }


  }
}

trait AuthWithoutTaxSummary extends Setup with AuthorityTest {

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new NinoNotFoundOnAccount("The user must have a National Insurance Number")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.failed(exception)
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    val testCustomerProfileGenericConnector = new TestCustomerProfileGenericConnector(true, testAccount, testPushReg, testPreferences, JsNull, testState, testTaxCreditSummary, testTaxCreditDecision, testAuthToken)
    override val service: OrchestrationService = new TestOrchestrationService(testCustomerProfileGenericConnector, authConnector)
    override val app: String = "AuthWithoutNino Native Apps Orchestration"
  }


}

trait AuthWithoutNino extends Setup with AuthorityTest {

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new NinoNotFoundOnAccount("The user must have a National Insurance Number")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.failed(exception)
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
    override val service: OrchestrationService = testOrchestrationService
    override val app: String = "AuthWithoutNino Native Apps Orchestration"
  }
}

trait AuthWithLowCL extends Setup with AuthorityTest {
  val routeToIv=true
  val routeToTwoFactor=false

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new AccountWithLowCL("Forbidden to access since low CL")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.successful(Accounts(Some(nino), None, routeToIv, routeToTwoFactor, "102030394AAA"))
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    val app = "AuthWithLowCL Native Apps Orchestration"
    override val service: LiveOrchestrationService = new TestOrchestrationService(testCustomerProfileConnector,authConnector)
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
  }
}

trait AuthWithWeakCreds extends Setup with AuthorityTest {
  val routeToIv=false
  val routeToTwoFactor=true

  override lazy val authConnector = new TestAuthConnector(None) {
    lazy val exception = new AccountWithWeakCredStrength("Forbidden to access since weak cred strength")
    override def accounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] = Future.successful(Accounts(Some(nino), None, routeToIv, routeToTwoFactor, "102030394AAA"))
    override def grantAccess()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = Future.failed(exception)
  }

  override lazy val testAccess = new TestAccessCheck(authConnector)
  override lazy val testCompositeAction = new TestAccountAccessControlWithAccept(testAccess)

  val controller = new NativeAppsOrchestrationController {
    val app = "AuthWithWeakCreds Native Apps Orchestration"
    override val service: LiveOrchestrationService = new TestOrchestrationService(testCustomerProfileConnector,authConnector)
    override val accessControl: AccountAccessControlWithHeaderCheck = testCompositeAction
  }
}

trait SandboxSuccess extends Setup {
  val controller = new NativeAppsOrchestrationController {
    val app = "Sandbox Native Apps Orchestration"
    override val service: OrchestrationService = SandboxOrchestrationService
    override val accessControl: AccountAccessControlWithHeaderCheck = AccountAccessControlCheckOff
  }
}

trait AuthorityTest extends UnitSpec {
  self: Setup =>

  def testNoNINO(func: => play.api.mvc.Result) = {
    val result: play.api.mvc.Result = func

    status(result) shouldBe 401
    contentAsJson(result) shouldBe noNinoOnAccount
  }

  def testLowCL(func: => play.api.mvc.Result) = {
    val result: play.api.mvc.Result = func

    status(result) shouldBe 401
    contentAsJson(result) shouldBe lowCL
  }

  def testWeakCredStrength(func: => play.api.mvc.Result) = {
    val result: play.api.mvc.Result = func

    status(result) shouldBe 401
    contentAsJson(result) shouldBe weakCredStrength
  }
}
