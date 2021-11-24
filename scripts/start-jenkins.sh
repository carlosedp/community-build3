#!/usr/bin/env bash
set -e

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
source $scriptDir/utils.sh

if [ -z "$CB_DOCKER_USERNAME" ]; then
  echo >&2 "CB_DOCKER_USERNAME env variable has to be set and nonempty"
  exit 1
fi

if [ -z "$CB_DOCKER_PASSWORD" ]; then
  echo >&2 "CB_DOCKER_PASSWORD env variable has to be set and nonempty"
  exit 1
fi

if [ -z "$CB_K8S_NAMESPACE" ]; then
  echo >&2 "CB_K8S_NAMESPACE env variable has to be set and nonempty"
  exit 1
fi

if [ -z "${CB_BUILD_CRON_TRIGGER+x}" ]; then
  echo >&2 "CB_BUILD_CRON_TRIGGER env variable has to be set"
  exit 1
fi

HELM_EXPERIMENTAL_OCI=1 helm registry login operatorservice.azurecr.io -u "$CB_DOCKER_USERNAME" -p "$CB_DOCKER_PASSWORD"

scbk create configmap jenkins-seed-jobs --from-file=$scriptDir/../jenkins/seeds --dry-run=client -o yaml | scbk apply -f -
scbk create configmap jenkins-common-lib-vars --from-file=$scriptDir/../jenkins/common-lib/vars --dry-run=client -o yaml | scbk apply -f -
scbk create configmap jenkins-build-configs --from-file=$scriptDir/../env/prod/config --dry-run=client -o yaml | scbk apply -f -

HELM_EXPERIMENTAL_OCI=1 helm --namespace="$CB_K8S_NAMESPACE" \
  install jenkins oci://operatorservice.azurecr.io/charts/op-svc-jenkins-crs --version 0.1.3 -f k8s/jenkins.yaml \
  --set jenkins.namespace="$CB_K8S_NAMESPACE" \
  --set 'jenkins.podSpec.jenkinsController.env[0].name'=BUILD_CRON_TRIGGER \
  --set 'jenkins.podSpec.jenkinsController.env[0].value'="$CB_BUILD_CRON_TRIGGER" \
  --set 'jenkinsConfigurationsAsCode[0].namespace'="$CB_K8S_NAMESPACE" \
  --set 'jenkinsGroovyScripts[0].namespace'="$CB_K8S_NAMESPACE"
