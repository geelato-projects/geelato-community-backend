@echo on
@echo 设置输出为UTF-8
CHCP 65001

cd ..
call mvn clean package -Dmaven.test.skip=true
call mvn clean install -Dmaven.test.skip=true
cd bin