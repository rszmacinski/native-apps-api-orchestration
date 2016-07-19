User Tax Account Profile
----
  Retrieves the current user's tax account identifiers, e.g. National Insurance Number (NINO), Self Assessment UTR, etc,
  and flag to describe if Identity Verification is required

* **URL**

  `/profile/accounts`

* **Method:**

  `GET`

*  **URL Params**

   N/A

* **Success Response:**

  * **Code:** 200 <br />
    **Response body:**

```json
{
  "nino" : "WX772755B",
  "saUtr" : "618567",
  "routeToIV" : false
}
```

* **Error Response:**

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"UNAUTHORIZED","message":"NINO does not exist on account"}`

  * **Code:** 401 UNAUTHORIZED <br />
    **Content:** `{"code":"LOW_CONFIDENCE_LEVEL","Confidence Level on account does not allow access"}`

  * **Code:** 406 NOT ACCEPTABLE <br />
    **Content:** `{"code":"ACCEPT_HEADER_INVALID","message":"The accept header is missing or invalid"}`

  * **Code:** 500 INTERNAL_SERVER_ERROR <br />


