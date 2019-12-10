# piveau scheduling
Microservice for scheduling pipes.

## Table of Contents
1. [Build](#build)
1. [Run](#run)
1. [Interface](#interface)
1. [Docker](#docker)
1. [Configuration](#configuration)
    1. [Environment](#environment)
    1. [Logging](#logging)
1. [License](#license)

## Build
Requirements:
 * Git
 * Maven 3
 * Java 11

```bash
$ git clone https://github.com/piveau-data/piveau-consus-scheduling.git
$ cd piveau-consus-scheduling
$ mvn package
```

## Run

```bash
$ java -jar target/piveau-scheduling-far.jar
```

## Interface

The documentation of the REST service can be found when the root context is opened in a browser.

```
http://localhost:8080/
```

## Docker

Build docker image:
```bash
$ docker build -t piveau/piveau-scheduling .
```

Run docker image:
```^bash
$ docker run -it -p 8080:8080 piveau/piveau-scheduling
```

## Configuration

### Environment
| Variable| Description | Default Value |
| :--- | :--- | :--- |
| `PIVEAU_SERVICE_DISCOVERY` |  Json object describing the mapping between service name and endpoint | _No default value_ |
| `PIVEAU_SCHEDULING_PIPE_REPOSITORY` | | _No default value_ |
| `PIVEAU_SCHEDULING_PIPE_REPOSITORY_BRANCH` | | master |
| `GITLAB_USERNAME` | | _No default value_ |
| `GITLAB_TOKEN` | | _No default value_ |

### Logging
See [logback](https://logback.qos.ch/documentation.html) documentation for more details

| Variable| Description | Default Value |
| :--- | :--- | :--- |
| `PIVEAU_PIPE_LOG_APPENDER` | Configures the log appender for the pipe context | `STDOUT` |
| `LOGSTASH_HOST`            | The host of the logstash service | `logstash` |
| `LOGSTASH_PORT`            | The port the logstash service is running | `5044` |
| `PIVEAU_PIPE_LOG_PATH`     | Path to the file for the file appender | `logs/piveau-pipe.%d{yyyy-MM-dd}.log` |
| `PIVEAU_PIPE_LOG_LEVEL`    | The log level for the pipe context | `INFO` |

## License

[Apache License, Version 2.0](LICENSE.md)
