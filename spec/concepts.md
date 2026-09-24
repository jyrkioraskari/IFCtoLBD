---
id: concepts.core
spec_version: "0.1.0"
status: proposed
---

# Core concepts

## Model revision

A model revision is one conversion input representing the state of a building
model at a particular time. A file name or location is transport information,
not persistent model identity.

## Model scope

A model scope is caller-supplied text that identifies the revision family to
which a model belongs. All revisions of the same model use the same scope;
unrelated models use different scopes. It is not inferred from the input path.

## IFC GlobalId

An IFC GlobalId is the 22-character compressed representation attached to an
IFC root object. Stable identity 0.1.0 only covers GlobalIds that can be decoded
to a 128-bit UUID.

## Element identity

Element identity is the absolute IRI used for the same IFC-rooted entity
wherever it occurs in output RDF. Identity is distinct from a label, IFC Tag,
source-file location, RDF serialization order, and Java object identity.

## Base IRI

The base IRI is an absolute caller-controlled namespace. It remains part of a
stable element IRI: changing it creates a different RDF identity.

## Graph equivalence

Two RDF graphs are graph-equivalent when they contain the same RDF statements,
allowing differences in statement order, prefix choice, and blank-node labels.
Exact element IRIs and vocabulary IRIs are not normalized away.
