#!/usr/bin/env bash
set -euox pipefail

pushd ant-tracks-analyzer/
docker build -t ant-tracks-analyzer .
popd

pushd ant-tracks-jvm/
docker build -t ant-tracks-jvm .
popd


