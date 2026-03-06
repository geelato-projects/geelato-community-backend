#!/bin/bash

cd ../..

./maven_install.sh

cd geelato-web-quickstart

mvn clean package -D maven.test.skip=true

cd bin
