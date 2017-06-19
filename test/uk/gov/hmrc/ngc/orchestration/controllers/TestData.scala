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

import play.api.libs.json._


object TestData {

  def upgradeRequired(upgradeRequired: Boolean) : JsValue = Json.parse(s"""{"upgrade": $upgradeRequired}""")

  def responseTicket : JsValue = Json.parse(s"""{"responseData": {"ticket_id": 1980683879}}""")

  val testPreferences = Json.parse("""{"digital":true,"email":{"email":"name@email.co.uk","status":"verified"}}""")

  val testState = Json.parse("""{"submissionState":true}""")

  val testStateNotInSubmission = Json.parse("""{"submissionState":false}""")

  val testAuthToken = JsString("someTestAuthToken")

  val testTaxCreditDecision: JsValue = Json.parse("""{"showData":true}""")
  val testTaxCreditDecisionNotDisplay: JsValue = Json.parse("""{"showData":false}""")
  val testPushReg = JsNull

  val noNinoOnAccount = Json.parse("""{"code":"UNAUTHORIZED","message":"NINO does not exist on account"}""")
  val lowCL = Json.parse("""{"code":"LOW_CONFIDENCE_LEVEL","message":"Confidence Level on account does not allow access"}""")
  val weakCredStrength = Json.parse("""{"code":"WEAK_CRED_STRENGTH","message":"Credential Strength on account does not allow access"}""")


  val pollResponse = Json.obj("status" -> Json.parse("""{"code":"poll"}"""))

