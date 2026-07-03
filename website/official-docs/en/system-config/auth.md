# Auth Module

File:

- `properties/auth.properties`

## Purpose

This file contains:

- OAuth2 client settings
- WeChat-related application settings
- `sa-token` basics

## Key Areas

- `geelato.oauth2.*`
- `geelato.wx.*`
- `sa-token.*`

## Notes

- keep OAuth2 client secrets and WeChat secrets in environment variables
- align the OAuth2 URL with the deployed unified authentication center
- combine this page with [Authentication and Authorization](../authentication/security-authentication.md) to understand how runtime backend auth consumes these credentials
