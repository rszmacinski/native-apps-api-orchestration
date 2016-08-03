startup
----
  Request to initiate startup information. Please note this is an asynchronous request.
  
* **URL**

  `/native-app/{nino}/startup`

* **Method:**
  
  `POST`
  
*  **Form Post**

   **Required:**

   `nino=[Nino]`

   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

*  **Request body**

If no token exists, then send an empty json payload ```{}```.

```json
{
    "token": "some-token"
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "status" : "poll"
}
```

Please note the above status could be poll, error or throttle.
If the response status is poll, then a call is required to the `/native-app/{nino}/poll` service to understand the outcome of the call.
If the response status is error then a server-side failure occurred.
If the response status is throttle then too many current requests are being performed by the server, and the request should be re-tried.

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



