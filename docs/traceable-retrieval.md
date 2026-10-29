# Traceable graph retrieval

[Documentation home](README.md) · [Optional identity and links](optional-integration.md)

IFCtoLBD provides a shared, deterministic retrieval service for converted LBD
graphs. It is read-only, does not accept arbitrary SPARQL, interpret natural
language, or fetch external resources. Existing conversion defaults and RDF
output are unchanged.

## Retrieval contract

`GraphRetrievalService` finds elements by RDF IRI or IFC GlobalId, type, and
spatial location; inspects an element's properties, materials, relationships,
linked records, supporting triples, and available provenance; and lists linked
documents, observations, datasets, and maintenance records.

Results contain a status, query kind, entities, supporting statements, available
provenance, offset, limit, total count, and truncation flag. Statuses distinguish
`FOUND`, successful `EMPTY`, `NOT_FOUND`, `AMBIGUOUS`, `UNSUPPORTED`, and
`ERROR`. Limits are 1–1000 and offsets are capped at 100,000. A returned unit is
evidence from the graph, not an inferred unit.

## CLI

Focused retrieval can follow an ordinary conversion without changing its output:

```sh
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar \
  org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI \
  --profile evidence \
  --inspect-element '2O2Fr$t4X7Zf8NOew3FLOH' \
  --retrieval-limit 200 \
  --retrieval-output wall.json \
  --provenance-output building.provenance.ttl \
  --target_file building.ttl model.ifc
```

Find criteria use `--find-element`, `--find-type`, and `--find-location`.
`--linked-records` retrieves only separate link records. Use one focused task per
invocation. For existing RDF, `GraphRetrieval_CLI` accepts `--data`, optional
`--external-links`, `--external-link-report`, and `--provenance`, and a `find`, `inspect`, or `links` task.

## Optional provenance and validation

The `evidence` profile adds element-level traceability to the companion manifest
graph: a path-free source fingerprint, source IFC GlobalId and type, conversion
version/options, `prov:wasDerivedFrom`, scope `element`, and mapping kind
`DIRECT`. This does not claim statement-level provenance. Mapping kinds keep
deterministic derivations, external proposals, and AI annotations distinct.
Ordinary profiles do not add this evidence.

`GraphValidationService` returns a standard SHACL RDF report, finding counts,
and a concise summary. The optional `traceability` pack checks manifests,
evidence scope/kind, and external mapping reports. SHACL conformance checks graph
structure; it does not establish factual correctness of building information.

## API and MCP

The Node.js API exposes `POST /api/v1/retrieve`. Upload `dataFile` and optionally
`externalLinksFile` and `provenanceFile`, then choose `task=find|inspect|links`.
Uploads are temporary and bounded.

MCP clients use `find_elements`, `inspect_element`, and `get_linked_records` on a
model returned by `load_ifc`. `validate_model` accepts the `traceability` pack
and validates data with its retained provenance and external-link report.

## Agent workflow

1. Call `load_ifc`; use `profile: "evidence"` only when traceability is needed.
2. Use `find_elements`, then `inspect_element` for the selected identifier.
3. Call `get_linked_records`; do not fetch targets without separate authority.
4. Call `validate_model`, and `explain_validation` for finding details.
5. Explain with `supportingStatements` and `provenance`; disclose element-only
   scope, truncation, missing information, ambiguity, or unavailability.
6. Call `close_model` when finished.

Natural-language interpretation remains an optional consumer outside the shared
service. The [no-LLM evaluation](../examples/graph-assisted-retrieval/README.md)
compares this workflow with document-only retrieval without assuming a winner.
