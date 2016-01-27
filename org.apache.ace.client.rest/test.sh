#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under the terms of ASLv2 (http://www.apache.org/licenses/LICENSE-2.0).
#
# Test script that sends out REST commands
#

# Check out a new workspace
echo "*** Creating new workspace..."
WORK=`curl -s -d dummy_data -w %{redirect_url} http://localhost:8080/client/work`
echo "Workspace is ${WORK}"

echo "*** Adding artifact, feature, distribution, target and all associations..."

RND=$RANDOM
BSN=org.apache.bundle${RND}
VERSION=1.0.0
NAME=${BSN}-${VERSION}
ART=`curl -v -d "{attributes: { artifactName: '${NAME}' , mimetype: 'application/vnd.osgi.bundle', Bundle-Name: '${BSN}', Bundle-SymbolicName: '${BSN}', Bundle-Version: '${VERSION}', url: 'http://localhost:8080/obr/${NAME}.jar', artifactDescription: 'coolio', processorPid: '' }, tags: { generated: 'true' }}" -w %{redirect_url} ${WORK}/artifact`
ARTID=`echo ${ART##*/}`
echo "Artifact is ${ART} => ${ARTID}"

FEAT=`curl -v -d "{ attributes: { name: 'feature-${RANDOM}', description: 'a feature' }, tags: {}}" -w %{redirect_url} ${WORK}/feature`
FEATID=`echo ${FEAT##*/}`
echo "Feature is ${FEAT} => ${FEATID}"

DIST=`curl -v -d "{ attributes: { name: 'distribution-${RANDOM}', description: 'a distribution' }, tags: {}}" -w %{redirect_url} ${WORK}/distribution`
DISTID=`echo ${DIST##*/}`
echo "Distribution is ${DIST} => ${DISTID}"

TARGET=`curl -v -d "{ attributes: { id: 'target-${RANDOM}', autoapprove: 'true' }, tags: {}}" -w %{redirect_url} ${WORK}/target`
TARGETID=`echo ${TARGET##*/}`
echo "Target is ${TARGET} => ${TARGETID}"

ASSOC1=`curl -v -d "{ attributes: { left: '${ARTID}', leftCardinality: '1', right: '${FEATID}', rightCardinality: '1' }, tags: {}}" -w %{redirect_url} ${WORK}/artifact2feature`
echo "Association is ${ASSOC1}"

ASSOC2=`curl -v -d "{ attributes: { left: '${FEATID}', leftCardinality: '1', right: '${DISTID}', rightCardinality: '1' }, tags: {}}" -w %{redirect_url} ${WORK}/feature2distribution`
echo "Association is ${ASSOC2}"

ASSOC3=`curl -v -d "{ attributes: { left: '${DISTID}', leftCardinality: '1', right: '${TARGETID}', rightCardinality: '1' }, tags: {}}" -w %{redirect_url} ${WORK}/distribution2target`
echo "Association is ${ASSOC3}"

# Get a list of artifacts
#curl ${WORK}/artifact

# Commit the workspace
echo "*** Committing workspace..."
curl -v -d dummy_data ${WORK}

echo "*** Done."
