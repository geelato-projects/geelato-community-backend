@echo off

@echo ========================================
@echo Phase 1: Community Base Modules
@echo ========================================

cd geelato-parent
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-lang
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-utils
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-security
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-core
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-meta
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-orm
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-common
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-dynamic-datasource
call mvn clean install -U -D maven.test.skip=true

@echo ========================================
@echo Phase 2: Sibling Projects (External Dependencies)
@echo ========================================

@echo [2-1] geelato-plugins
cd ../../geelato-plugins
call mvn clean install -U -D maven.test.skip=true

@echo [2-2] geelato-authorization
cd ../geelato-authorization
call mvn clean install -U -D maven.test.skip=true

@echo [2-3] geelato-workflow
cd ../geelato-workflow
call mvn clean install -U -D maven.test.skip=true

@echo [2-4] geelato-enterprise module
cd ../geelato-enterprise/geelato-package
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-oss
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-site-collocation
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-svcp
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-market
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-message
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-actuator
call mvn clean install -U -D maven.test.skip=true

@echo [2-5] geelato-schedule (depends on geelato-web-oss)
cd ../../geelato-schedule
call mvn clean install -U -D maven.test.skip=true

@echo ========================================
@echo Phase 3: Community Upper Modules
@echo ========================================

cd ../geelato-community-backend/geelato-plugin-manager
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-platform
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-test
call mvn clean install -U -D maven.test.skip=true

cd ../geelato-web-quickstart
call mvn clean install -U -D maven.test.skip=true

cd ..
@echo ========================================
@echo All builds completed!
@echo ========================================
