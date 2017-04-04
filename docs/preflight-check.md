preflight-check
----
  Return initial application startup data. The response to this service includes an upgrade status flag, account data and Journey Id. The upgrade status is derived from using the supplied POST data against application configuration.

  The pre-flight service can be used to verify the MFA status of the user. An optional MFA operation can be supplied in the POST request for MFA status. Two operation options exist which are "start" and "outcome". The
  "start" operation is used to understand if MFA is in the correct state. The "outcome" operation is used to understand the outcome of a MFA web journey.

  The service will return an encrypted cookie called mdtpapi which is used to drive the identity of the off-line task and must be supplied to the startup service request.

  
* **URL**

  `/native-app/preflight-check?journeyId=1234`

    The journeyId is optional. Supplying the journeyId will default the response journeyId.

* **Method:**
  
  `POST`
  
*  **JSON**

Current version information of application. The "os" attribute can be either ios, android or windows.


To verify the status of the users authority record and understand if an upgrade must be performed the below POST request is used.

```json
{
    "os": "ios",
    "version" : "0.1.0"
}
```

To understand the MFA status of the user, the following POST request must be made.

```json
{
    "os": "ios",
    "version" : "0.1.0",
    "mfa":{
  	    "operation":"start"
    }
}
```

To understand the outcome of a MFA web journey, the following POST request must be made. The apiURI attribute is resolved from a previous response to pre-flight.

```json
{
    "os": "ios",
    "version" : "0.1.0",
    "mfa":{
  	    "operation":"outcome",
        "apiURI": "/multi-factor-authentication/journey/58dd8a62177e92b102a45165?origin=NGC"
    }
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

The below JSON response will be returned when either the users account has a correct 2FA/MFA status, or when pre-flight is invoked without an mfa operation.

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

If an mfa operation was supplied in the POST request and the users MFA credential strength is not Strong, the below response is returned. The webURI provides a URL
to be supplied to the browser, and the apiURI is later used to validate the outcome of the MFA web journey.

```json
{
    "upgradeRequired": true,
    "accounts": {
        "nino": "WX772755B",
        "saUtr": "618567",
        "routeToIV": false,
        "routeToTwoFactor": true,
        "journeyId": "f880d43b-bc44-4a68-b2e3-c0197963f01e"
    },
    "mfaURI": {
        "webURI": "http://localhost:9721/multi-factor-authentication/journey/58dd8a62177e92b102a45165?origin=NGC",
        "apiURI": "/multi-factor-authentication/journey/58dd8a62177e92b102a45165?origin=NGC"
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



