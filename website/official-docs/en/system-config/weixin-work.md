# Weixin Work Module

File:

- `properties/weixin_work.properties`

## Purpose

This file contains server-side signature validation settings for Weixin Work.

## Key Properties

- `wework.validate.token`
- `wework.validate.aeskey`
- `wework.validate.corpid`

## Notes

- keep these values aligned with the Weixin Work admin console
- inject them through environment variables in production
- do not confuse them with the generic WeChat settings in `auth.properties`
