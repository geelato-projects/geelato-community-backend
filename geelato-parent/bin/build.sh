#!/bin/bash

cd ..
mvn install -Dmaven.test.skip=true

cd ../geelato-lang
mvn install -Dmaven.test.skip=true

cd ../geelato-core
mvn install -Dmaven.test.skip=true

cd ../geelato-utils
mvn install -Dmaven.test.skip=true

cd ../../geelato-plugins/geelato-plugin-all
mvn install -Dmaven.test.skip=true

cd ../../geelato-community/geelato-plugin-manager
mvn install -Dmaven.test.skip=true

cd ../geelato-web-platform
mvn install -Dmaven.test.skip=true

cd ../geelato-web-quickstart
mvn install -Dmaven.test.skip=true

cd ../../geelato-plugins
mvn install -Dmaven.test.skip=true
