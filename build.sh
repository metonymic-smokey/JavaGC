#!/usr/bin/env bash
set -euox pipefail

pushd ant-tracks-analyzer/
docker build -t samyaks/object-analyzer-ant-tracks-analyzer .
popd

pushd ant-tracks-jvm/
docker build -t samyaks/object-analyzer-ant-tracks-jvm .
popd

pushd analysis
docker build -t samyaks/object-analyzer-analyzer .
popd

docker build -t samyaks/object-analyzer .
docker push samyaks/object-analyzer
