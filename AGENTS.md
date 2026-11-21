# AGENTS.md

## Project overview

IFCtoLBD converts IFC building models into Linked Building Data (RDF). The main implementation is a Java 21 multi-module Maven project with Python, Node.js, OpenAPI, Scala, desktop, and MCP integrations. Read `README.md` for the user-facing overview and `docs/development.md` before architectural or build changes.

For every change, identify the user, their entry point, and an observable success condition. Trace existing callers, fixtures, and tests. Make the smallest complete change that preserves existing contracts. Apply the specialized guidance below when the task touches that area.

## Working-tree safety

Run `git status --short` before editing. Preserve user changes, including pre-existing generated modifications. Do not reset, revert, delete, or reformat unrelated files. Limit edits to the task and report any pre-existing changes that prevent safe verification.

## Repository layout

Root Maven reactor modules in dependency order:

1. `IFCtoRDF` — IFC parsing and IFC-oriented RDF generation.
2. `IFCtoLBD\_Geometry` — geometry extraction and IfcOpenShell integration.
3. `IFCtoLBD` — core conversion APIs, RDF mappings, validation, profiles, and CLI.
4. `IFCProps2ExcelOnline` — Vaadin property export application.
5. `IFCtoLBD\_Desktop\_2026` — JavaFX desktop application.

Build and check these projects separately:

* `IFCtoLBD\_MCP` — Node.js MCP server using the shaded converter JAR.
* `IFCtoLBD\_NodeJS` — Node.js integration examples.
* `IFCtoLBD\_Python` — JPype wrapper and Python examples.
* `IFCtoLBD\_OpenAPI\_Node\_js` — modern Node.js HTTP/OpenAPI service using the shaded JAR.
* `IFCtoLBD\_OpenAPI` — separate Maven web API.
* `ifc-to-lbd-scala` — Scala example.
* `maven\_demo` — historical consumer example.

## Build requirements

Use JDK 21 or newer and prefer the checked-in Maven wrapper. Run commands from the repository root unless explicitly targeting a separate project. The first Maven build may need network access to download dependencies.

```sh
./mvnw install
./mvnw verify
./mvnw -pl IFCtoLBD -am test
./mvnw -pl IFCtoLBD -am package -DskipTests
```

Prefer `-pl <module> -am` for focused work so Maven also builds required reactor dependencies.

## Implementation and compatibility

Start core converter work in `IFCtoLBD/src/main/java/org/linkedbuildingdata/ifc2lbd`.

|Entry point|Responsibility|
|-|-|
|`IFCtoLBDConverter\_CLI`|Command-line interface|
|`IFCtoLBDConverter`|Conversion orchestration|
|`core/IFCtoLBDConverterCore`|Topology, element, attribute, and property mapping|
|`core/utils/IfcOWLUtils`|IFC relationship traversal|
|`ConversionRequest`|Conversion options and profiles|
|`ConversionResult`|Generated graphs and validation results|
|`ConversionSession`|Temporary storage and conversion resource ownership|

* Preserve module boundaries. Keep parsing, geometry, desktop, and integration concerns in their existing modules unless the change requires otherwise. Keep independently testable domain decisions in one place. Before adding an abstraction, identify the decision it hides and the callers it protects.
* Close owned resources, including `ConversionResult`, `IFCtoLBDConverter`, and `ConversionSession`, where applicable. Copy or serialize data that must outlive its owning session.
* Follow adjacent code. The repository has no universal automatic formatter. Keep unrelated formatting and mechanical rewrites out of the diff.
* Treat Java and wrapper APIs, CLI options, defaults, profiles, identifiers, and saved formats as contracts. Check affected callers and older inputs. Document migration and recovery for intentional breaking changes.
* Verify unfamiliar APIs against the installed version. Prefer existing capabilities. For a new algorithm or dependency, identify the limitation it solves and check maintenance, licence, compatibility, and migration cost. For performance claims, compare against a correct baseline on representative models and include parsing, setup, I/O, and peak memory where relevant. Record workload, versions, and variability.

## RDF and IFC changes

* Keep vocabulary IRIs and identifier generation stable unless a compatibility change is explicitly requested. Preserve model scope for stable identifiers across revisions.
* Consider schema variants, missing optional relationships, and spatial objects without mesh geometry. Geometry is optional. Unrelated tests must run without a local IfcOpenShell installation. Geometry work currently targets IfcOpenShell 0.8.5 and must account for the legacy fallback backend.
* Preserve source values, datatypes, units, and provenance where the mapping promises them. Distinguish supplied facts, derived values, generated suggestions, and reviewed decisions. Represent missing or ambiguous evidence explicitly.
* Keep Turtle and other RDF fixtures deterministic and readable. Document new user-visible vocabulary terms or profiles and include an example query.

## User interfaces and integration handoffs

