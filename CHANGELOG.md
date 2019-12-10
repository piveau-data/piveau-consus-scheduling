# ChangeLog

## [1.0.1](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-scheduling/tags/1.0.1) (2019-12-10)

**Added:**
* Shell command `pipe` for list of pipes, launch a pipe and show a pipe
* Expose port 5000 in Dockerfile

**Changed:**
* Shell command `trigger` refactored

## [1.0.0](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-scheduling/tags/1.0.0) (2019-11-08)

**Added:**
* `PIVEAU_LOG_LEVEL` for configuring the global log level of the `io.piveau` package
* `PIVEAU_` prefix to logstash configuration environment variables
* `PIVEAU_CLUSTER_CONFIG` for configuring piveau cluster. See pipe launcher for config schema

**Changed:**
* Use `PipeLauncher` for running pipes
* Requires now latest LTS Java 11
* Docker base image to openjdk:11-jre

**Removed:**
* `config` element of trigger. Utilize `configs` to apply a config to all segments.
* Obsolete classes `ServiceDiscovery` and `GitRepository` 
* `piveau-exporting-hub` special handling

**Fixed:**
* Update all dependencies

## [0.1.1](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-scheduling/tags/0.!.1) (2019-07-11)

**Fixed:**
* Pipe configs handling

## [0.1.0](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-scheduling/tags/0.!.0) (2019-05-17)

**Added:**
* A service discovery mechanism
* Log config at startup (debug)

**Removed:**
* Environment configuration `PIVEAU_HUB_ADDRESS`. Deprecated due to new service discovery feature

## [0.0.1](https://gitlab.fokus.fraunhofer.de/viaduct/piveau-scheduling/tags/0.0.1) (2019-05-03)
Initial release
