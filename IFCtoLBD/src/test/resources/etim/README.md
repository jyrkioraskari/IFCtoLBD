# ETIM test snapshot

`etim-examples.json` is a small, offline, reduced test snapshot for the two ETIM classes exercised
by the converter. The class codes, names and selected feature codes/names are based on the ETIM
International class viewer:

- https://viewer.etim-international.com/class/EC000005
- https://viewer.etim-international.com/class/EC003535?lang=en-GB

The example is intentionally incomplete; it is not a replacement for a bSDD dictionary export.
The ETIM 9.0 class, reusable-property and class-property identifiers in the snapshot were verified
against the buildingSMART bSDD API on 2026-10-15. Only the minimum identifiers and selected factual
names needed by the tests are retained because the API reports the ETIM dictionary licence as
rights-reserved. Runtime identifiers are taken verbatim from bSDD responses by
`BsddClassificationResolver`; no production URI is derived from a label.
The offline rights tests also preserve bSDD's dictionary owner, status, licence text and
licence-information URL verbatim and verify their RDF provenance output.
