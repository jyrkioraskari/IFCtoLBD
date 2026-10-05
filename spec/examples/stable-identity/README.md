---
id: example.stable-identity.basic
spec_version: "0.1.0"
status: proposed
verifies:
  - SID-001
  - SID-002
  - SID-003
  - SID-004
  - SID-005
  - SID-006
  - SID-007
  - SID-008
  - SID-009
  - SID-010
  - SID-011
  - SID-012
---

# Executable stable-identity example

[`wall.ifcjson`](wall.ifcjson) is a minimal language-neutral IFC input.
Convert it with:

- base IRI `https://example.com/#`
- policy `stable-guid-v1`
- model scope `model a`

Run [`required.ask.rq`](required.ask.rq) against the union of the emitted data
graphs; it must return `true`. Run [`forbidden.ask.rq`](forbidden.ask.rq); it must
return `false`. SPARQL result formatting is irrelevant.

Copying or renaming the input and repeating the conversion with the same inputs
must satisfy the same assertions. Repeating it with scope `model-b` must not emit
the element IRI for `model a`. Changing only the wall name must retain the IRI,
while changing the base IRI must change it. A missing or blank model scope must
be rejected, and the conversion manifest must record the selected policy, scope,
and configuration identifier.

The repository's Java implementation executes this example in
`SoftwareIntentStableIdentityTest`. Other implementations can use any SPARQL
1.1 engine and do not need Java or Maven.

From the repository root, run the current implementation check with:

```sh
./mvnw -pl IFCtoLBD -am \
  -Pintegration \
  -Dtest=SoftwareIntentStableIdentityTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

The YAML front matter identifies the rules exercised by the example. This test
does not resolve the missing or invalid GlobalId questions.
