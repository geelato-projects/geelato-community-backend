@echo off
REM =============================================================================
REM 把 geelato community 发布到 Sonatype Central Portal (Maven Central)。
REM
REM 用法：
REM   publish-central.bat <release-version> [settings.xml]
REM
REM 示例：
REM   publish-central.bat 1.0.0
REM   publish-central.bat 1.0.0 ..\bin\release-settings.xml
REM
REM 前置条件（详见 geelato-community\RELEASE.md）：
REM   1) 已生成 Central Portal User Token，并填入 settings.xml 的 <server id="central">
REM   2) 本机 PATH 上有 gpg，并已导入签名密钥（gpg --list-secret-keys 能看到）
REM
REM 本脚本会把相关模块版本临时设为发布版本并发布；脚本退出前会尝试还原为 SNAPSHOT。
REM 建议在干净的 git 工作区运行（构建后用 git checkout 还原 pom）。
REM =============================================================================
setlocal enabledelayedexpansion

set "VERSION=%~1"
set "SETTINGS=%~2"
set "EXTRA_ARGS="
if not "%SETTINGS%"=="" set "EXTRA_ARGS=-s %SETTINGS%"

if "%VERSION%"=="" goto :usage
echo %VERSION% | findstr /I "SNAPSHOT" >nul && goto :usage

REM 仓库根定位（本脚本位于 geelato-community\bin\ 下）
set "SCRIPT_DIR=%~dp0"
set "COMMUNITY_DIR=%SCRIPT_DIR%.."
set "ENTERPRISE_DIR=%COMMUNITY_DIR%\.."

pushd "%COMMUNITY_DIR%"
set "COMMUNITY_DIR=%CD%"
popd
pushd "%ENTERPRISE_DIR%"
set "ENTERPRISE_DIR=%CD%"
popd

set "SNAPSHOT_VER=1.0.0-SNAPSHOT"

echo ============================================================
echo  发布版本： %VERSION%
echo  community： %COMMUNITY_DIR%
echo  enterprise：%ENTERPRISE_DIR%
echo ============================================================

REM ---------- 环境自检 ----------
where mvn >nul 2>&1 || (echo ERROR: 未找到 mvn & exit /b 1)
where gpg >nul 2>&1 || (echo ERROR: 未找到 gpg ^(签名必需^) & exit /b 1)
gpg --list-secret-keys >nul 2>&1 || (echo ERROR: gpg 无可用私钥 & exit /b 1)

REM ---------- 1) 设版本 ----------
echo [1/3] 设置发布版本 %VERSION% ...

REM community 内全部模块统一版本
pushd "%COMMUNITY_DIR%"
call mvn -q -N versions:set -DnewVersion=%VERSION% -DgenerateBackupPoms=false -DprocessAllModules=true %EXTRA_ARGS%
call mvn -q -f geelato-parent\pom.xml versions:set -DnewVersion=%VERSION% -DgenerateBackupPoms=false %EXTRA_ARGS%
popd

REM 外部模块
pushd "%ENTERPRISE_DIR%\geelato-package"
call mvn -q versions:set -DnewVersion=%VERSION% -DgenerateBackupPoms=false %EXTRA_ARGS%
popd

pushd "%ENTERPRISE_DIR%\geelato-plugins"
call mvn -q -N versions:set -DnewVersion=%VERSION% -DgenerateBackupPoms=false -DprocessAllModules=true %EXTRA_ARGS%
popd

REM ---------- 2) 预装 parent / bom / 插件父 到本地仓库 ----------
echo [2/3] 预装 parent / bom ...
pushd "%COMMUNITY_DIR%"
call mvn -q -pl geelato-parent,geelato-framework-bom install -DskipTests %EXTRA_ARGS%
popd
pushd "%ENTERPRISE_DIR%\geelato-plugins"
call mvn -q -pl . install -N -DskipTests %EXTRA_ARGS%
popd

REM ---------- 3) 逐模块构建并发布（按依赖顺序） ----------
echo [3/3] 构建并发布 ...
set "FAIL=0"

for %%M in (
    geelato-lang
    geelato-utils
    geelato-security
    geelato-core
    geelato-meta
    geelato-orm
    geelato-web-common
) do (
    call :deploy "%COMMUNITY_DIR%\%%M" %%M || set "FAIL=1"
)
call :deploy "%ENTERPRISE_DIR%\geelato-package" geelato-package || set "FAIL=1"
call :deploy "%ENTERPRISE_DIR%\geelato-plugins\geelato-plugin-all" plugin-all || set "FAIL=1"
call :deploy "%COMMUNITY_DIR%\geelato-framework-starter" geelato-framework-starter || set "FAIL=1"
call :deploy "%COMMUNITY_DIR%\geelato-web-platform" geelato-web-platform || set "FAIL=1"
call :deploy "%COMMUNITY_DIR%\geelato-web-runtime" geelato-web-runtime || set "FAIL=1"
call :deploy "%COMMUNITY_DIR%\geelato-app-scaffold-starter" geelato-app-scaffold-starter || set "FAIL=1"

REM ---------- 还原 SNAPSHOT（尽量还原，建议以 git checkout 为准） ----------
echo 还原 SNAPSHOT 版本 ...
pushd "%COMMUNITY_DIR%"
call mvn -q -N versions:set -DnewVersion=%SNAPSHOT_VER% -DgenerateBackupPoms=false -DprocessAllModules=true %EXTRA_ARGS% >nul 2>&1
popd
pushd "%ENTERPRISE_DIR%\geelato-package"
call mvn -q versions:set -DnewVersion=0.0.1-SNAPSHOT -DgenerateBackupPoms=false %EXTRA_ARGS% >nul 2>&1
popd
pushd "%ENTERPRISE_DIR%\geelato-plugins"
call mvn -q -N versions:set -DnewVersion=0.0.1-SNAPSHOT -DgenerateBackupPoms=false -DprocessAllModules=true %EXTRA_ARGS% >nul 2>&1
popd

if "%FAIL%"=="1" (
    echo ============================================================
    echo  有模块发布失败，请查看上方日志。
    echo  建议执行 git checkout --恢复 pom.xml 后重试。
    echo ============================================================
    exit /b 1
)

echo ============================================================
echo  发布任务已提交到 Central Portal。
echo  登录 https://central.sonatype.com 查看发布状态。
echo ============================================================
exit /b 0

:deploy
REM %1 = 模块绝对路径  %2 = 模块名
pushd "%~1"
echo ---- 发布 %~2 (%VERSION%) ----
call mvn clean deploy -P release -DskipTests %EXTRA_ARGS%
set "RC=!errorlevel!"
popd
if not "!RC!"=="0" (
    echo ERROR: %~2 发布失败
    exit /b 1
)
exit /b 0

:usage
echo ERROR: 必须提供正式版本号（非 SNAPSHOT）。
echo 用法： publish-central.bat ^<release-version^> [settings.xml]
echo 示例： publish-central.bat 1.0.0 ..\bin\release-settings.xml
exit /b 1
