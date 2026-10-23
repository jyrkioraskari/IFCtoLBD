# Optional BIM–sensor integration example

This development-only, offline example keeps conversion facts, supplied links, and measurements separate.
It converts two IFC/JSON revisions, resolves a reviewed `sosa:hosts` mapping, discovers a temperature
HVAC terminal's temperature sensor and its local measurement service with SPARQL using BOT, SOSA, and Brick terms, then retrieves the
latest value from a temporary loopback HTTP API. Measurements are never copied into the building graph.

Build and run from the repository root:

```sh
./mvnw -pl IFCtoLBD -am package -DskipTests
java -cp IFCtoLBD_MCP/lib/ifctolbd-converter.jar examples.BimSensorIntegrationExample
```

The persistent project namespace and unchanged GlobalId retain the meeting-room HVAC terminal IRI after file renaming,
element reordering, and the added terminal. The example answers “Which modeled HVAC terminals have linked
temperature sensors, and what are their latest readings?” It also reconverts revision 2 with existing identity mode
and resolves the mapping again, showing that stable identity is optional. A changed GlobalId would require
an explicit reconciliation row; names and geometry are never used to infer continuity.
