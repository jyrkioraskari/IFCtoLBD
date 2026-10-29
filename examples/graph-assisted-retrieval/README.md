# Graph-assisted retrieval evaluation

This separate, no-LLM example compares document-only retrieval with deterministic
graph-assisted retrieval over the same synthetic building facts and supporting
documents. It measures retrieval coverage; it does not claim that either approach
is better before the results are produced.

The fixture is original, redistributable under the repository's Apache-2.0
license, and deliberately small enough to audit.

## Run

Build the converter distribution first, then execute the evaluation:

```sh
npm --prefix IFCtoLBD_MCP run build:converter
node examples/graph-assisted-retrieval/run-evaluation.mjs
```

Use `--jar /path/to/ifctolbd-converter.jar` to select another build, or
`--output /path/to/results.json` to retain a machine-readable report. The runner
requires Java 21 and Node.js 20 or newer. It performs no network requests.

The report records configuration, elapsed time, per-question expected-token
coverage, evidence-reference correctness, unanswerable handling, and execution
failures. These are retrieval metrics, not an assessment of generated prose or
the factual correctness of the fixture.

