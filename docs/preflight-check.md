preflight-check
----
  Return initial application startup data. The response to this service includes an upgrade status flag, account data and Journey Id.

  The upgrade status is derived from using the supplied POST data against application configuration.

  The service will return an encrypted cookie called mdtpapi which is used to drive the identity of the off-line task and must be supplied to the startup service request.

  
* **URL**

  `/native-app/preflight-check?journeyId=1234`

    The journeyId is optional. Supplying the journeyId will default the response journeyId.

* **Method:**
  
  `POST`
  
*  **JSON**

Current version information of application. The "os" attribute can be either ios, android or windows.

```json
{
    "os": "ios",
    "version" : "0.1.0"
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "upgradeRequired": true,
  "accounts": {
    "nino": "WX772755B",
    "saUtr": "618567",
    "routeToIV": false,
    "routeToTwoFactor": false,
    "journeyId": "f880d43b-bc44-4a68-b2e3-c0197963f01e"
  }
}
```

Please note the optional attributes are "nino" and "sautr".


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



