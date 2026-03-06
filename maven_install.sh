#!/bin/bash

if [ -d "/opt/homebrew/opt/openjdk@17" ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
elif [ -d "/usr/local/opt/openjdk@17" ]; then
    export JAVA_HOME=/usr/local/opt/openjdk@17
elif [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
elif [ -d "/usr/lib/jvm/temurin-17-jdk" ]; then
    export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk
else
    echo "错误: 未找到 Java 17 安装路径"
    exit 1
fi

export PATH=$JAVA_HOME/bin:$PATH
echo "使用 JAVA_HOME: $JAVA_HOME"

cd geelato-parent
mvn clean install -U -D maven.test.skip=true

cd ../geelato-lang
mvn clean install -U -D maven.test.skip=true

cd ../geelato-utils
mvn clean install -U -D maven.test.skip=true

cd ../geelato-security
mvn clean install -U -D maven.test.skip=true

cd ../geelato-core
mvn clean install -U -D maven.test.skip=true

cd ../geelato-meta
mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-common
mvn clean install -U -D maven.test.skip=true

cd ../geelato-orm
mvn clean install -U -D maven.test.skip=true

cd ../geelato-dynamic-datasource
mvn clean install -U -D maven.test.skip=true

cd ../geelato-plugin-manager
mvn clean install -U -D maven.test.skip=true

cd ../geelato-reactor-platform
mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-platform
mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-quickstart
mvn clean install -U -D maven.test.skip=true

cd ..
