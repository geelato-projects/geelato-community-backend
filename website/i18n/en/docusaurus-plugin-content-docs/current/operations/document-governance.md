# Documentation Governance

This first release establishes the rules for maintaining the official framework documentation.

## Source of Truth

- Repository markdown files are the source of truth
- The Docusaurus website is the publishing surface
- `docs/` remains for engineering topic notes
- `SrvExplain/` remains for generated API supplements

## Required Governance Rules

- update official docs together with framework delivery changes
- keep bilingual structure aligned
- keep API documentation aligned with both OpenAPI and `SrvExplain`
- document module boundary changes when runtime/designer responsibilities move

## First Governance Checks

- site builds successfully
- links remain valid
- core onboarding path remains discoverable in both languages
- API dual-track entry remains visible from the official site
