---
id: conversion.process
spec_version: "0.1.0"
status: proposed
depends_on:
  - concepts.core
---

# Abstract conversion process

The converter accepts an IFC model, a base IRI, and conversion options. It emits
one or more RDF graphs plus conversion metadata. Internal staging graphs and
library-specific model objects are not part of the contract.

```text
validate_request(input, options)
parse_input_into_ifc_entities(input)

for each entity selected for output:
    identity := identity_policy(base_iri, model_scope, entity.global_id)
    map entity topology and data using identity

emit data graphs
emit conversion metadata
```

The stable-identity policy is defined separately because callers may select a
different policy. Conversion order must not affect the resulting element IRI.
