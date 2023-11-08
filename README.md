# piveau importing oai-pmh
Importing records via the OAI-PMH protocol and feeding a pipe.

The service is based on the piveau-pipe-connector library. Any configuration applicable for the piveau-pipe-connector can also be used for this service.

## Table of Contents
1. [Build](#build)
1. [Run](#run)
1. [Docker](#docker)
1. [Configuration](#configuration)
    1. [Environment](#environment)
    1. [Logging](#logging)
1. [License](#license)

## Build
Requirements:
 * Git
 * Maven 3
 * Java 17

```bash
$ git clone <gitrepouri>
$ cd piveau-consus-importing-oaipmh
$ mvn package
```

## Run

```bash
$ java -jar target/importing-oaipmh.jar
```

## Docker

Build docker image:

```bash
$ docker build -t piveau/piveau-importing-oaipmh .
```

Run docker image:

```bash
$ docker run -it -p 8080:8080 piveau/piveau-importing-oaipmh
```
## Configuration

### Environment
See also piveau-pipe-connector

| Variable                           | Description                                                                     | Default Value |
|:-----------------------------------|:--------------------------------------------------------------------------------|:--------------|
| `PIVEAU_IMPORTING_SEND_LIST_DELAY` | The delay in millisecond for sending the identifier list after the last dataset | `8000`        |
| `PIVEAU_OAIPMH_ADAPTER_URI`        | The address of an oai-pmh adapter                                               | -             |

### Logging
See [logback](https://logback.qos.ch/documentation.html) documentation for more details

| Variable                   | Description                                       | Default Value                         |
|:---------------------------|:--------------------------------------------------|:--------------------------------------|
| `PIVEAU_PIPE_LOG_APPENDER` | Configures the log appender for the pipe context  | `STDOUT`                              |
| `PIVEAU_LOGSTASH_HOST`     | The host of the logstash service                  | `logstash`                            |
| `PIVEAU_LOGSTASH_PORT`     | The port the logstash service is running          | `5044`                                |
| `PIVEAU_PIPE_LOG_PATH`     | Path to the file for the file appender            | `logs/piveau-pipe.%d{yyyy-MM-dd}.log` |
| `PIVEAU_PIPE_LOG_LEVEL`    | The log level for the pipe context                | `INFO`                                |
| `PIVEAU_LOG_LEVEL`         | The general log level for the `io.piveau` package | `INFO`                                |

## License

[Apache License, Version 2.0](LICENSE.md)