  def taxSummaryData(additional:Option[String]=None) : JsValue = Json.parse(
    s"""{
      |    "taxSummaryDetails": {
      |      "nino": "CS700100A",
      |      "version": 154,
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1100L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 7900,
      |                    "totalTax": 1580,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7900,
      |                        "tax": 1580,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 7900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1580,
      |              "totalTaxableIncome": 7900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 7900,
      |            "totalTax": 1580
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "decreasesTax": {
      |        "personalAllowance": 11000,
      |        "personalAllowanceSourceAmount": 11000,
      |        "paTapered": false,
      |        "total": 11000
      |      },
      |      "totalLiability": {
      |        "nonSavings": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 7900,
      |          "totalTax": 1580,
      |          "taxBands": [
      |            {
      |              "income": 7900,
      |              "tax": 1580,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11000
      |        },
      |        "mergedIncomes": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 7900,
      |          "totalTax": 1580,
      |          "taxBands": [
      |            {
      |              "income": 93,
      |              "tax": 0,
      |              "lowerBand": 0,
      |              "upperBand": 5000,
      |              "rate": 0
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 5000,
      |              "upperBand": 32000,
      |              "rate": 7.5
      |            },
      |            {
      |              "income": 7900,
      |              "tax": 1580,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 32.5
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 38.1
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11000
      |        },
      |        "totalLiability": 7900,
      |        "totalTax": 1580,
      |        "totalTaxOnIncome": 1580,
      |        "underpaymentPreviousYear": 0,
      |        "outstandingDebt": 0,
      |        "childBenefitAmount": 0,
      |        "childBenefitTaxDue": 0,
      |        "liabilityReductions": {
      |          "enterpriseInvestmentSchemeRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "concessionalRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "maintenancePayments": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "doubleTaxationRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        },
      |        "liabilityAdditions": {
      |          "excessGiftAidTax": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "excessWidowsAndOrphans": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "pensionPaymentsAdjustment": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        }
      |      },
      |      "extensionReliefs": {
      |        "giftAid": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        },
      |        "personalPension": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        }
      |      },
      |      "taxCodeDetails": {
      |        "employment": [
      |          {
      |            "id": 2,
      |            "name": "Sainsburys",
      |            "taxCode": "1100L"
      |          }
      |        ],
      |        "taxCode": [
      |          {
      |            "taxCode": "L"
      |          }
      |        ],
      |        "taxCodeDescriptions": [
      |          {
      |            "taxCode": "1100L",
      |            "name": "Sainsburys",
      |            "taxCodeDescriptors": [
      |              {
      |                "taxCode": "L"
      |              }
      |            ]
      |          }
      |        ],
      |        "deductions": [
      |
      |        ],
      |        "allowances": [
      |          {
      |            "description": "Tax Free Amount",
      |            "amount": 11000,
      |            "componentType": 0
      |          }
      |        ],
      |        "splitAllowances": false,
      |        "total": 0
      |      }
      |    },
      |    "baseViewModel": {
      |      "estimatedIncomeTax": 1580,
      |      "taxableIncome": 7900,
      |      "taxFree": 11000,
      |      "personalAllowance": 11000,
      |      "hasTamc": false,
      |      "taxCodesList": [
      |        "1100L"
      |      ],
      |      "hasChanges": false
      |    },
      |    "estimatedIncomeWrapper": {
      |      "estimatedIncome": {
      |        "increasesTax": true,
      |        "incomeTaxEstimate": 1580,
      |        "incomeEstimate": 18900,
      |        "taxFreeEstimate": 11000,
      |        "taxRelief": false,
      |        "taxCodes": [
      |          "1100L"
      |        ],
      |        "potentialUnderpayment": false,
      |        "additionalTaxTable": [
      |
      |        ],
      |        "additionalTaxTableTotal": "0.00",
      |        "reductionsTable": [
      |
      |        ],
      |        "reductionsTableTotal": "-0.00",
      |        "graph": {
      |          "id": "taxGraph",
      |          "bands": [
      |            {
      |              "colour": "TaxFree",
      |              "barPercentage": 58.21,
      |              "tablePercentage": "0",
      |              "income": 11000,
      |              "tax": 0
      |            },
      |            {
      |              "colour": "Band1",
      |              "barPercentage": 41.79,
      |              "tablePercentage": "20",
      |              "income": 7900,
      |              "tax": 1580
      |            }
      |          ],
      |          "minBand": 0,
      |          "nextBand": 18900,
      |          "incomeTotal": 18900,
      |          "incomeAsPercentage": 100,
      |          "taxTotal": 1580
      |        },
      |        "hasChanges": false
      |      }
      |    },
      |    "taxableIncome": {
      |      "taxFreeAmount": 11000,
      |      "incomeTax": 1580,
      |      "income": 18900,
      |      "taxCodeList": [
      |        "1100L"
      |      ],
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1100L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 7900,
      |                    "totalTax": 1580,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7900,
      |                        "tax": 1580,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 7900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1580,
      |              "totalTaxableIncome": 7900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 7900,
      |            "totalTax": 1580
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "employmentPension": {
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
      |                      "rate": 20
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 32000,
      |                      "upperBand": 150000,
      |                      "rate": 40
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 150000,
      |                      "upperBand": 0,
      |                      "rate": 45
      |                    }
      |                  ],
      |                  "allowReliefDeducts": 179
      |                },
      |                "worksNumber": "1",
      |                "jobTitle": " ",
      |                "startDate": "2008-04-06",
      |                "income": 7354,
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
      |        "totalEmploymentPensionAmt": 18900,
      |        "hasEmployment": true,
      |        "isOccupationalPension": false
      |      },
      |      "investmentIncomeData": [
      |
      |      ],
      |      "investmentIncomeTotal": 0,
      |      "otherIncomeData": [
      |
      |      ],
      |      "otherIncomeTotal": 0,
      |      "benefitsData": [
      |
      |      ],
      |      "benefitsTotal": 0,
      |      "taxableBenefitsData": [
      |
      |      ],
      |      "taxableBenefitsTotal": 0,
      |      "hasChanges": false
      |    }
      |    ${additional.fold(""){id => s""","ASYNC_TEST_ID":"$id""""}}
      |}
    """.stripMargin)


  def taxSummary(id:Option[String]=None) = Json.obj("taxSummary" -> taxSummaryData(id))

  val submissionStateDataOff =   Json.parse("""{"enableRenewals": false}""")
  val submissionStateOff: JsObject = Json.obj("state" -> submissionStateDataOff)

  val submissionStateDataOn =   Json.parse("""{"enableRenewals": true}""")
  val submissionStateOn: JsObject = Json.obj("state" -> submissionStateDataOn)

  val statusCompleteData = Json.parse("""{"code": "complete"}""")
  val statusComplete = Json.obj("status" -> statusCompleteData)

  val statusThrottleData = Json.parse("""{"code": "throttle"}""")
  val statusThrottle = Json.obj("status" -> statusThrottleData)

  val statusErrorData = Json.parse("""{"code": "error"}""")
  val statusError = Json.obj("status" -> statusErrorData)

