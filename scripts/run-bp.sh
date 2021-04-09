#!/usr/bin/env bash
set -e 

scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

source $scriptDir/env.sh

# $scriptDir/start-maven.sh
# sleep 100 # mvn-proxy needs a lot of time to start (probably thanks to gradle bootRun)
# TODO add health endpoint (to mvn-repo) and health check here instead hardcoded sleep

# # build scala-release image
# $scriptDir/build-publish-scala.sh

# # build executor image
#$scriptDir/build-executor.sh

# release scala
# docker run \
#   --rm \
#   --network $DOCKER_NETWORK \
#   communitybuild3/publish-scala \
#   https://github.com/lampepfl/dotty.git \
#   master \
#   $CM_SCALA_VERSION \
#   $PROXY_HOSTNAME

for line in $(cat coordinator/bp.csv | head -1) 
do
  repo=$(echo $line | awk -F "," '{ print $1 }')
  rev=$(echo $line | awk -F "," '{ print $2 }')
  ver=$(echo $line | awk -F "," '{ print $3 }')
  args=$(echo $line | cut -f 4- -d "," | tr "," " ")
  # echo $(cat bp.csv | awk -F "," '{print $1 " " $2 " " $3 " \"" $4 "\"" }')
  echo Running: $repo / $rev / $ver -- $args

  docker run \
    --rm \
    --network $DOCKER_NETWORK \
    communitybuild3/executor \
    $repo \
    $rev \
    $CM_SCALA_VERSION \
    $ver \
    "$args" \
    $PROXY_HOSTNAME 
done

# run munit with new scala version
# docker run \
#   --rm \
#   --network $DOCKER_NETWORK \
#   communitybuild3/executor \
#   https://github.com/scalameta/munit.git \
#   v0.7.22 \
#   $CM_SCALA_VERSION \
#   0.7.22-communityBuild \
#   "org.scalameta%munit-scalacheck org.scalameta%munit" \
#   $PROXY_HOSTNAME 

# $scriptDir/stop-maven.sh
