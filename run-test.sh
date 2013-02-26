#!/bin/bash

# ZANATA_SERVER=http://zanata-fearless.lab.eng.bne.redhat.com:8080/
ZANATA_SERVER=http://zanatatest.usersys.redhat.com/zanata/
REST_SERVER=http://skynet-dev.usersys.redhat.com:8080/TopicIndex/seam/resource/rest
USERNAME=admin
TOKEN=b6d7044e9ee3b2447c28fb7c50d86d98
PROJECT=skynet-topics
PROJECT_VERSION=1
MAINCLASS=Main
DEFAULT_LOCALE=en-US
MIN_ZANATA_CALL_INTERVAL=0.2

# Get the directory hosting the script. This is important if the script is called from 
# another working directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -DtopicIndex.zanataServer=${ZANATA_SERVER} \
     -DtopicIndex.zanataUsername=${USERNAME} \
     -DtopicIndex.zanataToken=${TOKEN} \
     -DtopicIndex.zanataProject=${PROJECT} \
     -DtopicIndex.zanataProjectVersion=${PROJECT_VERSION} \
     -DtopicIndex.skynetServer=${REST_SERVER} \
     -DtopicIndex.defaultLocale=${DEFAULT_LOCALE} \
     -DtopicIndex.minZanataCallInterval=${MIN_ZANATA_CALL_INTERVAL} \
     -jar ${DIR}/target/ZanataTranslationFixTool-0.0.1-SNAPSHOT.jar "$@"
