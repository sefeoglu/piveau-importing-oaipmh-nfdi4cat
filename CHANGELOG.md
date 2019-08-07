# ChangeLog

## Unreleased

**Added:**
* buildInfo.json for build info via `/health` path
* config.schema.json

**Changed:**
* Add `PIVEAU_` prefix to logstash configuration environment variables
* Upgrade to Vert.x 3.8.0
* Upgrade to pipe-connector 0.0.4

**Removed:**

**Fixed:**

## [0.1.0](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-importing-oaipmh/tags/0.1.0) (2019-05-17)

**Added:**
* `catalogue` read from configuration and pass it to the info object
* Environment `PIVEAU_IMPORTING_SEND_LIST_DELAY` for a configurable delay
* `sendListDelay` pipe configuration option

**Changed:**
* Readme

**Removed:**
* `mode` configuration and fetchIdentifier

## [0.0.1](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-importing-oaipmh/tags/0.0.1) (2019-05-03)
Initial release
