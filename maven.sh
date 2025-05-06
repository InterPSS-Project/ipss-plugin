#!/bin/bash

# Directory containing the IEEE ODM JAR files
IEEE_LIB_DIR="../ipss-common/ipss.lib/lib/ieee"

# Loop through all JAR files in the directory
for JAR_FILE in "$IEEE_LIB_DIR"/*.jar; do
  if [ -f "$JAR_FILE" ]; then

    FILENAME=$(basename "$JAR_FILE")
    # Use sed to extract artifactId by removing any version pattern and extension
    ARTIFACTID=$(echo "$FILENAME" | sed -E 's/-[0-9]+(\.[0-9]+)*(-SNAPSHOT)?\.jar$//')

    GROUPID="org.ieee.odm"  
    VERSION=$(echo "$FILENAME" | grep -oE '[0-9]+(\.[0-9]+)*(-SNAPSHOT)?' | head -1)
    
    if [ -z "$VERSION" ]; then
      VERSION="1.0"  # Default version if not found
    fi

    # Install the JAR file into the local Maven repository
    mvn install:install-file -Dfile="$JAR_FILE" \
                             -DgroupId="$GROUPID" \
                             -DartifactId="$ARTIFACTID" \
                             -Dversion="$VERSION" \
                             -Dpackaging=jar \
                             -DgeneratePom=true

    echo "Installed $JAR_FILE" with artifactId: $ARTIFACTID with version: $VERSION
  fi
done

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