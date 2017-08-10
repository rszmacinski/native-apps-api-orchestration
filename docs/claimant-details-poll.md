poll claimant-details
----
  Request for result to the claimant-details service request. A call to startup must have been performed first before the poll service is invoked. The orchestrate service will return a cookie called mdtpapi and this cookie must be supplied to the poll service. This service should be invoked every 2-3 seconds to verify the outcome of the Startup service call which created the async task.
  
* **URL**

  `/native-app/{nino}/poll`

* **Method:**
  
  `GET`

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

  If the task has not completed, the below will be returned. 
  ```json
  {
    "status" : "poll"
  }
  ```

  On success the below JSON will be returned.

    ```
    {
      "OrchestrationResponse": {
        "serviceResponse": [
          {
            "name": "claimant-details",
            "responseData": {
              "references": [
                {
                  "household": {
                    "barcodeReference": "200000000000013",
                    "applicationID": "198765432134567",
                    "applicant1": {
                      "nino": "CS700100A",
                      "title": "MR",
                      "firstForename": "JOHN",
                      "secondForename": "",
                      "surname": "DENSMORE"
                    },
                    "householdEndReason": ""
                  },
                  "renewal": {
                    "awardStartDate": "12/10/2030",
                    "awardEndDate": "12/10/2010",
                    "renewalStatus": "NOT_SUBMITTED",
                    "renewalNoticeIssuedDate": "12/10/2030",
                    "renewalNoticeFirstSpecifiedDate": "12/10/2010",
                    "renewalFormType":"D"
                  }
                }
              ]
            },
            "failure": false
          }
        ]
      },
      "status": {
        "code": "complete"
      }
    }
    ```

  Note that a success response (as above) will be returned in cases where a partial failure occurs. However, in such cases the `"renewalFormType"` field will be missing.

  An error response will be returned when tax credit claimant details cannot be retrieved at all. Please note the JSON attribute called "failure".

    ```
    {
      "OrchestrationResponse": {
        "serviceResponse": [
          {
            "name": "claimant-details",
            "failure": true
          }
        ]
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

  If the response status is complete then the async service call has completed. The response will contain a list of response objects containing responseData, which is the response of the service call made.


* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 404 NOTFOUND <br/>

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  OR when there is a server failure

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>
