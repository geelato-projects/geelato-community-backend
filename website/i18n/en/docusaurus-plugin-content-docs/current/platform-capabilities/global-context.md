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

The code currently defines:

- `__SecurityLevel__ = 2`

and derives:

- `__ColumnEncrypt__ = __SecurityLevel__ > 0`
- `__ApiEncrypt__ = __SecurityLevel__ > 1`

So under the current default:

- column encryption is enabled
- API encryption semantics are enabled

## What Is Already Environment-Variable Driven

The following items are explicitly supported through environment variables:

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
- `__SecurityLevel__`
- `__ColumnEncrypt__`
- `__ApiEncrypt__`

So the accurate statement for the current repository state is:

- encryption algorithm and keys already support environment variables
- environment and security level do not yet switch directly through environment variables

If you want environment and security level to be externalized too, `GlobalContext` needs to be extended further.

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

which is currently derived from:

- `__SecurityLevel__ > 0`

Under the current default code, it is enabled.

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

- inject real keys through environment variables in production
- define `GEELATO_ENCRYPT_TYPE` explicitly
- mark only truly sensitive columns as `encrypted=true`
- do not confuse "global column encryption enabled" with "all columns are encrypted"

## Current Boundary

Although `GlobalContext` exposes concepts such as environment, security level, and API encryption, they are still compile-time constants in the current codebase.

So the most accurate summary is:

- algorithm and keys are already externalized
- environment and security level are still static
- full externalization requires further extension of `GlobalContext`

## Suggested Reading

- [Authentication and Authorization](../authentication/security-authentication.md)
- [Traffic Tagging](traffic-tagging.md)
- [System Configuration](../system-config/overview.md)
