#!/bin/sh
#
# This script can be used to check the signatures and checksums of staged ACE
# release using gpg.
#


# Params

RELEASE=${1}
DOWNLOAD=${2:-/tmp/ace-staging}

if [ -z "${RELEASE}" -o ! -d "${DOWNLOAD}" ]
then
 echo "Usage: check_staged_ace.sh <release-version> [temp-directory]"
 exit
fi

# Consts
KEYS_URL="http://www.apache.org/dist/ace/KEYS"
REL_URL="https://dist.apache.org/repos/dist/dev/ace/apache-ace-${RELEASE}/"
PWD=`pwd`
mkdir ${DOWNLOAD} 2>/dev/null

echo "################################################################################"
echo "                               IMPORTING KEYS                                   "
echo "################################################################################"
if [ ! -e "${DOWNLOAD}/KEYS" ]
then
 wget --no-check-certificate -P "${DOWNLOAD}" $KEYS_URL 
fi
gpg --import "${DOWNLOAD}/KEYS" 

if [ ! -e "${DOWNLOAD}/apache-ace-${RELEASE}" ]
then
 echo "################################################################################"
 echo "                           DOWNLOAD STAGED REPOSITORY                           "
 echo "################################################################################"

 wget \
  -e "robots=off" --wait 1 -r -np "--reject=html,txt" "--follow-tags=" \
  -P "${DOWNLOAD}/apache-ace-${RELEASE}" -nH "--cut-dirs=5" --ignore-length --no-check-certificate \
  $REL_URL

else
 echo "################################################################################"
 echo "                       USING EXISTING STAGED REPOSITORY                         "
 echo "################################################################################"
 echo "${DOWNLOAD}/apache-ace-${RELEASE}"
fi

echo "################################################################################"
echo "                          CHECK SIGNATURES AND DIGESTS                          "
echo "################################################################################"

cd ${DOWNLOAD}/apache-ace-${RELEASE}
for i in `find . -type f -printf '%P\n' | grep -v '\.\(asc\|sha\|md5\)$'`
do
 f=`echo $i`
 echo "checking $f" 

 gpg --verify $f.asc
 if [ "$?" = "0" ]; then echo " ASC: OK"; else echo " ASC: FAIL"; fi

 if [ "`cat $f.md5 2>/dev/null`" = "`gpg --print-md md5 $f 2>/dev/null`" ];
 then echo " MD5: OK"; else echo " MD5: FAIL"; fi

 if [ "`cat $f.sha 2>/dev/null`" = "`gpg --print-md sha512 $f 2>/dev/null`" ];
 then echo " SHA: OK"; else echo " SHA: FAIL"; fi

done
cd $PWD
echo "################################################################################"

