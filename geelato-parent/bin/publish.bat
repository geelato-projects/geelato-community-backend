@echo on
@echo 设置输出为UTF-8
CHCP 65001

cd ..
call mvn clean deploy -Dmaven.test.skip=true -P sonatype-oss-release
cd bin
