#!/usr/bin/env bash

set -eu

git config --global user.email "travis-ci@atomist.com"
git config --global user.name "Travis CI"

export ATOMIST_VERSION=$(lein/lein atomistci set-version)

lein/lein do clean, dynamodb-local test

if [ "${TRAVIS_BRANCH}" == "master" ] && [ -n "$ATOMIST_VERSION" ] && [ "${TRAVIS_PULL_REQUEST}" == "false" ] ; then
    docker login -u ${ARTIFACTORY_USER} -p ${ARTIFACTORY_PWD} -e bot@atomist.com sforzando-docker-dockerv2-local.artifactoryonline.com
    lein/lein do clean, metajar, container build
    docker tag sforzando-docker-dockerv2-local.artifactoryonline.com/rugarchives:${ATOMIST_VERSION} sforzando-docker-dockerv2-local.artifactoryonline.com/rugarchives:latest
    docker push sforzando-docker-dockerv2-local.artifactoryonline.com/rugarchives
    lein/lein atomistci push-tag ${TRAVIS_BUILD_NUMBER}
fi
