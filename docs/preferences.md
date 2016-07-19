User Preferences
----
  Finds a user's preferences

* **URL**

  `/profile/preferences`

* **Method:**

  `GET`

* **Success Response:**

  * **Code:** 200 <br />
    **Response body:**

```json
{
  "digital" : true,
  "email" : {
    "email" : "name@email.co.uk",
    "status" : "verified"
  }
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


