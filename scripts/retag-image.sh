#!/usr/bin/env bash
set -e

if [ $# -ne 3 ]; then 
  echo "Wrong number of script arguments"
  exit 3
fi

IMAGE_NAME_SUFFIX="$1"
OLD_TAG="$2"
NEW_TAG="$3"

docker image tag "virtuslab/scala-community-build-${IMAGE_NAME_SUFFIX}:${OLD_TAG}" "virtuslab/scala-community-build-${IMAGE_NAME_SUFFIX}:${NEW_TAG}"
