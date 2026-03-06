#!/bin/bash

mvn install:install-file -Dfile=plugin-all-0.0.1-SNAPSHOT.jar -DgroupId=cn.geelato -DartifactId=plugin-all -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar

mvn install:install-file -Dfile=pom.xml -DgroupId=cn.geelato -DartifactId=geelato-plugins -Dversion=0.0.1-SNAPSHOT -Dpackaging=pom
