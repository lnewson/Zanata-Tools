#!/bin/bash

ZANATA_SERVER=
REST_SERVER=
USERNAME=
TOKEN=
PROJECT=
PROJECT_VERSION=
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
