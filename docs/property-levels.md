# Property levels 1–3

[Documentation home](README.md) · [Glossary](glossary.md)

Property levels control how much structure surrounds an IFC property value.
Level 1 is easiest to query. Level 2 gives the property its own identity. Level
3 adds a state, which is useful when values can change over time.

The examples below follow the same `Pset_WallCommon.LoadBearing = true` property
at every level. The identifiers are shortened for readability; the converter
generates full IRIs or blank nodes according to its settings.

```turtle
@prefix inst:   <https://example.org/building/> .
@prefix props:  <https://w3id.org/props#> .
@prefix opm:    <https://w3id.org/opm#> .
@prefix schema: <http://schema.org/> .
@prefix prov:   <http://www.w3.org/ns/prov#> .
@prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#> .
```

## Level 1: value on the wall

```turtle
inst:wall-1 props:loadBearing_property_simple true .
```

Choose level 1 when consumers mainly need the current value and simple queries.
There is no separate resource on which to attach metadata.

## Level 2: property as a resource

```turtle
inst:wall-1 props:loadBearing inst:wall-1-load-bearing .

inst:wall-1-load-bearing
    a opm:Property ;
    rdfs:label "Pset_WallCommon:LoadBearing" ;
    schema:value true .
```

Choose level 2 when the property itself needs an identifier, a label, a unit,
or a link to a dictionary entry.

## Level 3: value in a property state

```turtle
inst:wall-1 props:loadBearing inst:wall-1-load-bearing .

inst:wall-1-load-bearing
    a opm:Property ;
    rdfs:label "Pset_WallCommon:LoadBearing" ;
    opm:hasPropertyState inst:wall-1-load-bearing-state .

inst:wall-1-load-bearing-state
    a opm:CurrentPropertyState ;
    schema:value true ;
    prov:generatedAtTime "2026-10-08T10:00:00" .
```

Choose level 3 when you need to distinguish a property from its current value
or plan to connect states to provenance and revision information. It creates
more triples and makes basic queries longer.

## Choosing a level

| Need | Level |
| --- | --- |
| Compact output and direct value queries | 1 |
| Metadata or dictionary links on each property | 2 |
| States, provenance, or change tracking | 3 |

Set the CLI level with `--level=1`, `--level=2`, or `--level=3`. The desktop
offers the same choice in its property settings. The W3C LBD community’s
[property modelling presentation](https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf)
remains useful background, but the examples above are the maintained description
of current output.
