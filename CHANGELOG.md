# ChangeLog

## Unreleased

**Added:**
* buildInfo.json for build info via `/health` path
* config.schema.json
* Configuration change listener
* `PIVEAU_LOG_LEVEL` for general `io.piveau' package log level configuration
* `sendHash` for configuring optional hash calculation in pipe
* `sendHash` to config schema
 
**Changed:**
* `PIVEAU_` prefix to logstash configuration environment variables
* Hash is optional and calculation is based on canonical algorithm

**Removed:**

**Fixed:**
* Update all dependencies

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
