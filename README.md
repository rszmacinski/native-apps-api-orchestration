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
| ```/native-app/preflight-check``` | POST | Return pre-flight information. The cookie named mdtpapi will be returned from this service and must be supplied to the startup service. [More...](docs/preflight-check.md) |
| ```/native-app/:nino/startup``` | POST | Initiate an async service call to return personal tax data. The cookie named mdtpapi will be returned from this service and must be supplied to the poll service.  [More...](docs/startup.md) |
| ```/native-app/:nino/poll``` | GET | Poll the status of the async task which was initiated from startup. The cookie named mdtpapi must be supplied to this service. [More...](docs/poll.md) |
| Feedback ```/native-app/:nino/startup``` | POST | Initiate an async service to submit feedback. The cookie named mdtpapi will be returned from this service and must be supplied to the poll service.  [More...](docs/feedback.md) |
| Feedback ```/native-app/:nino/poll``` | GET | Poll the status of the async feedback task which was initiated from startup. The cookie named mdtpapi must be supplied to this service. [More...](docs/feedback-poll.md) |

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
