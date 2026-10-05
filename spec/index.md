---
id: spec.index
spec_version: "0.1.0"
status: proposed
---

# IFCtoLBD Software Intent Specification

This specification describes the observable behaviour that an IFC-to-LBD
implementation must provide. It deliberately avoids prescribing Java classes,
Maven modules, storage libraries, or internal algorithms unless they are part of
an externally visible compatibility contract.

Version 0.1.0 is the first, deliberately narrow slice. It specifies stable
element identity and establishes the format to use for later capabilities.

## Normative language

The words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** are
normative. Numbered rules are the units of conformance. Prose, procedures, and
examples explain those rules but do not silently add requirements.

Every document has YAML front matter containing a stable identifier, the
specification version, and its review status:

- `proposed`: precise enough to review and test, but not yet accepted as a
  compatibility commitment.
- `accepted`: approved as intended behaviour.
- `unresolved`: records a question for which implementations must not be used
  as the sole source of intent.

Implementation evidence is kept in [evidence.yaml](evidence.yaml). Evidence can
be `verified`, `partial`, `conflicting`, or `unchecked`; evidence status never
changes a proposed rule into an accepted one.

## Reading order

1. [Concepts](concepts.md)
2. [Conversion model](conversion.md)
3. [Conversion contract](contracts/conversion.md)
4. [Stable element identity](capabilities/stable-identity.md)
5. [Executable stable-identity example](examples/stable-identity/README.md)
6. [Decision records](decisions/0001-model-scoped-guid-identity.md)
7. [Implementation evidence](evidence.yaml)

## Scope

The current release specifies identity only for IFC resources with a valid IFC
GlobalId under the `stable-guid-v1` policy. Topology, properties, quantities,
units, geometry, validation, error taxonomy, and lifecycle rules remain future
work and are not implied by this document.

Conformance to 0.1.0 means satisfying all accepted rules in the selected
capability and passing its executable examples. Because every rule in this
initial draft is proposed, passing the examples currently demonstrates candidate
compatibility rather than certification.
