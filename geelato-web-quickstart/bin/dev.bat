@echo off
setlocal enabledelayedexpansion
CHCP 65001

set ENV_FILE=%~dp0.env
if exist "%ENV_FILE%" (
    echo Loading environment variables from .env ...
    for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
        set "line=%%a"
        if not "!line!"=="" (
            set "firstChar=!line:~0,1!"
            if not "!firstChar!"=="#" (
                if not "%%b"=="" set "%%a=%%b"
            )
        )
    )
) else (
    echo Warning: .env file not found at %ENV_FILE%
    echo Please copy .env.example to .env and modify it for your local environment.
)

java --add-opens java.base/java.lang=ALL-UNNAMED ^
--add-opens java.base/java.math=ALL-UNNAMED ^
--add-opens java.base/java.util=ALL-UNNAMED ^
--add-opens java.base/java.util.concurrent=ALL-UNNAMED ^
--add-opens java.base/java.net=ALL-UNNAMED ^
--add-opens java.base/java.text=ALL-UNNAMED ^
--add-opens java.sql/java.sql=ALL-UNNAMED ^
-Dfile.encoding=UTF-8 -jar ^
../target/geelato-web-quickstart-1.0.0-SNAPSHOT.jar
endlocal
pause
