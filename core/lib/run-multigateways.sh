#!/bin/sh
startnr=$1
currentNr=$startnr
nrOfGateway=$2
max=`expr $startnr + $nrOfGateway`
LIMIT=$max
DIRNAME=`dirname $0`   

if [ $# -ne 2 ]
then
    echo "Usage: `basename $0` startnr number_of_framework_instances"
    exit 1
fi

instances="instances="
while [ "$currentNr" -lt "$LIMIT" ]
do
    instances=$instances"gateway-"$currentNr","
    currentNr=`expr $currentNr + 1`
done
echo "Starting framework"
# startDate=`date`
cd $DIRNAME
java -jar multigatewaybootstrap.jar bundle ${instances%,*} > output-felix.txt 2>&1

exit 0