---
id: capability.stable-identity
spec_version: "0.1.0"
status: proposed
policy_id: stable-guid-v1
depends_on:
  - concepts.core
  - contracts.conversion
examples:
  - ../examples/stable-identity/README.md
---

# Stable element identity

## Purpose

Allow datasets to retain links to an IFC element across revisions of one model,
provided its IFC GlobalId, model scope, base IRI, and policy version remain
unchanged.

## Scope

These rules apply when `stable-guid-v1` is selected and the mapped IFC resource
has a valid IFC GlobalId. Behaviour for absent or invalid GlobalIds is recorded
as an open question rather than inferred from a fallback implementation.

## Inputs

- Absolute base IRI.
- Non-blank persistent model scope.
- Valid 22-character compressed IFC GlobalId.
- Policy identifier `stable-guid-v1`.

Product type, source path, source resource IRI, properties, and RDF
serialization are not identity inputs.

## Outputs

An absolute element IRI and conversion metadata identifying the policy and model
scope.

## Rules

- **SID-001:** Stable identity mode MUST reject a request whose model scope is
  absent, empty, or consists only of whitespace.
- **SID-002:** Given unchanged base IRI, model scope, valid GlobalId, and policy
  version, the element IRI MUST be identical across conversions.
- **SID-003:** Changing only the input file name or location MUST NOT change the
  element IRI.
- **SID-004:** Changing element properties or mapped product type MUST NOT change
  the element IRI.
- **SID-005:** Distinct model scopes MUST produce distinct element IRIs for the
  same GlobalId.
- **SID-006:** Changing the base IRI MUST produce a different element IRI.
- **SID-007:** The element IRI MUST use this exact construction:
  `{normalized-base}/model/{encoded-scope}/element/{uuid}`.
- **SID-008:** `normalized-base` MUST be the base IRI with every trailing `/` or
  `#` removed.
- **SID-009:** `encoded-scope` MUST be the UTF-8
  `application/x-www-form-urlencoded` encoding of the model scope with `+`
  replaced by `%20` so spaces are percent encoded.
- **SID-010:** `uuid` MUST be the lowercase, hyphenated 128-bit UUID decoded
  from the IFC compressed GlobalId.
- **SID-011:** Every output reference to the mapped element MUST reuse the same
  element IRI.
- **SID-012:** Conversion metadata MUST report policy `stable-guid-v1`, the
  model scope verbatim, and configuration identifier
  `stable-guid-v1@{model-scope}`.

## Procedure

```text
require non_blank(model_scope)
require valid_ifc_global_id(global_id)
normalized_base := remove_trailing(base_iri, '/', '#')
encoded_scope := form_urlencode_utf8(model_scope).replace('+', '%20')
uuid := decode_ifc_global_id_as_lowercase_hyphenated_uuid(global_id)
return normalized_base + '/model/' + encoded_scope + '/element/' + uuid
```

## Acceptance examples

For base `https://example.com/#`, scope `model a`, and GlobalId
`2O2Fr$t4X7Zf8NOew3FNr2`, the exact IRI is:

```text
https://example.com/model/model%20a/element/9808fd7f-dc48-478e-9217-628e833d7d42
```

| Change | Expected identity |
| --- | --- |
| File renamed; other identity inputs unchanged | Same |
| Property or product mapping changed | Same |
| Model scope changed | Different |
| Base IRI changed | Different |

The fixture and machine-executable assertions are under
[`examples/stable-identity`](../examples/stable-identity/README.md).

## Open questions

- **SID-Q001:** Must a missing GlobalId fail conversion, omit the entity, or use
  an explicitly unstable fallback identity?
- **SID-Q002:** What exact failure is required for a malformed compressed
  GlobalId?
- **SID-Q003:** Should a future policy encode arbitrary Unicode scope text with
  RFC 3986 path-segment encoding instead of preserving the v1 form-encoding
  algorithm?
