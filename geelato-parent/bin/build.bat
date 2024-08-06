@echo on
@echo 设置输出为UTF-8
CHCP 65001

cd ..
call mvn install -Dmaven.test.skip=true

cd ../geelato-lang
call mvn install -Dmaven.test.skip=true

cd ../geelato-core
call mvn install -Dmaven.test.skip=true

cd ../geelato-utils
call mvn install -Dmaven.test.skip=true

cd ../../geelato-plugins/geelato-plugin-all
call mvn install -Dmaven.test.skip=true

cd ../../geelato-community/geelato-plugin-manager
call mvn install -Dmaven.test.skip=true

cd ../geelato-web-platform
call mvn install -Dmaven.test.skip=true

cd ../geelato-web-quickstart
call mvn install -Dmaven.test.skip=true

cd ../../geelato-plugins
call mvn install -Dmaven.test.skip=true