  val taxCreditSummaryData = Json.parse(
    """
      |{
      |    "paymentSummary": {
      |      "workingTaxCredit": {
      |        "amount": 86.63,
      |        "paymentDate": 1437004800000,
      |        "paymentFrequency": "WEEKLY"
      |      },
      |      "childTaxCredit": {
      |        "amount": 51.76,
      |        "paymentDate": 1437004800000,
      |        "paymentFrequency": "WEEKLY"
      |      }
      |    },
      |    "personalDetails": {
      |      "forename": "Nuala",
      |      "surname": "O'Shea",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "partnerDetails": {
      |      "forename": "Frederick",
      |      "otherForenames": "Tarquin",
      |      "surname": "Hunter-Smith",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "children": {
      |      "child": [
      |        {
      |          "firstNames": "Sarah",
      |          "surname": "Smith",
      |          "dateOfBirth": 936057600000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Joseph",
      |          "surname": "Smith",
      |          "dateOfBirth": 884304000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Mary",
      |          "surname": "Smith",
      |          "dateOfBirth": 852768000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        }
      |      ]
      |    },
      |    "showData": true
      |  }
    """.stripMargin)
  val taxCreditSummary = Json.obj("taxCreditSummary" -> taxCreditSummaryData)

  val taxSummaryEmpty = Json.obj("taxSummary" -> Json.obj())

  val taxCreditSummaryEmpty = Json.obj("taxCreditSummary" -> Json.obj())

  val sandboxStartupResponse = Json.obj("status" -> Json.parse("""{"code":"poll"}"""))

