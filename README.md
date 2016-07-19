# native-apps-api-orchestration

[![Build Status](https://travis-ci.org/hmrc/native-apps-api-orchestration.svg?branch=master)](https://travis-ci.org/hmrc/native-apps-api-orchestration) [ ![Download](https://api.bintray.com/packages/hmrc/releases/native-apps-api-orchestration/images/download.svg) ](https://bintray.com/hmrc/releases/native-apps-api-orchestration/_latestVersion)

Consolidation of Next Generation Cconsumer API services - Driver for simpler mobile apps API calls

Requirements
------------

The following services are exposed from the micro-service.

Please note it is mandatory to supply an Accept HTTP header to all below services with the value ```application/vnd.hmrc.1.0+json```.


API
---

| *Task* | *Supported Methods* | *Description* |
|--------|----|----|
| ```/native-app/preflight-check``` | GET | Wraps Service Calls ```/profile/accounts``` [More...](docs/accounts.md) and ```/profile/native-app/version-check``` [More...](docs/versionCheck.md) |
| ```/native-app/:nino/startup/:renewalReference/:year``` | GET | Wraps Service Calls ```/income/:nino/tax-summary/:year```[More...](docs/tax-summary.md),
| ```/profile/preferences```[More...](docs/preferences.md), ```/income/tax-credits/submission/state``` [More...](docs/tax-credits-submission-state.md), 
| ```/income/:nino/tax-credits/:renewalReference/auth``` [More...](docs/authenticate.md), ```/income/:nino/tax-credits/tax-credits-decision``` [More...](docs/tax-credit-decision.md),
| ```/income/:nino/tax-credits/tax-credits-summary``` [More...](docs/tax-credits-summary.md), ```/push/registration``` [More...](docs/registration.md) |

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


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
