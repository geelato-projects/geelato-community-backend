#!/bin/bash

java --add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.math=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.util.concurrent=ALL-UNNAMED \
--add-opens java.base/java.net=ALL-UNNAMED \
--add-opens java.base/java.text=ALL-UNNAMED \
--add-opens java.sql/java.sql=ALL-UNNAMED \
-Dfile.encoding=UTF-8 -jar \
../target/geelato-web-quickstart-1.0.0-SNAPSHOT.jar
