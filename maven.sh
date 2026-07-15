#!/bin/bash

#install the ipss.core.lib jar file

IPSS_LIB_DIR="../ipss-common/ipss.lib/lib/ipss"
JAR_FILE="$IPSS_LIB_DIR"/ipss.core.lib-1.0.jar; 
ARTIFACTID="ipss.core.lib"
GROUPID="com.interpss"
VERSION="1.0"           

# Install the JAR file into the local Maven repository
mvn install:install-file -Dfile="$JAR_FILE" \
                            -DgroupId="$GROUPID" \
                            -DartifactId="$ARTIFACTID" \
                            -Dversion="$VERSION" \
                            -Dpackaging=jar \
                             -DgeneratePom=true

echo "Installed $JAR_FILE" with artifactId: $ARTIFACTID


mvn clean install -DskipTests