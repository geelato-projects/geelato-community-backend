@echo on
@echo 设置输出为UTF-8
CHCP 65001

call mvn install:install-file -Dfile=plugin-all-0.0.1-SNAPSHOT.jar -DgroupId=cn.geelato -DartifactId=plugin-all -Dversion=0.0.1-SNAPSHOT -Dpackaging=jar

call mvn install:install-file -Dfile=pom.xml -DgroupId=cn.geelato -DartifactId=geelato-plugins -Dversion=0.0.1-SNAPSHOT -Dpackaging=pom