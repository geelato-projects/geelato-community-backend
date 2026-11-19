# Global Context

This page explains:

- `cn.geelato.core.GlobalContext`

in `geelato-core`.

It currently acts as a low-level global runtime entry for:

- environment flag
- security level
- column-encryption and API-encryption switches
- default encryption algorithm
- AES / SM4 / SM2 / RSA key loading

## What It Solves

`GlobalContext` centralizes runtime values that many low-level components need to read consistently, such as:

- which environment the system is in
- whether column encryption is enabled
- whether API encryption semantics are enabled
- which encryption algorithm is currently selected
- where the current algorithm keys come from

## Current Global Items

### Environment

The code currently defines:

- `__Environment__ = "development"`

and exposes it through:

- `GlobalContext.getEnvironment()`

### Security Level

The security level is a deployment-level protection grading, configured through an environment variable:

- `GEELATO_SECURITY_LEVEL`
- an integer from 0 to 10, default `2`
- illegal values (non-numeric or out of range) fail at startup
- read once at class-loading time and fixed afterwards

Level definitions:

| Code | Name | Column Encryption | API Encryption | Anonymous Login |
|---|---|---|---|---|
| 0 | Development | off | off | on |
| 1 | Basic | on | off | on |
| 2 (default) | Standard | on | on | on |
| 3 | Enhanced | on | on | on |
| 4-9 | Reserved (same as 3) | on | on | on |
| 10 | Strict | on | on | off |

Derived rules:

- column encryption: enabled when level ≥ 1 (`getColumnEncryptOption()`)
- API encryption: enabled when level ≥ 2 (`getApiEncryptOption()`)

### Anonymous Access and Cache

The anonymous login channel is decided by:

- the environment variable `GEELATO_ANONYMOUS_ENABLED` when set explicitly, accepting `true` / `false` (case-insensitive); illegal values fail at startup
- otherwise by the security level: on when level < 10, off when level ≥ 10 (`getAnonymousOption()`)

The cache switch `__CACHE__` depends on whether the environment is `product`.

## What Is Already Environment-Variable Driven

The following items are explicitly supported through environment variables:

- `GEELATO_SECURITY_LEVEL`
- `GEELATO_ANONYMOUS_ENABLED`
- `GEELATO_ENCRYPT_TYPE`
- `GEELATO_AES_KEY`
- `GEELATO_SM4_KEY`
- `GEELATO_SM2_PUBLIC_KEY`
- `GEELATO_SM2_PRIVATE_KEY`
- `GEELATO_RSA_PUBLIC_KEY`
- `GEELATO_RSA_PRIVATE_KEY`

The rule is:

- use the environment variable first when it exists and is non-empty
- otherwise fall back to the built-in default value

## What Is Not Yet Environment-Variable Driven

The following items are still static constants in the current code:

- `__Environment__`
- `__ColumnEncrypt__` (derived from the security level)
- `__ApiEncrypt__` (derived from the security level)

So the accurate statement for the current repository state is:

- encryption algorithm and keys already support environment variables
- security level and the anonymous switch support environment variables (`GEELATO_SECURITY_LEVEL` / `GEELATO_ANONYMOUS_ENABLED`)
- the environment flag is still a static constant
- column-encryption and API-encryption switches are derived from the security level and cannot be set independently

## How To Choose the Encryption Algorithm

The default algorithm is read through:

- `GlobalContext.getEncryptType()`

which prefers:

- `GEELATO_ENCRYPT_TYPE`

`EncryptUtils` currently supports:

- `aes`
- `rsa`
- `sm2`
- `sm4`

Encryption uses the current global algorithm, while decryption reads the algorithm prefix from the stored value itself.

Stored encrypted values look like:

```text
aes:xxxx
rsa:xxxx
sm2:xxxx
sm4:xxxx
```

## Required Keys by Algorithm

### AES

- `GEELATO_AES_KEY`

### SM4

- `GEELATO_SM4_KEY`

### RSA

- `GEELATO_RSA_PUBLIC_KEY`
- `GEELATO_RSA_PRIVATE_KEY`

RSA encryption uses the public key and decryption uses the private key.

### SM2

- `GEELATO_SM2_PUBLIC_KEY`
- `GEELATO_SM2_PRIVATE_KEY`

The current implementation requires both keys for SM2 operations.

## Environment Variable Examples

Security level and anonymous channel example (production with heightened confidentiality):

```powershell
$env:GEELATO_SECURITY_LEVEL="3"
$env:GEELATO_ANONYMOUS_ENABLED="false"
```

Classified deployment closing the anonymous channel:

```powershell
$env:GEELATO_SECURITY_LEVEL="10"
```

PowerShell example for RSA:

```powershell
$env:GEELATO_ENCRYPT_TYPE="rsa"
$env:GEELATO_RSA_PUBLIC_KEY="your-public-key"
$env:GEELATO_RSA_PRIVATE_KEY="your-private-key"
```

AES example:

```powershell
$env:GEELATO_ENCRYPT_TYPE="aes"
$env:GEELATO_AES_KEY="your-16-char-key"
```

## How Column-Level Encryption Works

### Step 1: Global Switch

Column encryption is first guarded by:

- `GlobalContext.getColumnEncryptOption()`

which is derived from the security level (on when level ≥ 1).

Under the default level `2`, it is enabled.

### Step 2: Column Metadata

A field is not encrypted only because the global switch is on.

The real per-field condition is:

- `ColumnMeta.encrypted = true`

Only fields marked as encrypted in metadata enter the encryption path on save.

### Step 3: Save-Time Encryption

In `JsonTextSaveParser`, the save flow:

- checks `GlobalContext.getColumnEncryptOption()`
- iterates fields
- calls `EncryptUtils.encrypt(...)` only for fields whose `ColumnMeta.isEncrypted()` is `true`

### Step 4: Read-Time Decryption

At read time, `CommonRowMapper` calls:

- `EncryptUtils.decrypt(...)`

for string values.

If the value matches the `algorithm:ciphertext` pattern, it is decrypted by the prefixed algorithm. Otherwise it is returned as-is.

So:

- encrypted values can be automatically decrypted
- plain string values are not broken

## Recommended Practice

- set `GEELATO_SECURITY_LEVEL` according to the deployment scenario (the default 2 suits general commercial production)
- explicitly close the anonymous channel in production (`GEELATO_ANONYMOUS_ENABLED="false"`)
- inject real keys through environment variables in production
- define `GEELATO_ENCRYPT_TYPE` explicitly
- mark only truly sensitive columns as `encrypted=true`
- do not confuse "global column encryption enabled" with "all columns are encrypted"

## Current Boundary

Although `GlobalContext` exposes concepts such as environment, security level, and API encryption, their externalization levels differ:

- algorithm and keys are externalized through environment variables
- security level and the anonymous switch are externalized (`GEELATO_SECURITY_LEVEL` / `GEELATO_ANONYMOUS_ENABLED`)
- the environment flag is still a static constant
- column-encryption and API-encryption switches are derived from the security level and cannot be set independently

## Suggested Reading

- [Authentication and Authorization](../authentication/security-authentication.md)
- [Traffic Tagging](traffic-tagging.md)
- [System Configuration](../system-config/overview.md)
