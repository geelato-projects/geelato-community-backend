-- MQL 测试表结构（5 个表）
-- 在目标数据库手动执行此文件以初始化测试环境。
-- 命令示例：mysql -u root -p geelato < mql-test-schema.sql

-- 注册 MQL 自定义函数（increment/findinset/fuzzymatch 依赖）
-- 注意：MySQL 函数创建需要相关权限；如无权限可跳过，仅 increment 类场景受影响
CREATE FUNCTION IF NOT EXISTS gfn_increment(val BIGINT, step INT) RETURNS BIGINT
    DETERMINISTIC NO SQL RETURN val + step;

-- 组织机构（树形 pid 自引用）
CREATE TABLE IF NOT EXISTS mql_test_org (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(128),
    code        VARCHAR(64),
    pid         BIGINT,
    status      VARCHAR(32),
    create_at   DATETIME,
    creator     VARCHAR(64),
    tenant_code VARCHAR(64)
);

-- 用户（org 外键、多种字段类型、自引用 pid 树）
CREATE TABLE IF NOT EXISTS mql_test_user (
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(128),
    login_name    VARCHAR(64),
    email         VARCHAR(128),
    mobile_phone  VARCHAR(32),
    age           INT,
    score         BIGINT,
    balance       DECIMAL(12, 2),
    birthday      DATE,
    create_at     DATETIME,
    org_id        BIGINT,
    pid           BIGINT,
    enable_status INT,
    creator       VARCHAR(64),
    tenant_code   VARCHAR(64)
);

-- 订单（user+org 双外键、JSON列、保留字列 index/key/enable）
CREATE TABLE IF NOT EXISTS mql_test_order (
    id          BIGINT PRIMARY KEY,
    order_no    VARCHAR(64),
    user_id     BIGINT,
    org_id      BIGINT,
    amount      DECIMAL(14, 2),
    quantity    INT,
    status      VARCHAR(32),
    tags        JSON,
    `index`     VARCHAR(64),
    `key`       VARCHAR(64),
    `enable`    VARCHAR(32),
    create_at   DATETIME,
    creator     VARCHAR(64),
    tenant_code VARCHAR(64)
);

-- 订单明细（order 外键，多级关联）
CREATE TABLE IF NOT EXISTS mql_test_order_item (
    id           BIGINT PRIMARY KEY,
    order_id     BIGINT,
    product_name VARCHAR(128),
    qty          INT,
    price        DECIMAL(12, 2),
    create_at    DATETIME,
    creator      VARCHAR(64),
    tenant_code  VARCHAR(64)
);
