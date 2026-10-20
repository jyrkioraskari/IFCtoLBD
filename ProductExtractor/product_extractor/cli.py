"""Command-line interface for the ProductExtractor.

Reads an IFCtoLBD Linked Building Data Turtle file and writes a JSON array with
one summary object per building element (materials, material layers, property
sets and values, and IFC attributes). With ``--web`` it instead serves a local
web interface for interactive extraction.
"""

import argparse
import sys
from pathlib import Path

from rdflib import Graph
from rdflib.namespace import RDF

from . import namespaces as ns
from .extractor import (
    element_type_counts,
    load_graph,
    parse_type_filter,
    summarize_graph,
    write_json,
)
from .webui import serve


def build_parser():
    parser = argparse.ArgumentParser(
        prog="product-extractor",
        description=(
            "Summarise building elements from IFCtoLBD LBD Turtle output as JSON: "
            "materials, material layers, property sets with values, and IFC attributes."
        ),
    )
    parser.add_argument(
        "-i",
        "--input",
        help=(
            "IFCtoLBD output file to read: LBD Turtle (.ttl) or TriG (.trig). "
            "Required unless --web is used without a file (load it in the browser)."
        ),
    )
    parser.add_argument(
        "-o",
        "--output",
        help=(
            "JSON file to write (default: '<input name>-elements.json' next to the "
            "input; use '-' for standard output)."
        ),
    )
    parser.add_argument(
        "-t",
        "--types",
        help=(
            "Comma-separated element type filter, e.g. 'Wall,Slab,Door' or "
            "'beo:Wall,beo:Door'. Matches rdf:type local names case-insensitively. "
            "Omit to include every building element (bot:Element)."
        ),
    )
    parser.add_argument(
        "--list-types",
        action="store_true",
        help="List the element types found in the input with occurrence counts, then exit.",
    )
    parser.add_argument(
        "--compact",
        action="store_true",
        help="Write compact JSON (single line) instead of indented JSON.",
    )
    parser.add_argument(
        "--web",
        action="store_true",
        help=(
            "Start a local web interface for interactive extraction instead of "
            "writing a JSON file. Ignores --output/--types/--compact."
        ),
    )
    parser.add_argument(
        "--host",
        default="127.0.0.1",
        help="Interface for --web to bind (default: 127.0.0.1).",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=8000,
        help="Port for --web (default: 8000; 0 picks a free port).",
    )
    return parser


def _default_output_path(input_path):
    input_path = Path(input_path)
    return str(input_path.with_name(input_path.stem + "-elements.json"))


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)

    if not args.web and not args.input:
        parser.error("--input is required (or start --web and load a file in the browser)")

    if args.input:
        try:
            graph = load_graph(args.input)
        except FileNotFoundError:
            print(f"error: input file not found: {args.input}", file=sys.stderr)
            return 1
        except Exception as error:  # rdflib raises various parser exceptions
            print(f"error: could not parse {args.input}: {error}", file=sys.stderr)
            return 1
    else:
        graph = Graph()

    if args.list_types:
        counts = element_type_counts(graph)
        width = max((len(name) for name in counts), default=0)
        for name, count in counts.items():
            print(f"{name:<{width}}  {count}")
        elements = len(list(graph.subjects(RDF.type, ns.ELEMENT_CLASS)))
        print(f"\n{elements} building elements, {len(counts)} distinct types")
        return 0

    if args.web:
        try:
            serve(graph, host=args.host, port=args.port, source_name=args.input)
        except OSError as error:
            print(f"error: could not start the web interface: {error}", file=sys.stderr)
            return 1
        return 0

    type_filter = parse_type_filter(args.types)
    summaries = summarize_graph(graph, type_filter)

    output = args.output if args.output else _default_output_path(args.input)
    write_json(summaries, output, compact=args.compact)

    if output != "-":
        print(f"Wrote {len(summaries)} element summaries to {output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
