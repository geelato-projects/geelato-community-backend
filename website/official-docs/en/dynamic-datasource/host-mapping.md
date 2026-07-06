---
id: host-mapping
title: Intranet Host Mapping (Local to Prod)
sidebar_label: Host Mapping (Local Debug)
---

# Dynamic Datasource: Intranet Host/Port Mapping (Local to Prod DB)

## 1. Background

The connection information for the dynamic datasource comes from the table `platform_dev_db_connect`, where `db_hostname_ip` / `db_port` might store intranet addresses. When developers connect directly to the production main database locally (to access table data), they often don't have the same network segment environment, causing the dynamic datasource to fail to connect upon first use.

This feature is used to: Under the premise of **not modifying database table data**, do a "local mapping rewrite" for the intranet host/port through an independent text file, allowing the dynamic datasource to connect in the local environment (e.g., via public network, VPN, or SSH Tunnel).

## 2. Scope of Effect

- Only affects the `host/port` used during the creation of the JDBC URL by the dynamic datasource.
- Does not affect fields like database name, username, password, etc.
- When the mapping file is not enabled/configured, the behavior remains the same as before (still connecting directly according to the host/port in the table).

## 3. Usage

### 3.1 Prepare Mapping File

Default path: `conf/db-host-map.txt` under the application running directory.

You can also specify an absolute path via an environment variable:

- `GEELATO_DS_HOST_MAP_FILE=D:\path\to\db-host-map.txt`

### 3.2 File Format (One per line)

- Blank lines are ignored
- Lines starting with `#` or `//` are ignored (comments)
- Supports the following three formats:
  - `sourceHost=targetHost`
  - `sourceHost:sourcePort=targetHost:targetPort`
  - `sourceHost=targetHost:targetPort`

Explanation:
- `sourceHost` supports IP or domain name
- When both `sourceHost:sourcePort` and `sourceHost` mappings exist, `sourceHost:sourcePort` takes priority.

### 3.3 Examples

#### SSH Tunnel (Recommended for local troubleshooting)

The table has `172.20.10.8:3306`, locally forwarded via SSH to `127.0.0.1:13306`:

```text
172.20.10.8:3306=127.0.0.1:13306
```

#### Direct Public Network Connection

The table has `172.20.10.8:3306`, the actual reachable public network is `47.xx.xx.xx:3306`:

```text
172.20.10.8:3306=47.xx.xx.xx:3306
```

#### Change Host Only (Don't change port)

```text
172.20.10.8=47.xx.xx.xx
```

## 4. Implementation Details

- The mapping file is read and cached by the dynamic datasource module, and automatically refreshes based on the "file last modified time".
- Before creating the `HikariDataSource` and assembling the JDBC URL, the mapping result is used to rewrite the host/port.

## 5. Notes

- It is recommended to use this mapping only in local/troubleshooting scenarios; production environments should generally not rely on such rewrites.
- It is recommended not to commit the mapping file to the version repository to avoid leaking public network addresses, jump host strategies, or port planning.
- The mapping only solves "unreachable addresses" and does not solve account permissions, whitelists, SSL, network jitter, etc.