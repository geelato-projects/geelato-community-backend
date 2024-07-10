::设置输出为UTF-8
CHCP 65001

:: 若需指定配置文件可加上 --spring.config.location=/your_path_of_application_file/application.properties
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.sql/java.sql=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar ../target/geelato-web-quickstart-1.0.2-SNAPSHOT.jar reset_db
pause