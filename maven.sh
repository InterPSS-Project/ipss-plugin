#!/bin/bash

# Directory containing the IEEE ODM JAR files
IEEE_LIB_DIR="../ipss-common/ipss.lib/lib/ieee"

# Loop through all JAR files in the directory
for JAR_FILE in "$IEEE_LIB_DIR"/*.jar; do
  if [ -f "$JAR_FILE" ]; then

    ARTIFACTID="ieee.odm"
    GROUPID="org.ieee.odm"  
    VERSION="1.0"           

    # Install the JAR file into the local Maven repository
    mvn install:install-file -Dfile="$JAR_FILE" \
                             -DgroupId="$GROUPID" \
                             -DartifactId="$ARTIFACTID" \
                             -Dversion="$VERSION" \
                             -Dpackaging=jar \
                             -DgeneratePom=true

    echo "Installed $JAR_FILE"
  fi
done

# install the ipss.core.lib jar file
IPSS_LIB_DIR="../ipss-common/ipss.lib/lib/ipss"
JAR_FILE="$IPSS_LIB_DIR"/ipss.core.lib-1.0.jar; 
ARTIFACTID="com.interpss"
GROUPID="ipss.core.lib"  
VERSION="1.0"           

# Install the JAR file into the local Maven repository
mvn install:install-file -Dfile="$JAR_FILE" \
                            -DgroupId="$GROUPID" \
                            -DartifactId="$ARTIFACTID" \
                            -Dversion="$VERSION" \
                            -Dpackaging=jar \
                             -DgeneratePom=true

echo "Installed $JAR_FILE"


mvn clean install -DskipTests
