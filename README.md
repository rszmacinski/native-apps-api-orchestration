# native-apps-api-orchestration

[![Build Status](https://travis-ci.org/hmrc/native-apps-api-orchestration.svg?branch=master)](https://travis-ci.org/hmrc/native-apps-api-orchestration) [ ![Download](https://api.bintray.com/packages/hmrc/releases/native-apps-api-orchestration/images/download.svg) ](https://bintray.com/hmrc/releases/native-apps-api-orchestration/_latestVersion)

Consolidation of Next Generation Consumer API services - Driver for simpler mobile apps API calls

Requirements
------------

The following services are exposed from the micro-service.

Please note it is mandatory to supply an Accept HTTP header to all below services with the value ```application/vnd.hmrc.1.0+json```.


API
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/native-app/preflight-check``` | GET | Wraps Service Calls ```/auth/authority``` and ```/profile/native-app/version-check``` [More...](https://github.com/hmrc/customer-profile/blob/master/docs/versionCheck.md) |
| ```/native-app/:nino/startup``` | POST | Wraps Service Calls ```/income/:nino/tax-summary/:year```[More...](https://github.com/hmrc/personal-income/blob/master/docs/tax-summary.md), ```/profile/preferences```[More...](https://github.com/hmrc/customer-profile/blob/master/docs/preferences.md), ```/income/tax-credits/submission/state``` [More...](https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-submission-state.md), ```/income/:nino/tax-credits/:renewalReference/auth``` [More...](https://github.com/hmrc/personal-income/blob/master/docs/authenticate.md), ```/income/:nino/tax-credits/tax-credits-decision``` [More...](https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-decision.md), ```/income/:nino/tax-credits/tax-credits-summary``` [More...](https://github.com/hmrc/personal-income/blob/master/docs/tax-credits-summary.md), ```/push/registration``` [More...](https://github.com/hmrc/push-registration/blob/master/docs/registration.md) |

# Sandbox
All the above endpoints are accessible on sandbox with `/sandbox` prefix on each endpoint, e.g.
```
    GET /sandbox/native-app/preflight-check
```

# Version
Version of API need to be provided in `Accept` request header
```
Accept: application/vnd.hmrc.v1.0+json
```


* **URL**

  `/native-app/preflight-check`

* **Method:**

  `GET`

*  **URL Params**

   N/A

* **Success Response:**

  * **Code:** 200 <br />
    **Response body:**

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


* **URL**

  `/native-app/:nino/startup`

* **Method:**
  
  `POST`
  
*  **URL Params**

   **Required:**
 
   `nino=[Nino]`
   
   The nino given must be a valid nino. ([http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm](http://www.hmrc.gov.uk/manuals/nimmanual/nim39110.htm))

* **Success Response:**

  * **Code:** 200 <br />
    **Content:** 
        TODO

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
