#!/bin/bash
#
# Test script that sends out REST commands
#

# Check out a new workspace
echo "*** Creating new workspace..."
WORK=`curl -s -d dummy_data -w %{redirect_url} http://localhost:8080/client/work`
echo "Workspace is ${WORK}"

# Add two random features (might fail, name is actually random)
#curl -v -d "{ attributes: { name: 'feature-${RANDOM}', description: 'a random feature' }, tags: { generated: 'true'}}" ${WORK}/feature
#curl -v -d "{ attributes: { name: 'feature-${RANDOM}', description: 'another random feature' }, tags: { generated: 'true'}}" ${WORK}/feature

# Add a feature, a distribution and an association between the two
echo "*** Adding feature, distribution and association..."
FEAT=`curl -v -d "{ attributes: { name: 'feature-${RANDOM}', description: 'a feature' }, tags: {}}" -w %{redirect_url} ${WORK}/feature`
FEATID=`echo ${FEAT##*/} | perl -MURI::Escape -lne 'print uri_unescape($_)'`
echo "Feature is ${FEAT} => ${FEATID}"
DIST=`curl -v -d "{ attributes: { name: 'distribution-${RANDOM}', description: 'a distribution' }, tags: {}}" -w %{redirect_url} ${WORK}/distribution`
DISTID=`echo ${DIST##*/} | perl -MURI::Escape -lne 'print uri_unescape($_)'`
echo "Distribution is ${DIST} => ${DISTID}"
ASSOC=`curl -v -d "{ attributes: { leftEndpoint: '${FEATID}', leftCardinality: '1', rightEndpoint: '${DISTID}', rightCardinality: '1' }, tags: {}}" -w %{redirect_url} ${WORK}/feature2distribution`
echo "Association is ${ASSOC}"

# Get a list of artifacts
#curl ${WORK}/artifact
#curl ${WORK}/artifact/%28%26%28Bundle-SymbolicName%3Dnet.luminis.android.desktop%29%28Bundle-Version%3D1.0.0%29%29

# Add a random artifact (does not upload to OBR yet)
#echo "*** Adding a new, random artifact..."
#RND=$RANDOM
#BSN=org.apache.bundle${RND}
#VERSION=1.0.0
#NAME=${BSN}-${VERSION}
#curl -v -d "{attributes: { artifactName: '${NAME}' , mimetype: 'application/vnd.osgi.bundle', Bundle-Name: '${BSN}', Bundle-SymbolicName: '${BSN}', Bundle-Version: '${VERSION}', url: 'http://localhost:8080/obr/${NAME}.jar', artifactDescription: 'coolio', processorPid: '' }, tags: { generated: 'true' }}" ${WORK}/artifact

# Create a target
TARGET=`curl -v -d "{ attributes: { id: 'target-${RANDOM}', autoapprove: 'true' }, tags: {}}" -w %{redirect_url} ${WORK}/target`
echo "Target is ${TARGET}"

# Commit the workspace
echo "*** Committing workspace..."
curl -v -d dummy_data ${WORK}

echo "*** Done."
