#!/usr/bin/env python3
"""Portable RDF assertions for the versioned IFCtoLBD conformance cases."""

import argparse
from contextlib import nullcontext
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from rdflib import Graph, Literal, Namespace, RDF, RDFS, URIRef


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = json.loads((Path(__file__).parent / "cases.json").read_text())
BOT = Namespace("https://w3id.org/bot#")
QUDT = Namespace("http://qudt.org/schema/qudt/")
BMO = Namespace("https://w3id.org/digitalconstruction/BuildingMaterials#")
IFCTOLBD = Namespace("https://w3id.org/ifctolbd/mapping#")
OPM = Namespace("https://w3id.org/opm#")
SCHEMA = Namespace("http://schema.org/")
EVIDENCE = Namespace("https://w3id.org/ifctolbd/evidence#")


def require(case, assertion, expected, observed):
    if expected != observed:
        raise AssertionError(f"{case}: {assertion}; expected {expected!r}; observed {observed!r}")


def labelled(graph, name):
    return {s for s in graph.subjects(RDFS.label, Literal(name)) if (s, RDF.type, BOT.Element) in graph}


def numeric(case, assertion, value, expected):
    try:
        observed = Decimal(str(value))
    except (InvalidOperation, TypeError):
        observed = str(value)
    require(case, assertion, Decimal(str(expected)), observed)


def answer_rows(relative_path):
    """Read the authoritative Jena table already published with an example."""
    rows = []
    for line in (ROOT / relative_path).read_text().splitlines():
        match = re.fullmatch(r"\|(.+)\|", line)
        if not match:
            continue
        cells = [cell.strip() for cell in match.group(1).split("|")]
        if cells and cells[0].startswith('"'):
            rows.append([json.loads(cell) if cell.startswith('"') else cell for cell in cells])
    return rows


def check(case_id, case, graphs):
    expected = case["expected"]
    if case_id == "building-inventory":
        # This is the published getting-started SPARQL question, evaluated on RDF terms.
        rows = list(graphs["model"].query((ROOT / case["query"]).read_text()))
        observed = [(str(s), str(t), int(c)) for s, t, c in rows]
        answer = answer_rows(expected["answer_file"])
        promised = [(name, iri.strip("<>"), int(count)) for name, iri, count in answer]
        require(case_id, "storey wall inventory",
                promised, observed)
    elif case_id == "missing-vs-false":
        answer = answer_rows(expected["missing_answer_file"])
        require(case_id, "published missing answer count", 1, len(answer))
        wall, missing_property = answer[0]
        require(case_id, "published missing property", "LoadBearing", missing_property)
        prop = URIRef(expected["property"])
        missing, present = graphs["missing"], graphs["false"]
        old_walls = labelled(missing, wall)
        new_walls = labelled(present, wall)
        require(case_id, "target wall exists in both graphs", (1, 1), (len(old_walls), len(new_walls)))
        old_values = list(missing.objects(next(iter(old_walls)), prop))
        new_values = list(present.objects(next(iter(new_walls)), prop))
        require(case_id, "missing LoadBearing assertion", expected["missing_count"], len(old_values))
        require(case_id, "explicit false LoadBearing assertion", [Literal(False)], new_values)
    elif case_id == "identity-across-revisions":
        answer = answer_rows(expected["answer_file"])
        require(case_id, "published revision answer count", 2, len(answer))
        changes = {kind: (iri, label) for kind, iri, label in answer}
        old_iri, old_label = changes["removed label"]
        new_iri, new_label = changes["added label"]
        require(case_id, "published revision identity", old_iri, new_iri)
        iri = URIRef(old_iri.strip("<>"))
        before, after = graphs["before"], graphs["after"]
        for name, graph, label in (("before", before, old_label),
                                   ("after", after, new_label)):
            actual = labelled(graph, label)
            require(case_id, f"{name} wall exact IRI and label", {iri}, actual)
            require(case_id, f"{name} IRI label", [Literal(label)], list(graph.objects(iri, RDFS.label)))
    elif case_id == "equivalent-lengths":
        unit = URIRef(expected["unit"])
        for name in ("millimetres", "metres"):
            graph = graphs[name]
            layers = list(graph.subjects(RDF.type, BMO.Layer))
            require(case_id, f"{name} material layer count", 1, len(layers))
            layer = layers[0]
            scalars = list(graph.objects(layer, BMO.hasThickness))
            require(case_id, f"{name} scalar thickness count", 1, len(scalars))
            numeric(case_id, f"{name} scalar thickness", scalars[0], expected["value"])
            quantities = list(graph.objects(layer, IFCTOLBD.thicknessQuantity))
            require(case_id, f"{name} thickness quantity count", 1, len(quantities))
            quantity = quantities[0]
            require(case_id, f"{name} metre unit IRI", [unit], list(graph.objects(quantity, QUDT.unit)))
            values = list(graph.objects(quantity, QUDT.numericValue))
            require(case_id, f"{name} quantity numeric value count", 1, len(values))
            numeric(case_id, f"{name} normalized length", values[0], expected["value"])
        graph = graphs["explicit-unit"]
        properties = list(graph.subjects(RDFS.label, Literal(expected["explicit_property_label"])))
        require(case_id, "explicit property count", 1, len(properties))
        states = list(graph.objects(properties[0], OPM.hasPropertyState))
        require(case_id, "explicit property state count", 1, len(states))
        state = states[0]
        require(case_id, "explicit property unit overrides project unit", [unit],
                list(graph.objects(state, QUDT.unit)))
        require(case_id, "explicit property unit resolution method", [Literal(expected["explicit_method"])],
                list(graph.objects(state, EVIDENCE.unitResolutionMethod)))
        values = list(graph.objects(state, SCHEMA.value))
        require(case_id, "explicit property value count", 1, len(values))
        if values:
            numeric(case_id, "explicit property numeric value", values[0], expected["value"])


