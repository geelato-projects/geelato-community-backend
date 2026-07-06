# Website

This site is built with [Docusaurus](https://docusaurus.io/) and publishes to GitHub Pages for `https://docs.geelato.cn`.

## Install

```bash
npm ci
```

## Local Development

```bash
npm start
```

## Build

```bash
npm run build
```

The generated static files are written to `build/`.

## GitHub Actions Deployment

The repository root contains `.github/workflows/deploy-geelato-docs.yml`.

It builds `website` and publishes `build/` to:

- repository: `geelato-projects/geelato-community-backend`
- branch: `gh-pages`
- custom domain: `docs.geelato.cn`

Current content layout:

- site source: `website`
- docs content: `website/official-docs`
- static assets: `website/static`
- optional API source references: `SrvExplain`

The workflow listens to these paths:

- `.github/workflows/deploy-geelato-docs.yml`
- `website/**`
- `SrvExplain/**`

Required secret in the source repository:

- `GEELATO_DOCS_DEPLOY_TOKEN`
  - use a GitHub Personal Access Token
  - grant write access to `geelato-projects/geelato-community-backend`

Recommended GitHub Pages settings in `geelato-community-backend`:

- source: `Deploy from a branch`
- branch: `gh-pages`
- folder: `/ (root)`
- custom domain: `docs.geelato.cn`

## Domain Configuration Manual

### 1. DNS

Add a DNS record for the subdomain:

- type: `CNAME`
- host: `docs`
- value: `geelato-projects.github.io`

Do not point `docs.geelato.cn` to a fixed IP unless your DNS provider does not support CNAME for subdomains.

### 2. GitHub Pages

Open the target repository `geelato-projects/geelato-community-backend`:

- `Settings` -> `Pages`
- source: `Deploy from a branch`
- branch: `gh-pages`
- folder: `/ (root)`
- custom domain: `docs.geelato.cn`
- save and wait for GitHub to verify the domain

### 3. HTTPS

After DNS takes effect and the custom domain is verified:

- enable `Enforce HTTPS`
- keep the generated `CNAME` file in the published site root

### 4. Verification

Recommended checks after the first deployment:

- `nslookup docs.geelato.cn`
- open `https://docs.geelato.cn`
- confirm the certificate is valid
- confirm `/en/intro` and `/zh-cn/intro` can be accessed

## Manual Fallback

If you need to publish without GitHub Actions:

```bash
GIT_USER=<github-user> npm run deploy
```

This uses the Docusaurus deploy flow and targets the configured repository metadata in `docusaurus.config.ts`.
