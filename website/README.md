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

It builds `geelato-community/website` and publishes `build/` to:

- repository: `geelato-projects/geelato-community-backend`
- branch: `gh-pages`
- custom domain: `docs.geelato.cn`

Required secret in the source repository:

- `GEELATO_DOCS_DEPLOY_TOKEN`
  - use a GitHub Personal Access Token
  - grant write access to `geelato-projects/geelato-community-backend`

Recommended GitHub Pages settings in `geelato-community-backend`:

- source: `Deploy from a branch`
- branch: `gh-pages`
- folder: `/ (root)`
- custom domain: `docs.geelato.cn`

## Manual Fallback

If you need to publish without GitHub Actions:

```bash
GIT_USER=<github-user> npm run deploy
```

This uses the Docusaurus deploy flow and targets the configured repository metadata in `docusaurus.config.ts`.