  val sandboxPollResponse = Json.parse(
    """
      |{
      |  "taxSummary": {
      |    "taxSummaryDetails": {
      |      "nino": "CS700100A",
      |      "version": 154,
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1100L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 7900,
      |                    "totalTax": 1580,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7900,
      |                        "tax": 1580,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 7900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1580,
      |              "totalTaxableIncome": 7900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 7900,
      |            "totalTax": 1580
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "decreasesTax": {
      |        "personalAllowance": 11000,
      |        "personalAllowanceSourceAmount": 11000,
      |        "paTapered": false,
      |        "total": 11000
      |      },
      |      "totalLiability": {
      |        "nonSavings": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 7900,
      |          "totalTax": 1580,
      |          "taxBands": [
      |            {
      |              "income": 7900,
      |              "tax": 1580,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11000
      |        },
      |        "mergedIncomes": {
      |          "totalIncome": 18900,
      |          "totalTaxableIncome": 7900,
      |          "totalTax": 1580,
      |          "taxBands": [
      |            {
      |              "income": 93,
      |              "tax": 0,
      |              "lowerBand": 0,
      |              "upperBand": 5000,
      |              "rate": 0
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 5000,
      |              "upperBand": 32000,
      |              "rate": 7.5
      |            },
      |            {
      |              "income": 7900,
      |              "tax": 1580,
      |              "lowerBand": 0,
      |              "upperBand": 32000,
      |              "rate": 20
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 32.5
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 38.1
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 32000,
      |              "upperBand": 150000,
      |              "rate": 40
      |            },
      |            {
      |              "income": 0,
      |              "tax": 0,
      |              "lowerBand": 150000,
      |              "upperBand": 0,
      |              "rate": 45
      |            }
      |          ],
      |          "allowReliefDeducts": 11000
      |        },
      |        "totalLiability": 7900,
      |        "totalTax": 1580,
      |        "totalTaxOnIncome": 1580,
      |        "underpaymentPreviousYear": 0,
      |        "outstandingDebt": 0,
      |        "childBenefitAmount": 0,
      |        "childBenefitTaxDue": 0,
      |        "liabilityReductions": {
      |          "enterpriseInvestmentSchemeRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "concessionalRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "maintenancePayments": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "doubleTaxationRelief": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        },
      |        "liabilityAdditions": {
      |          "excessGiftAidTax": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "excessWidowsAndOrphans": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          },
      |          "pensionPaymentsAdjustment": {
      |            "codingAmount": 0,
      |            "amountInTermsOfTax": 0
      |          }
      |        }
      |      },
      |      "extensionReliefs": {
      |        "giftAid": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        },
      |        "personalPension": {
      |          "sourceAmount": 0,
      |          "reliefAmount": 0
      |        }
      |      },
      |      "taxCodeDetails": {
      |        "employment": [
      |          {
      |            "id": 2,
      |            "name": "Sainsburys",
      |            "taxCode": "1100L"
      |          }
      |        ],
      |        "taxCode": [
      |          {
      |            "taxCode": "L"
      |          }
      |        ],
      |        "taxCodeDescriptions": [
      |          {
      |            "taxCode": "1100L",
      |            "name": "Sainsburys",
      |            "taxCodeDescriptors": [
      |              {
      |                "taxCode": "L"
      |              }
      |            ]
      |          }
      |        ],
      |        "deductions": [
      |
      |        ],
      |        "allowances": [
      |          {
      |            "description": "Tax Free Amount",
      |            "amount": 11000,
      |            "componentType": 0
      |          }
      |        ],
      |        "splitAllowances": false,
      |        "total": 0
      |      }
      |    },
      |    "baseViewModel": {
      |      "estimatedIncomeTax": 1580,
      |      "taxableIncome": 7900,
      |      "taxFree": 11000,
      |      "personalAllowance": 11000,
      |      "hasTamc": false,
      |      "taxCodesList": [
      |        "1100L"
      |      ],
      |      "hasChanges": false
      |    },
      |    "estimatedIncomeWrapper": {
      |      "estimatedIncome": {
      |        "increasesTax": true,
      |        "incomeTaxEstimate": 1580,
      |        "incomeEstimate": 18900,
      |        "taxFreeEstimate": 11000,
      |        "taxRelief": false,
      |        "taxCodes": [
      |          "1100L"
      |        ],
      |        "potentialUnderpayment": false,
      |        "additionalTaxTable": [
      |
      |        ],
      |        "additionalTaxTableTotal": "0.00",
      |        "reductionsTable": [
      |
      |        ],
      |        "reductionsTableTotal": "-0.00",
      |        "graph": {
      |          "id": "taxGraph",
      |          "bands": [
      |            {
      |              "colour": "TaxFree",
      |              "barPercentage": 58.21,
      |              "tablePercentage": "0",
      |              "income": 11000,
      |              "tax": 0
      |            },
      |            {
      |              "colour": "Band1",
      |              "barPercentage": 41.79,
      |              "tablePercentage": "20",
      |              "income": 7900,
      |              "tax": 1580
      |            }
      |          ],
      |          "minBand": 0,
      |          "nextBand": 18900,
      |          "incomeTotal": 18900,
      |          "incomeAsPercentage": 100,
      |          "taxTotal": 1580
      |        },
      |        "hasChanges": false
      |      }
      |    },
      |    "taxableIncome": {
      |      "taxFreeAmount": 11000,
      |      "incomeTax": 1580,
      |      "income": 18900,
      |      "taxCodeList": [
      |        "1100L"
      |      ],
      |      "increasesTax": {
      |        "incomes": {
      |          "taxCodeIncomes": {
      |            "employments": {
      |              "taxCodeIncomes": [
      |                {
      |                  "name": "Sainsburys",
      |                  "taxCode": "1100L",
      |                  "employmentId": 2,
      |                  "employmentPayeRef": "BT456",
      |                  "employmentType": 1,
      |                  "incomeType": 0,
      |                  "employmentStatus": 1,
      |                  "tax": {
      |                    "totalIncome": 18900,
      |                    "totalTaxableIncome": 7900,
      |                    "totalTax": 1580,
      |                    "potentialUnderpayment": 0,
      |                    "taxBands": [
      |                      {
      |                        "income": 7900,
      |                        "tax": 1580,
      |                        "lowerBand": 0,
      |                        "upperBand": 32000,
      |                        "rate": 20
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 32000,
      |                        "upperBand": 150000,
      |                        "rate": 40
      |                      },
      |                      {
      |                        "income": 0,
      |                        "tax": 0,
      |                        "lowerBand": 150000,
      |                        "upperBand": 0,
      |                        "rate": 45
      |                      }
      |                    ],
      |                    "allowReliefDeducts": 179
      |                  },
      |                  "worksNumber": "1",
      |                  "jobTitle": " ",
      |                  "startDate": "2008-04-06",
      |                  "income": 7900,
      |                  "otherIncomeSourceIndicator": false,
      |                  "isEditable": true,
      |                  "isLive": true,
      |                  "isOccupationalPension": false,
      |                  "isPrimary": true
      |                }
      |              ],
      |              "totalIncome": 18900,
      |              "totalTax": 1580,
      |              "totalTaxableIncome": 7900
      |            },
      |            "hasDuplicateEmploymentNames": false,
      |            "totalIncome": 18900,
      |            "totalTaxableIncome": 7900,
      |            "totalTax": 1580
      |          },
      |          "noneTaxCodeIncomes": {
      |            "totalIncome": 0
      |          },
      |          "total": 18900
      |        },
      |        "total": 18900
      |      },
      |      "employmentPension": {
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
      |                      "rate": 20
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 32000,
      |                      "upperBand": 150000,
      |                      "rate": 40
      |                    },
      |                    {
      |                      "income": 0,
      |                      "tax": 0,
      |                      "lowerBand": 150000,
      |                      "upperBand": 0,
      |                      "rate": 45
      |                    }
      |                  ],
      |                  "allowReliefDeducts": 179
      |                },
      |                "worksNumber": "1",
      |                "jobTitle": " ",
      |                "startDate": "2008-04-06",
      |                "income": 7354,
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
      |        "totalEmploymentPensionAmt": 18900,
      |        "hasEmployment": true,
      |        "isOccupationalPension": false
      |      },
      |      "investmentIncomeData": [
      |
      |      ],
      |      "investmentIncomeTotal": 0,
      |      "otherIncomeData": [
      |
      |      ],
      |      "otherIncomeTotal": 0,
      |      "benefitsData": [
      |
      |      ],
      |      "benefitsTotal": 0,
      |      "taxableBenefitsData": [
      |
      |      ],
      |      "taxableBenefitsTotal": 0,
      |      "hasChanges": false
      |    }
      |  },
      |  "taxCreditSummary": {
      |    "paymentSummary": {
      |    "workingTaxCredit": {
      |      "payments": [
      |        {
      |          "amount": 45.00,
      |          "paymentDate": "05-07-2017",
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 45.00,
      |          "paymentDate": "12-07-2017",
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 45.00,
      |          "paymentDate": "19-07-2017",
      |          "oneOffPayment": false
      |        }
      |      ],
      |      "frequency": "weekly"
      |    },
      |    "childTaxCredit": {
      |      "payments": [
      |        {
      |          "amount": 55.00,
      |          "paymentDate": "05-07-2017",
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 55.00,
      |          "paymentDate": "12-07-2017",
      |          "oneOffPayment": false
      |        },
      |        {
      |          "amount": 55.00,
      |          "paymentDate": "19-07-2017",
      |          "oneOffPayment": false
      |        }
      |      ],
      |      "frequency": "weekly"
      |    },
      |    "paymentSummaryEnabled": true,
      |    "totalsByDate": [
      |      {
      |        "amount": 100.00,
      |        "paymentDate": "05-07-2017"
      |      },
      |      {
      |        "amount": 100.00,
      |        "paymentDate": "12-07-2017"
      |      },
      |      {
      |        "amount": 100.00,
      |        "paymentDate": "19-07-2017"
      |      }
      |    ]
      |  },
      |    "personalDetails": {
      |      "forename": "Nuala",
      |      "surname": "O'Shea",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "partnerDetails": {
      |      "forename": "Frederick",
      |      "otherForenames": "Tarquin",
      |      "surname": "Hunter-Smith",
      |      "nino": "CS700100A",
      |      "address": {
      |        "addressLine1": "19 Bushey Hall Road",
      |        "addressLine2": "Bushey",
      |        "addressLine3": "Watford",
      |        "addressLine4": "Hertfordshire",
      |        "postCode": "WD23 2EE"
      |      }
      |    },
      |    "children": {
      |      "child": [
      |        {
      |          "firstNames": "Sarah",
      |          "surname": "Smith",
      |          "dateOfBirth": 936057600000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Joseph",
      |          "surname": "Smith",
      |          "dateOfBirth": 884304000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        },
      |        {
      |          "firstNames": "Mary",
      |          "surname": "Smith",
      |          "dateOfBirth": 852768000000,
      |          "hasFTNAE": false,
      |          "hasConnexions": false,
      |          "isActive": true
      |        }
      |      ]
      |    }
      |  },
      |  "state": {
      |    "enableRenewals": true
      |  },
      |  "status": {
      |    "code": "complete"
      |  }
      |}
    """.stripMargin)

}
