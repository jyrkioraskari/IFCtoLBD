# Review ETIM pump suggestions offline

The IFC4 model contains five pumps. The source supplies an ETIM classification
for `P-Source`. `P-Reviewed` and `P-Suggested` have independent name and flow
observations; only the former is accepted in the second conversion.
`P-Ambiguous` lacks subtype evidence. `P-Contradictory` has an incompatible
flow datatype. The flow observation has no explicit unit, which the review
query reports as incomplete evidence. The fixture uses ETIM **10.0** class
[`EC010980` (Built-in circulation pump)](https://viewer.etim-international.com/class/EC010980?lang=en-GB).
The [bSDD class record](https://api.bsdd.buildingsmart.org/api/Class/v1?Uri=https%3A%2F%2Fidentifier.buildingsmart.org%2Furi%2Fetim%2Fetim%2F10.0%2Fclass%2FEC010980&IncludeClassRelations=true)
lists `IfcPump` as a related IFC entity. The test pins a small offline resolver
snapshot of that relation and class definition. This sample is regression data,
not an accuracy estimate.

From the repository root, run the example through `IFCtoLBDConverter` in a
fresh JUnit temporary directory:

```sh
JAVA_HOME="$PWD/.tools/jdk" PATH="$PWD/.tools/jdk/bin:$PATH" \
  ./mvnw -Dmaven.repo.local="$PWD/.m2/repository" -pl IFCtoLBD -am \
  -Dtest=EtimPumpExampleTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The test converts `model.ifc` twice, executes `inventory.rq` and `review.rq`
against the actual conversion models, and compares the rows with the four
`expected-*.txt` files. Change the `VALUES ?subtype` line in each query to
retrieve a different verified subtype. Before review, the inventory contains
only `P-Source`.
After accepting `P-Reviewed`, it contains `P-Source` and `P-Reviewed` with
their distinct origins. The review still contains the unaccepted suggestion,
the ambiguous pump, and the contradictory pump. Neither suggestion enters the
inventory on score alone. The converter keeps the review reference and source
observations in RDF. ProductExtractor exposes assertions, suggestions,
assessments, and observations separately in its JSON element detail.

## Evaluate on a held-out project

Freeze one ETIM dictionary edition and its published IFC relations. Have
reviewers assign subtype labels to pumps in a project excluded from tuning.
Run the same candidate set with three ranking inputs: published relations only,
IFC name/description only, and full independent IFC observations. Exclude
supplied ETIM labels and class-dependent property mappings from every ranking
input. For each run, record:

- `N`: all reviewed pumps in the held-out project;
- `S`: pumps receiving a suggestion, and `A = N - S` abstentions;
- `TP`: suggested classes accepted by the independent reviewers;
- `FP`: suggested classes rejected by those reviewers;
- `FN`: reviewed target classes omitted from suggestions.

Report precision `TP/(TP+FP)`, recall `TP/(TP+FN)`, coverage `S/N`, and
abstention rate `A/N`, with raw numerators and denominators. Include review
effort and incorrect acceptance counts. No independently reviewed held-out
project is included in this repository, so these research metrics are not
reported from the five-pump fixture.