def read_graphs(case, paths):
    names = [output["name"] for output in case["outputs"]]
    if len(paths) != len(names):
        raise ValueError(f"expected {len(names)} RDF file(s), in this order: {', '.join(names)}")
    graphs = {}
    for name, path in zip(names, paths):
        graph = Graph()
        try:
            graph.parse(path, format="turtle")
        except Exception as error:
            raise ValueError(f"{name} RDF parse; expected valid Turtle; observed {error}") from error
        graphs[name] = graph
    return graphs


def main():
    parser = argparse.ArgumentParser(description="IFCtoLBD portable conformance suite")
    sub = parser.add_subparsers(dest="command", required=True)
    generate = sub.add_parser("reference", help="build current checkout and check Java outputs")
    generate.add_argument("--java", default="java", help="Java executable for the reference converter")
    generate.add_argument("--output-dir", type=Path, help="keep generated RDF in this directory")
    generate.add_argument("--maven-repo", type=Path, help="optional writable Maven repository")
    supplied = sub.add_parser("check", help="check supplied Turtle without Java")
    supplied.add_argument("case_id", choices=MANIFEST["cases"])
    supplied.add_argument("rdf", nargs="+", type=Path)
    args = parser.parse_args()
    if args.command == "reference":
        maven = [str(ROOT / "mvnw")]
        if args.maven_repo:
            maven.append(f"-Dmaven.repo.local={args.maven_repo}")
        subprocess.run([*maven, "-pl", "IFCtoLBD", "-am", "package", "-DskipTests"],
                       cwd=ROOT, check=True)
        jar = ROOT / "IFCtoLBD_MCP/lib/ifctolbd-converter.jar"
        destination = (nullcontext(args.output_dir) if args.output_dir else
                       tempfile.TemporaryDirectory(prefix="ifctolbd-conformance-"))
        with destination as directory:
            Path(directory).mkdir(parents=True, exist_ok=True)
            for case_id, case in MANIFEST["cases"].items():
                paths = []
                for output in case["outputs"]:
                    target = Path(directory) / f"{case_id}-{output['name']}.ttl"
                    result = subprocess.run([args.java, "-cp", str(jar),
                                    "org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter_CLI",
                                    str(ROOT / output["input"]), f"--target_file={target}",
                                    f"--profile={output['profile']}", *output["options"]],
                                   cwd=ROOT, text=True, capture_output=True)
                    if result.returncode:
                        raise RuntimeError(f"{case_id}/{output['name']} conversion failed:\n"
                                           f"{result.stdout[-4000:]}\n{result.stderr[-4000:]}")
                    paths.append(target)
                run_check(case_id, case, paths)
    else:
        case = MANIFEST["cases"][args.case_id]
        run_check(args.case_id, case, args.rdf)


def run_check(case_id, case, paths):
    profiles = ", ".join(dict.fromkeys(o["profile"] for o in case["outputs"]))
    heading = f"suite {MANIFEST['suite_version']} case {case_id} profile {profiles}"
    try:
        check(case_id, case, read_graphs(case, paths))
    except (AssertionError, ValueError) as error:
        print(f"FAIL {heading}: {error}", file=sys.stderr)
        raise SystemExit(1) from error
    print(f"PASS {heading}")


if __name__ == "__main__":
    main()
