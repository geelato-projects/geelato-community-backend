# OSS Module

File:

- `properties/oss.properties`

## Purpose

This file holds Aliyun OSS access settings.

## Key Properties

- `geelato.oss.accessKeyId`
- `geelato.oss.accessKeySecret`
- `geelato.oss.endPoint`
- `geelato.oss.bucketName`
- `geelato.oss.region`

## Notes

- inject secrets through environment variables
- keep bucket and region aligned with the actual OSS resources
- make sure upload capability has a fallback path if OSS is not configured
