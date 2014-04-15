#!/bin/bash

ZANATA_SERVER=
REST_SERVER=
USERNAME=
TOKEN=
PROJECT=
PROJECT_VERSION=
MAINCLASS=Main
MIN_ZANATA_CALL_INTERVAL=0.2

# Get the directory hosting the script. This is important if the script is called from 
# another working directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

java -Dpressgang.zanataServer=${ZANATA_SERVER} \
     -Dpressgang.zanataUsername=${USERNAME} \
     -Dpressgang.zanataToken=${TOKEN} \
     -Dpressgang.zanataProject=${PROJECT} \
     -Dpressgang.zanataProjectVersion=${PROJECT_VERSION} \
     -Dpressgang.restServer=${REST_SERVER} \
     -Dpressgang.minZanataCallInterval=${MIN_ZANATA_CALL_INTERVAL} \
     -jar ${DIR}/target/translation-fix-0.0.1-SNAPSHOT.jar "$@"
