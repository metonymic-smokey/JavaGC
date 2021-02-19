#!/bin/sh
set -euxo pipefail

mvn verify
java -Xms4g -Xmx4g -XX:+AlwaysPreTouch -jar target/benchmarks.jar
