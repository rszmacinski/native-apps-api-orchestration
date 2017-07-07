push-notification get-message
----
    
    Retrieve message associated with Id and update state to answer.
  
* **URL**

  `/native-app/:nino/startup?journeyId={id}`

* **Method:**
  
  `POST`
  
*  **Request body**

```json
{
  "request": [
    {
      "serviceName": "push-notification-get-message",
      "postRequest": {
        "messageId": "c59e6746-9cd8-454f-a4fd-c5dc42db7d99"      
      }
    }
  ]
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "id":"msg-some-id",
  "subject": "Weather",
  "body": "Is it raining?",
  "responses": {
    "yes": "Yes",
    "no": "No"
  }
}
```

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



