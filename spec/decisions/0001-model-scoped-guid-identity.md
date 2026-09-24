---
id: decision.0001
spec_version: "0.1.0"
status: proposed
affects:
  - capability.stable-identity
---

# Use model-scoped IFC GlobalIds for stable identity

## Context

An IFC GlobalId is designed to identify an IFC-rooted object, but independently
authored models can reuse the same value. File paths are poor revision-family
identifiers because files are routinely renamed, copied, and relocated.

## Decision

Stable element identity combines a caller-controlled model scope with the IFC
GlobalId under a caller-controlled base IRI. The policy is versioned as
`stable-guid-v1`, and its exact IRI construction is a compatibility contract.

## Consequences

- Revisions remain linkable when the file moves or mapped properties change.
- Callers must persist and correctly reuse a model scope.
- Different scopes isolate GlobalId collisions between models.
- Changing the base IRI, scope, or policy version is an identity migration.

## Alternatives not selected

- File path or file hash: changes across ordinary revision workflows.
- GlobalId alone: provides no namespace for collisions between models.
- Product type plus GlobalId: identity changes if classification or mapping
  changes.
- Random output UUID: cannot be reconstructed independently across revisions.
