# AGENTS.md

## Project overview

IFCtoLBD converts IFC building models into Linked Building Data (RDF). The main
implementation is a Java 21 multi-module Maven project, with separate Python,
Node.js, OpenAPI, Scala, desktop, and MCP integrations.

Read `README.md` for the user-facing overview and `docs/development.md` before
making architectural or build changes.

## Repository layout

The root Maven reactor builds these modules in dependency order:

- `IFCtoRDF`: IFC parsing and IFC-oriented RDF generation.
- `IFCtoLBD_Geometry`: geometry extraction and IfcOpenShell integration.
- `IFCtoLBD`: core conversion APIs, RDF mappings, validation, profiles, and CLI.
- `IFCProps2ExcelOnline`: Vaadin-based property export application.
- `IFCtoLBD_Desktop_2026`: JavaFX desktop application.

These projects are not part of the root reactor and must be handled separately:

- `IFCtoLBD_MCP`: Node.js MCP server using the shaded converter JAR.
- `IFCtoLBD_NodeJS`: Node.js integration examples.
- `IFCtoLBD_Python`: JPype wrapper and Python examples.
- `IFCtoLBD_OpenAPI`: separate Maven web API.
- `ifc-to-lbd-scala`: Scala example.
- `maven_demo`: historical consumer example.

## Build requirements

- Use JDK 21 or newer for the root build.
- Prefer the checked-in Maven wrapper: `./mvnw`.
- Run commands from the repository root unless a command explicitly targets a
  separate project.
- The first Maven build may require network access to download dependencies.

Common commands:

```sh
./mvnw install
./mvnw verify
./mvnw -pl IFCtoLBD -am test
./mvnw -pl IFCtoLBD -am package -DskipTests
```

`-pl <module> -am` is preferred for focused work because it also builds required
reactor dependencies.

## Testing

The default build excludes tests tagged `integration`, `shacl`, and `slow`.

Use the smallest relevant check first:

```sh
./mvnw -pl IFCtoLBD -am \
  -Dtest=TestClassName \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Then select the appropriate broader check:

```sh
./mvnw install                 # normal fast test suite
./mvnw verify -Pintegration    # unit, integration, and SHACL tests
./mvnw verify -Pfull           # all tests, including slow property tests
```

For conversion or RDF mapping changes:

- Add or update a focused test under `IFCtoLBD/src/test`.
- Prefer small, deterministic IFC fixtures.
- Assert RDF relationships, literals, or SHACL results rather than serialized
  statement order.
- Use offline fixtures for external registries and services.
- Run the integration profile when conversion behavior changes.

For MCP changes:

```sh
npm --prefix IFCtoLBD_MCP ci
npm --prefix IFCtoLBD_MCP run check
npm --prefix IFCtoLBD_MCP run build:converter
npm --prefix IFCtoLBD_MCP run test:integration
npm --prefix IFCtoLBD_MCP run smoke
```

For the older Node.js examples:

```sh
npm --prefix IFCtoLBD_NodeJS test
```

## Implementation guidance

Start core converter work in:

```text
IFCtoLBD/src/main/java/org/linkedbuildingdata/ifc2lbd
```

Important entry points include:

- `IFCtoLBDConverter_CLI`: command-line interface.
- `IFCtoLBDConverter`: conversion orchestration.
- `core/IFCtoLBDConverterCore`: topology, element, attribute, and property mapping.
- `core/utils/IfcOWLUtils`: IFC relationship traversal.
- `ConversionRequest`: conversion options and profiles.
- `ConversionResult`: generated graphs and validation results.
- `ConversionSession`: temporary storage and conversion resource ownership.

Preserve the established module boundaries. Avoid moving parsing, geometry,
desktop, or integration concerns into the core converter without a clear need.

Close `ConversionResult`, `IFCtoLBDConverter`, `ConversionSession`, and other
owned resources where applicable. Copy or serialize data that must outlive its
owning session.

Follow the style of adjacent code. The repository does not define a universal
automatic formatter, so avoid unrelated formatting or broad mechanical rewrites.

## RDF and IFC changes

When changing mappings:

- Keep vocabulary IRIs and identifier generation stable unless the task
  explicitly requires a compatibility change.
- Consider IFC schema variants and missing optional relationships.
- Do not assume every spatial object has mesh geometry.
- Keep Turtle and other RDF fixtures deterministic and human-readable.
- Document new vocabulary terms or profiles and include an example query when
  they are user-visible.
- For stable identifiers across revisions, keep model scope semantics intact.

Geometry is optional. Tests unrelated to geometry should not require a local
IfcOpenShell installation. Geometry-specific work currently targets
IfcOpenShell 0.8.5 and must account for the legacy fallback backend.

## Generated and bundled files

Do not hand-edit generated or build output unless the task specifically concerns
the generated artifact. This includes:

- Any `target/` directory.
- `node_modules/`.
- Generated Enunciate output.
- Generated Javadocs under `IFCtoLBD/docs`.
- Generated Vaadin frontend files.
- The shaded `IFCtoLBD_MCP/lib/ifctolbd-converter.jar`.

Builds of `IFCtoLBD` intentionally replace the MCP converter JAR with one shaded
distribution. Verify that only the expected JAR remains in `IFCtoLBD_MCP/lib`.

Avoid adding large IFC, RDF, image, or binary fixtures when a smaller fixture can
demonstrate the behavior.

## Documentation

Update documentation when changing public APIs, CLI options, profiles, runtime
requirements, or user-visible output.

Relevant files include:

- `README.md`: project overview.
- `docs/quick-start.md`: installation and first conversion.
- `docs/development.md`: builds and architecture.
- `docs/java_examples.md`: Java API examples.
- `docs/python_examples.md`: Python usage.
- `docs/using-triples.md`: RDF and SPARQL guidance.
- `IFCtoLBD_MCP/README.md`: MCP operation and security limits.

Keep commands synchronized with the current POMs and package scripts. When
changing a project version, search for hard-coded versions in CI, manifests,
documentation, examples, and release assertions.

## Working-tree safety

The repository may already contain unrelated or generated modifications.

Before editing:

```sh
git status --short
```

Preserve user changes. Do not reset, revert, delete, or reformat unrelated files.
Limit edits to files required by the task and report any pre-existing changes
that prevent safe verification.

## Completion checklist

Before handing off a change:

1. Review `git diff` and remove accidental generated-file changes.
2. Run the narrowest relevant test.
3. Run the applicable reactor or integration check when practical.
4. State exactly which checks ran and which were not run.
5. Note any required external tools, network access, or platform-specific checks.
