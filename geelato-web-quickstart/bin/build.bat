@echo on
@echo 设置输出为UTF-8
CHCP 65001

cd ../..

call maven_install.bat

cd geelato-web-quickstart

call mvn clean package -Dmaven.test.skip=true

cd bin