poll
----
  Request for result to the Startup service. A call to startup must have been performed first before the poll service is invoked. This service should be invoked every 2-3 seconds to verify the outcome of the Startup service call.
  
* **URL**

  `/native-app/{nino}/poll`

* **Method:**
  
  `GET`

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))


* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

If the task has not completed, the below will be returned. 
```json
{
  "status" : "poll"
}
```

On success the below JSON will be returned. Please see notes below detailing the optional attributes that can be returned. 

```
{
  "taxSummary": {
    "taxSummaryDetails": {
      "nino": "CS700100",
      "version": 1,
      "increasesTax": {
        "incomes": {
          "taxCodeIncomes": {
            "occupationalPensions": {
              "taxCodeIncomes": [
                {
                  "name": "PAYESCHEMEOPERATORNAME52603",
                  "taxCode": "K804",
                  "employmentId": 1,
                  "employmentPayeRef": "TZ99924",
                  "employmentType": 1,
                  "incomeType": 1,
                  "employmentStatus": 2,
                  "tax": {
                    "totalIncome": 33488,
                    "totalTaxableIncome": 41545,
                    "totalTax": 10245,
                    "taxBands": [
                      {
                        "income": 31865,
                        "tax": 6373,
                        "lowerBand": 0,
                        "upperBand": 31865,
                        "rate": 20
                      },
                      {
                        "income": 9680,
                        "tax": 3872,
                        "lowerBand": 31865,
                        "upperBand": 150000,
                        "rate": 40
                      },
                      {
                        "income": 0,
                        "tax": 0,
                        "lowerBand": 150000,
                        "upperBand": 0,
                        "rate": 45
                      }
                    ],
                    "allowReliefDeducts": -8057,
                    "actualTaxDueAssumingBasicRateAlreadyPaid": 3547.4
                  },
                  "startDate": "2012-01-01",
                  "endDate": "2013-01-01",
                  "income": 33488,
                  "otherIncomeSourceIndicator": false,
                  "isEditable": true,
                  "isLive": false,
                  "isOccupationalPension": true,
                  "isPrimary": true
                }
              ],
              "totalIncome": 33488,
              "totalTax": 10245,
              "totalTaxableIncome": 41545
            },
            "hasDuplicateEmploymentNames": false,
            "totalIncome": 33488,
            "totalTaxableIncome": 41545,
            "totalTax": 10245
          },
          "noneTaxCodeIncomes": {
            "statePension": 25557,
            "totalIncome": 25557
          },
          "total": 59045
        },
        "total": 74045
      },
      "totalLiability": {
        "totalTax": 10245,
        "totalTaxOnIncome": 10245,
        "underpaymentPreviousYear": 0,
        "outstandingDebt": 0,
        "childBenefitAmount": 0,
        "childBenefitTaxDue": 0
      },
      "gateKeeper": {
        "gateKeepered": true,
        "gateKeeperResults": [
          {
            "gateKeeperType": 1,
            "id": 3,
            "description": "TaxForCitizens:Gatekeeper Exception :- All Employments end before this year"
          }
        ]
      },
      "taxCodeDetails": {
        "employment": [
          {
            "id": 1,
            "name": "PAYESCHEMEOPERATORNAME52603",
            "taxCode": "K804"
          }
        ],
        "taxCode": [
          {
            "taxCode": "K"
          }
        ],
        "taxCodeDescriptions": [
          {
            "taxCode": "K804",
            "name": "PAYESCHEMEOPERATORNAME52603",
            "taxCodeDescriptors": [
              {
                "taxCode": "K"
              }
            ]
          }
        ],
        "deductions": [
          {
            "description": "savings income taxable at higher rate",
            "amount": 7500,
            "componentType": 32
          },
          {
            "description": "state pension\/state benefits",
            "amount": 25557,
            "componentType": 1
          }
        ],
        "allowances": [
          {
            "description": "personal allowance",
            "amount": 10000,
            "componentType": 11
          },
          {
            "description": "death, sickness or funeral benefits",
            "amount": 15000,
            "componentType": 25
          }
        ],
        "total": 0
      }
    },
    "baseViewModel": {
      "estimatedIncomeTax": 10245,
      "taxableIncome": 74045,
      "taxFree": 0,
      "personalAllowance": 0,
      "hasTamc": false,
      "taxCodesList": [
        "K804"
      ],
      "hasChanges": false
    },
    "gatekeeper": {
      "totalLiability": {
        "totalTax": 10245,
        "totalTaxOnIncome": 10245,
        "underpaymentPreviousYear": 0,
        "outstandingDebt": 0,
        "childBenefitAmount": 0,
        "childBenefitTaxDue": 0
      },
      "decreasesTax": {
        "paTapered": false,
        "total": 0
      },
      "employmentList": [
        {
          "a": "PAYESCHEMEOPERATORNAME52603",
          "b": "K804"
        }
      ],
      "increasesTax": {
        "incomes": {
          "taxCodeIncomes": {
            "occupationalPensions": {
              "taxCodeIncomes": [
                {
                  "name": "PAYESCHEMEOPERATORNAME52603",
                  "taxCode": "K804",
                  "employmentId": 1,
                  "employmentPayeRef": "TZ99924",
                  "employmentType": 1,
                  "incomeType": 1,
                  "employmentStatus": 2,
                  "tax": {
                    "totalIncome": 33488,
                    "totalTaxableIncome": 41545,
                    "totalTax": 10245,
                    "taxBands": [
                      {
                        "income": 31865,
                        "tax": 6373,
                        "lowerBand": 0,
                        "upperBand": 31865,
                        "rate": 20
                      },
                      {
                        "income": 9680,
                        "tax": 3872,
                        "lowerBand": 31865,
                        "upperBand": 150000,
                        "rate": 40
                      },
                      {
                        "income": 0,
                        "tax": 0,
                        "lowerBand": 150000,
                        "upperBand": 0,
                        "rate": 45
                      }
                    ],
                    "allowReliefDeducts": -8057,
                    "actualTaxDueAssumingBasicRateAlreadyPaid": 3547.4
                  },
                  "startDate": "2012-01-01",
                  "endDate": "2013-01-01",
                  "income": 33488,
                  "otherIncomeSourceIndicator": false,
                  "isEditable": true,
                  "isLive": false,
                  "isOccupationalPension": true,
                  "isPrimary": true
                }
              ],
              "totalIncome": 33488,
              "totalTax": 10245,
              "totalTaxableIncome": 41545
            },
            "hasDuplicateEmploymentNames": false,
            "totalIncome": 33488,
            "totalTaxableIncome": 41545,
            "totalTax": 10245
          },
          "noneTaxCodeIncomes": {
            "statePension": 25557,
            "totalIncome": 25557
          },
          "total": 59045
        },
        "total": 74045
      }
    }
  },
  "taxCreditSummary": {
    "paymentSummary": {
      "workingTaxCredit": {
        "amount": 160.45,
        "paymentDate": 1435190400000,
        "paymentFrequency": "WEEKLY"
      },
      "childTaxCredit": {
        "amount": 140.12,
        "paymentDate": 1435190400000,
        "paymentFrequency": "WEEKLY"
      }
    },
    "personalDetails": {
      "forename": "John",
      "surname": "Densmore",
      "nino": "CS700100A",
      "address": {
        "addressLine1": "13 Front Street",
        "addressLine2": "Gosforth",
        "addressLine3": "Newcastle",
        "postCode": "NE43 7AY"
      },
      "wtcPaymentFrequency": "WEEKLY",
      "ctcPaymentFrequency": "WEEKLY",
      "dayPhoneNumber": "0191 393 3993"
    },
    "children": {
      "child": [
        {
          "firstNames": "Paul",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Sasha",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Eve",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Laura",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Justine",
          "surname": "Cowling",
          "dateOfBirth": 967507200000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": true
        },
        {
          "firstNames": "Adam",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Martin",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Sarah",
          "surname": "Cowling",
          "dateOfBirth": 1420070400000,
          "hasFTNAE": true,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Jerry",
          "surname": "Cowling",
          "dateOfBirth": 1420156800000,
          "hasFTNAE": true,
          "hasConnexions": false,
          "isActive": false
        },
        {
          "firstNames": "Amy",
          "surname": "Cowling",
          "dateOfBirth": 935884800000,
          "hasFTNAE": false,
          "hasConnexions": false,
          "isActive": false
        }
      ]
    }
  },
  "state": {
    "enableRenewals": true
  },
  "status": {
    "code": "complete"
  }
}
```

Please note the above "status" attribute could be complete, poll, error or timeout.
If the response status is "poll", the request has not completed processing. A new call is required to the `/native-app/{nino}/poll` service to understand the outcome of the call.
If the response status is "error" then a server-side failure occurred building mandatory response data.
If the response status is "timeout" then the server-side timed-out waiting for the backend services to reply.

If the response status is complete then the async service call has completed. The response will contain a set of attributes which is taxSummary, state and an optional taxCreditSummary attribute.
If the response attribute 'taxCreditSummary' is empty (contains no attributes) then this indicates Tax-Credits are available for the user, however there is no user data to display. If the response attribute 'taxCreditSummary' is not returned, then the user is not defined for tax-credits.

| *json attribute* | *Mandatory* | *Description* |
|------------------|-------------|---------------|
| `taxSummary` | yes | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-summary.md> |
| `taxCreditSummary` | no | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-summary.md> |
| `state` | yes | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-submission-state.md> |




* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 401 UNAUTHORIZED <br/>
    **Content:** `{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized for access"}`

  * **Code:** 404 NOTFOUND <br/>

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  OR when a user does not exist or server failure

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>



