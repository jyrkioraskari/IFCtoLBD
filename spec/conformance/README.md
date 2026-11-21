# Portable RDF conformance suite 0.1.0

This case set is versioned separately from the Java converter. It checks selected
RDF facts, not byte-for-byte Turtle. Equivalent prefixes, statement order, and
blank-node names are accepted. [cases.json](cases.json) records each case,
its inputs, settings, and the location of its expected answer. The three getting-started inputs, SPARQL
questions, and existing expected tables remain authoritative in their original
example folders; the manifest points to those tables. Each case applies
only to the profiles named in the manifest. Cross-revision identity is opt-in:
the revision case uses `revision-ready`, a shared `training-building` model
scope, and the model-scoped GUID policy exercised by the existing example.

Install Python 3.10+ and the declared RDF dependency, plus JDK 21+ for the
reference command. Run these commands from the repository root:

```sh
python3 -m pip install -r spec/conformance/requirements.txt
python3 spec/conformance/check.py reference
```

The reference command builds the current checkout with the Maven wrapper,
selects the shaded converter JAR produced by that build, converts all cases,
checks the RDF, and writes outputs only in a temporary directory. Set
`JAVA_HOME` and `PATH` as needed for your JDK. Pass `--output-dir` to keep the
reference RDF for inspection, or `--maven-repo` if the default Maven repository
is not writable.

To check RDF from any other implementation, produce Turtle for the inputs
using each output's declared profile and options in `cases.json`, then pass the
files in the manifest's output order:

```sh
python3 spec/conformance/check.py check building-inventory /tmp/inventory.ttl
python3 spec/conformance/check.py check missing-vs-false /tmp/missing.ttl /tmp/false.ttl
python3 spec/conformance/check.py check identity-across-revisions /tmp/before.ttl /tmp/after.ttl
python3 spec/conformance/check.py check equivalent-lengths /tmp/mm-materials.ttl /tmp/m-materials.ttl /tmp/mm-properties.ttl
```

The `check` command reads only the supplied RDF and this published case set.
For example, a changed normalized length reports a failure like:

```text
FAIL suite 0.1.0 case equivalent-lengths profile materials-bmo, properties-opm: equivalent-lengths: millimetres normalized length; expected Decimal('2.5'); observed Decimal('2500')
```

The unit case deliberately checks two related promises. `materials-bmo`
normalizes layer thickness to `2.5` metres and links a QUDT quantity with
`unit:M`. `properties-opm` resolves the explicit metre unit on `TestLength`
ahead of the millimetre project unit. Ordinary OPM property values are not
generally normalized by this converter; this case does not claim otherwise.
Expected values come from the IFC source facts and documented mapping rules,
never from reference converter output.

During preparation, the named-profile CLI path ignored an explicit `--hasUnits`
flag, leaving the OPM property unit absent. This was a CLI option propagation
discrepancy, not a reason to weaken the expected answer. The conformance change
passes that flag into the named-profile request.
