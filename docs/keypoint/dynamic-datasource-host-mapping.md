# 动态数据源：内网 Host/Port 映射（本地连生产库）

## 背景

动态数据源的连接信息来自表 `platform_dev_db_connect`，其中 `db_hostname_ip` / `db_port` 可能存的是内网地址。开发者在本地直连生产主库（能访问表数据）时，往往不具备同网段网络环境，导致动态数据源在首次使用时无法连通。

该特性用于：在**不修改数据库表数据**的前提下，通过一个独立的文本文件对内网 host/port 做“本地映射重写”，让动态数据源在本地环境可以连通（例如通过公网、VPN 或 SSH Tunnel）。

## 生效范围

- 仅影响动态数据源创建 JDBC URL 过程中使用到的 `host/port`。
- 不影响库名、用户名、密码等字段。
- 不启用/不配置映射文件时，行为与原先保持一致（仍按表内 host/port 直连）。

## 使用方式

### 1) 准备映射文件

默认路径：应用运行目录下的 `conf/db-host-map.txt`

也可以通过环境变量指定绝对路径：

- `GEELATO_DS_HOST_MAP_FILE=D:\path\to\db-host-map.txt`

### 2) 文件格式（每行一条）

- 空行忽略
- 以 `#` 或 `//` 开头的行忽略（注释）
- 支持如下三种写法：
  - `sourceHost=targetHost`
  - `sourceHost:sourcePort=targetHost:targetPort`
  - `sourceHost=targetHost:targetPort`

说明：

- `sourceHost` 支持 IP 或域名
- 当同时存在 `sourceHost:sourcePort` 与 `sourceHost` 两种映射时，优先命中 `sourceHost:sourcePort`

### 3) 示例

#### SSH Tunnel（推荐本地排障方式）

表里为 `172.20.10.8:3306`，本地用 SSH 端口转发到 `127.0.0.1:13306`：

```text
172.20.10.8:3306=127.0.0.1:13306
```

#### 公网直连

表里为 `172.20.10.8:3306`，实际可达公网为 `47.xx.xx.xx:3306`：

```text
172.20.10.8:3306=47.xx.xx.xx:3306
```

#### 只改 host（不改端口）

```text
172.20.10.8=47.xx.xx.xx
```

## 实现要点

- 映射文件由动态数据源模块读取与缓存，并基于“文件最后修改时间”自动刷新。
- 在创建 `HikariDataSource` 拼接 JDBC URL 前，使用映射结果重写 host/port。

代码位置：

- 映射文件加载器：[DbHostMapFileLoader](file:///d:/geelato/geelato-enterprise/geelato-community/geelato-dynamic-datasource/src/main/java/cn/geelato/datasource/DbHostMapFileLoader.java)
- 动态建池时应用映射：[DynamicDataSourceRegistry](file:///d:/geelato/geelato-enterprise/geelato-community/geelato-dynamic-datasource/src/main/java/cn/geelato/datasource/DynamicDataSourceRegistry.java)

## 注意事项

- 建议只在本地/排障场景使用该映射；生产环境通常不应依赖此类重写。
- 映射文件建议不要提交到版本库，避免泄露公网地址、跳板策略或端口规划。
- 映射只解决“地址不可达”，不解决账号权限、白名单、SSL、网络抖动等问题。

