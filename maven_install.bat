@echo on
@echo 设置输出为UTF-8
CHCP 65001


cd geelato-parent
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-lang
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-utils
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-core
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-meta
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-security
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-common
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-orm
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-dynamic-datasource
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-plugin-manager
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-reactor-platform
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-platform
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-quickstart
call mvn clean install -U -D maven.test.skip=true

cd ..