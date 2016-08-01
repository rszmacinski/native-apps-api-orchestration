poll
----
  Request for outcome to the startup service. A call to startup MUST have been performed first.
  
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

```json
{
  "status" : "poll"
}
```

Please note the above status could be complete, poll, error or throttle.
If the response status is poll, then a call is required to the `/native-app/{nino}/poll` service to understand the outcome of the call.
If the response status is error then a server-side failure occurred.
If the response status is throttle then too many current requests are being performed by the server, and the request should be re-tried.

If the response status is complete then the async service call has completed. The response will contain a set of attributes which is taxSummary, state and an optional taxCreditSummary attribute.

| *json attribute* | *Mandatory* | *Description* |
|------------------|-------------|---------------|
| `taxSummary` | yes | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-summary.md> |
| `taxCreditSummary` | no | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-summary.md> |
| `state` | yes | See <https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-submission-state.md> |

If the response attribute 'taxCreditSummary' contains no attributes then this indicates Tax-Credits are available for the user, however there is no data to display.


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