* Use domain language and familiar controls. Keep the common conversion path discoverable with sensible defaults. Add an option only for a meaningful user choice. Group advanced settings with the task they affect. Show progress, completion, partial results, and recoverable errors. Preserve valid input and make consequential actions visible and reversible where practical.
* Choose the display by the question. Use a table for identifiers and exact values, aligned bars or dots for magnitude comparisons, and a spatial view for geometry or location. Label units and missing values. Use grouping, alignment, whitespace, and consistent scales. Check long labels and realistic model sizes.
* Keep choices and evidence together. For AI-assisted classification, let users inspect source evidence, correct the suggestion, or leave the item unresolved. When evaluating consequential suggestions, include plausible wrong answers and measure incorrect acceptances, correct acceptances, and review effort.
* For web interfaces, target WCAG 2.2 AA by default. Use semantic controls, keyboard access, visible focus, readable contrast, and cues beyond color. Support zoom, reduced motion, and alternatives to precise dragging. Provide text or table access to chart data and accessible lookup in a 3D view when users need to inspect objects. Check localized labels, text direction, and units where relevant.
* For consequential interface changes, observe a representative task with people who encounter the relevant barriers when feasible. Record where assistance was needed. Distinguish technical checks from observed usability.
* Verify handoffs through the affected consumer. For example, convert a small IFC file using the changed CLI or wrapper, open the RDF in the intended consumer, and query the expected element. Preserve selection, identifiers, units, and context across the handoff where applicable.

## Security and failure handling

Validate inputs at trust boundaries and enforce permissions where actions execute. Keep credentials and sensitive model content out of source, fixtures, and logs. External model requests require authorization for the data transferred. Bound external waits, retry only repeatable operations, and account for interruption or concurrent writes where applicable. Report what completed and how to recover. For consequential service rollouts, identify a failure signal and recovery path.

## Attribution and source provenance

When copying, adapting, translating, or extracting code:

* Read applicable LICENSE, NOTICE, and source-file headers. Preserve relevant copyright, licence, and attribution notices. Refactoring, renaming, translation, or AI generation does not remove upstream obligations.
* Record the upstream project URL, revision if known, source files, and destination files in `THIRD\_PARTY\_PROVENANCE.md`. Distinguish upstream authorship from your modifications.
* Carry required licence texts and notices into the distributed application or package. Flag unclear origin or licence compatibility for maintainer review.

Before completion, check for removed or overwritten headers, inspect release packaging for required notices, and summarize reused components and where their credits appear. These instructions support compliance with the applicable licences and do not replace or expand them.

## Testing

The default build excludes tests tagged `integration`, `shacl`, and `slow`. Start with the smallest relevant check:

```sh
./mvnw -pl IFCtoLBD -am \\
  -Dtest=TestClassName \\
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Then select the appropriate broader check:

```sh
./mvnw install                 # normal fast test suite
./mvnw verify -Pintegration    # unit, integration, and SHACL tests
./mvnw verify -Pfull           # all tests, including slow property tests
```

For conversion or RDF mapping changes, add or update a focused test under `IFCtoLBD/src/test`. Prefer small, deterministic IFC fixtures and offline fixtures for external services and registries. Assert RDF relationships, literals, or SHACL results. Statement order is immaterial. Run the integration profile when conversion behavior changes.

Derive expectations independently from the changed implementation. For a complex graph, pair a trusted example with a relationship between runs: reordering inputs that the contract defines as unordered should preserve the expected facts. For a bug fix, reproduce the failure and cover it when existing checks would miss it.

**MCP**

```sh
npm --prefix IFCtoLBD\_MCP ci
npm --prefix IFCtoLBD\_MCP run check
npm --prefix IFCtoLBD\_MCP run build:converter
npm --prefix IFCtoLBD\_MCP run test:integration
npm --prefix IFCtoLBD\_MCP run smoke
```

**Older Node.js examples**

```sh
npm --prefix IFCtoLBD\_NodeJS test
```

**Modern HTTP/OpenAPI service**

```sh
npm --prefix IFCtoLBD\_OpenAPI\_Node\_js ci
npm --prefix IFCtoLBD\_OpenAPI\_Node\_js run check
npm --prefix IFCtoLBD\_OpenAPI\_Node\_js test
npm --prefix IFCtoLBD\_OpenAPI\_Node\_js run smoke
```

## Generated and bundled files

Do not hand-edit build or generated output unless the task specifically concerns the artifact. This includes `target/` directories, `node\_modules/`, generated Enunciate output, generated Javadocs under `IFCtoLBD/docs`, generated Vaadin frontend files, and `IFCtoLBD\_MCP/lib/ifctolbd-converter.jar`.

Building `IFCtoLBD` intentionally replaces the MCP converter JAR with one shaded distribution. Verify that only the expected JAR remains in `IFCtoLBD\_MCP/lib`. Use small IFC, RDF, image, or binary fixtures when they demonstrate the behavior.

## Documentation

Update documentation for changes to public APIs, CLI options, profiles, runtime requirements, or user-visible output.

* `README.md` — project overview.
* `docs/quick-start.md` — installation and first conversion.
* `docs/development.md` — builds and architecture.
* `docs/java\_examples.md` — Java API examples.
* `docs/python\_examples.md` — Python usage.
* `docs/using-triples.md` — RDF and SPARQL guidance.
* `IFCtoLBD\_MCP/README.md` — MCP operation and security limits.

Keep commands synchronized with POMs and package scripts. For version changes, search CI, manifests, documentation, examples, and release assertions for hard-coded versions.

## Completion checklist

1. Review `git diff` against the initial working tree. Remove accidental generated changes introduced by this task and preserve pre-existing changes.
2. Run the narrowest relevant test, then the applicable reactor, integration, or service checks. Exercise the affected public entry point and a consequential boundary or failure case.
3. Check affected documentation, examples, packaged artifacts, and attribution.
4. Report the user-visible outcome, exactly which checks ran, which were skipped and why, and any remaining external-tool, network, or platform-specific verification.



