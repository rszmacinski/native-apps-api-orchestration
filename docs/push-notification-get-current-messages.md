push-notification get-current-messages
----
    
    Returns all messages that have not yet been answered or acknowledged.
  
* **URL**

  `/native-app/:nino/startup?journeyId={id}`

* **Method:**
  
  `GET`
  
*  **Request body**

```json
{
  "request": [
    {
      "serviceName": "push-notification-get-current-messages"
    }
  ]
}
```

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 

```json
{
  "messages": [
    {
      "subject": "snarkle",
      "body": "Foo, bar baz!",
      "callbackUrl": "http://example.com/quux",
      "responses": {
        "yes": "Sure",
        "no": "Nope"
      },
      "messageId": "msg-some-id"
    },
    {
      "subject": "stumble",
      "body": "Alpha, Bravo!",
      "callbackUrl": "http://abstract.com/",
      "responses": {
        "yes": "Sure",
        "no": "Nope"
      },
      "messageId": "msg-other-id"
    }
  ]
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



