# Third-party source provenance

This file records source material copied, extracted, or transformed into files
distributed by this repository. Dependency licence reports remain in the
module-specific `THIRD-PARTY` files.

## Digital Construction BuildingMaterials 0.1

- Upstream project: Digital Construction BuildingMaterials,
  <https://github.com/digitalconstruction/BuildingMaterials>
- Upstream revision: `b30a0a29e04c4f5d4a63e26a50987afce7d8492f`
  ("Snapshot 0.1", 2020-02-26)
- Source file: `v/0.1/bm.ttl`, Git blob
  `2099a67bd2025b86b7ca4fffacd79b58780b90f0`
- Source SHA-256:
  `b8ed0e82828ccc2700cc1c30252e0c1383bb2149821e63b43ed85ad925da7ca9`
- Destination: `IFCtoLBD/src/main/resources/ontology/bmo-0.1.ttl`
- Use: bundled unchanged for offline vocabulary verification; it is not loaded
  into conversion output and no ontology reasoning is enabled
- Authors: Janakiram Karlapudi and Prathap Valluru
- Licence and attribution: Creative Commons Attribution 4.0 International,
  as declared in the ontology; see
  <https://creativecommons.org/licenses/by/4.0/>. The source ontology retains
  its creator, licence, title, version, and namespace metadata. A distribution
  notice is included at `IFCtoLBD/src/main/resources/META-INF/NOTICE-BMO.md`.

## Building Topology Ontology 0.3.2

- Upstream project: W3C Linked Building Data Community Group, Building
  Topology Ontology (BOT), <https://github.com/w3c-lbd-cg/bot>
- Upstream version: 0.3.2, version IRI <https://w3id.org/bot-0.3.2>
- Source file: `bot.ttl`
- Destination: `IFCtoLBD/src/main/resources/bot.ttl`
- Use: bundled unchanged as an ontology resource for ICDD exports
- Licence and attribution: Creative Commons Attribution 1.0; the destination
  file retains the upstream copyright, licence declaration, creators, and
  contributors

## buildingSMART IFC4X3_ADD2 property and quantity definitions

- Upstream publisher: buildingSMART International
- Upstream publication: IFC 4.3.2.0 (IFC4X3_ADD2), official property-set
  definition archive,
  <https://standards.buildingsmart.org/IFC/DEV/IFC4_3/HTML/annex-a-psd.zip>
- Source archive SHA-256:
  `40d941301828112b4cab778f22a87c0b25bd7a260b36af306159a534299c1ee7`
- Source material: 645 property-set and quantity-set XML definitions from the
  760-file archive
- Destination: `IFCtoLBD/src/main/resources/ifc-bsdd/ifc-4.3-add2.tsv.gz`
- Transformation: extracted set names, property names, and their canonical
  buildingSMART identifier URIs into a compact, deterministic TSV index; no
  descriptions or prose definitions are included
- Use: offline resolution of official IFC property and quantity identifiers
- Licence and attribution: the formal IFC4.3 release is published under
  Creative Commons Attribution-NoDerivatives 4.0 International. The upstream
  copyright notice explicitly permits use in software development and
  translation into another computer language when full attribution is given.
  Copyright 1996-2026 buildingSMART International Limited. IFC and Industry
  Foundation Class are buildingSMART trademarks. See
  <https://github.com/buildingSMART/IFC4.x-development/blob/ifc4.3-main/LICENSE.md>
  and <https://creativecommons.org/licenses/by-nd/4.0/>.

The extraction format and checksum are also documented in
`IFCtoLBD/src/main/resources/ifc-bsdd/README.md`.

## ETIM example class and feature identifiers

- Upstream publisher: ETIM International
- Upstream pages: ETIM class viewer entries for
  [EC000005](https://viewer.etim-international.com/class/EC000005) and
  [EC003535](https://viewer.etim-international.com/class/EC003535?lang=en-GB),
  plus the buildingSMART bSDD v1 API, accessed 2026-10-15
- Source material: class codes/names, a small selection of published feature
  codes/names, and registry-supplied dictionary ownership, status, licence text,
  and licence-information URL used by deterministic offline tests
- Destination: `IFCtoLBD/src/test/resources/etim/etim-examples.json`
- Transformation: reduced JSON test snapshot containing registry-returned ETIM
  9.0 class, Property and ClassProperty identifiers; descriptions were shortened
- Use: verifies property meaning, unit compatibility, ambiguous-class
  abstention, separation of door-leaf, frame, and module widths, and faithful
  propagation of dictionary rights into generated RDF metadata
- Licence and attribution: bSDD reports the ETIM dictionary as "No license
  (rights reserved)". Only minimal factual identifiers and names required for
  interoperability tests are retained with publisher and source links in
  `IFCtoLBD/src/test/resources/etim/README.md`; this snapshot is not a
  redistribution of an ETIM dictionary or its full definitions
