preflight-check
----
  Return the upgrade status, account information.
  
* **URL**

  `/native-app/preflight-check`

* **Method:**
  
  `POST`
  
*  **JSON**

Supply version information. The "os" attribute can be either ios, android or windows.

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
  "upgradeRequired" : true,
  "accounts" : {
      "nino" : "WX772755B",
      "saUtr" : "618567",
      "routeToIV" : false
  }
}
```
 
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



