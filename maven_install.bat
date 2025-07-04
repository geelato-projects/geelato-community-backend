@echo on
@echo 设置输出为UTF-8
CHCP 65001


cd geelato-parent
call mvn install -Dmaven.test.skip=true

cd ../geelato-lang
call mvn install -Dmaven.test.skip=true

cd ../geelato-utils
call mvn install -Dmaven.test.skip=true

cd ../geelato-core
call mvn install -Dmaven.test.skip=true

cd ../geelato-security
call mvn install -Dmaven.test.skip=true

cd ../geelato-common-web
call mvn install -Dmaven.test.skip=true

cd ../geelato-dynamic-datasource
call mvn install -Dmaven.test.skip=true

cd ../geelato-plugin-manager
call mvn install -Dmaven.test.skip=true

cd ../geelato-reactor-platform
call mvn install -Dmaven.test.skip=true

cd ../geelato-web-platform
call mvn install -Dmaven.test.skip=true

cd ../geelato-web-quickstart
call mvn clean install -U -Dmaven.test.skip=true

cd ..