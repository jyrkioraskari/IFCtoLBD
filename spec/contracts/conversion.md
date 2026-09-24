---
id: contracts.conversion
spec_version: "0.1.0"
status: proposed
depends_on:
  - concepts.core
---

# Conversion contract

## Inputs relevant to stable identity

- A readable IFC input in a supported representation.
- An absolute base IRI.
- An identity-policy selection.
- A non-blank model scope when `stable-guid-v1` is selected.

The input file location is not an identity input.

## Outputs relevant to stable identity

- RDF resources consistently identified according to the selected policy.
- Conversion metadata naming the policy and its configuration.

RDF serialization order is not significant. Element IRIs are significant
exactly as strings.

## Failure boundary

A request that selects stable identity without a model scope fails before it can
claim a conforming stable-identity result. Version 0.1.0 does not yet standardize
an error serialization or process exit code.

## Unspecified contracts

Ownership and lifetime of in-memory results, the complete input-format matrix,
and the partitioning of output into named graphs are intentionally unresolved in
this version.
