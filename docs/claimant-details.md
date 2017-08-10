claimant-details
----
  Get tax credit claimant details.
  
  The claimant details service allows the retrieval of details of tax credit claimants in a single request. 
  
  The claimant-details service is exposed as an asynchronous service where the poll service must be used to check the status of the running task.

  This service will return an encrypted cookie called mdtpapi which is used to drive the identity of the off-line task and must be supplied to the poll service request.

  
* **URL**

  `/native-app/{nino}/startup?journeyId={id}`

* **Method:**
  
  `POST`
  
*  **Form Post**

*  **Request body**

   ```json
   {
     "serviceRequest": [
       {
         "name": "claimant-details"
       }
     ]
   }
   ```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

  ```json
  {
    "status": {
      "code": "poll"
    }
  }
  ```

  Please note the above status could be poll, error, throttle or timeout.
  If the response status is poll, then a call is required to the `/native-app/{nino}/poll` service to understand the outcome of the call.
  If the response status is error then a server-side failure occurred.
  If the response status is timeout then server-side timed-out waiting for the backend to reply. 
  If the response status is throttle then too many current requests are being executed by the server, and the request must be re-tried. The HTTP status code for throttle status is 429.

* **Error Response:**

  * **Code:** 400 BADREQUEST <br />
    **Content:** `{"code":"BADREQUEST","message":"Bad Request"}`

  * **Code:** 404 NOTFOUND <br/>

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  * **Code:** 429 TOO MANY REQUESTS <br />
    **Content:** `{"status": { "code": "throttle"}}`

  OR for server failure

  * **Code:** 500 INTERNAL_SERVER_ERROR <br/>



