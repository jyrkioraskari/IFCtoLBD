"""Local web interface for ProductExtractor.

Serves a single-page UI (and a small JSON API) from the Python standard
library only. The page is parsed once and kept in memory, so the user can
re-run the extraction with different type filters without re-parsing.

Endpoints:

- ``GET /`` — the single-page UI;
- ``GET /api/info`` — element count, per-type counts, material coverage;
- ``GET /api/extract?types=Wall,Slab`` — element summaries as a JSON array;
- ``POST /api/extract`` — same, with a JSON body ``{"types": [...]}``;
- ``POST /api/load`` — replace the loaded graph with an uploaded Turtle/TriG
  document (raw request body, original file name in ``X-Source-Name``).

The server binds to 127.0.0.1 by default; it is a local tool, not a public
service.
"""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from rdflib import Graph

from . import namespaces as ns
from .extractor import (
    element_type_counts,
    find_elements,
    parse_graph_text,
    parse_type_filter,
    summarize_graph,
)

INDEX_HTML_PATH = Path(__file__).parent / "webui" / "index.html"


class ProductExtractorServer(ThreadingHTTPServer):
    """HTTP server carrying the loaded graph and its source file name."""

    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, graph=None, server_address=("127.0.0.1", 8000), source_name=None):
        # The graph is replaced as a whole on upload and never mutated in
        # place, so handlers can safely read the reference without a lock.
        self.graph = graph if graph is not None else Graph()
        self.source_name = source_name
        super().__init__(server_address, ProductExtractorRequestHandler)


def graph_info(graph, source_name):
    """Overview of the loaded graph for the UI's filter controls."""
    elements = find_elements(graph)
    return {
        "source": source_name,
        "elements": len(elements),
        "types": element_type_counts(graph),
        "elementsWithMaterials": sum(
            1 for e in elements if any(graph.objects(e, ns.IFCTOLBD.materialAssignment))
        ),
    }


class ProductExtractorRequestHandler(BaseHTTPRequestHandler):
    server_version = "ProductExtractor/0.1"

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/":
            self._send(200, INDEX_HTML_PATH.read_bytes(), "text/html; charset=utf-8")
        elif path == "/api/info":
            self._send_json(graph_info(self.server.graph, self.server.source_name))
        elif path == "/api/extract":
            query = parse_qs(urlparse(self.path).query)
            types = query.get("types", [""])[0]
            self._extract(parse_type_filter(types))
        else:
            self._send_json({"error": f"not found: {path}"}, status=404)

    def do_POST(self):
        path = urlparse(self.path).path
        if path == "/api/extract":
            self._extract_post()
        elif path == "/api/load":
            self._load()
        else:
            self._send_json({"error": f"not found: {path}"}, status=404)

    def _extract(self, type_filter):
        self._send_json(summarize_graph(self.server.graph, type_filter))

    def _extract_post(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length) or b"{}")
        except ValueError:
            self._send_json({"error": "request body must be a JSON object"}, status=400)
            return
        if not isinstance(body, dict):
            self._send_json({"error": "request body must be a JSON object"}, status=400)
            return
        self._extract(parse_type_filter(body.get("types")))

    def _load(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            text = self.rfile.read(length).decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            self._send_json({"error": "request body must be UTF-8 Turtle/TriG text"}, status=400)
            return
        try:
            graph = parse_graph_text(text)
        except Exception as error:
            self._send_json({"error": f"could not parse the uploaded file: {error}"}, status=400)
            return
        self.server.graph = graph
        self.server.source_name = self.headers.get("X-Source-Name") or "uploaded.ttl"
        self._send_json(graph_info(graph, self.server.source_name))

    def _send_json(self, payload, status=200):
        self._send(status, json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                   "application/json; charset=utf-8")

    def _send(self, status, data, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        pass  # keep the console clean; serve() prints the URL


def make_server(graph=None, host="127.0.0.1", port=8000, source_name=None):
    """Create (but do not start) the web interface server."""
    return ProductExtractorServer(graph, (host, port), source_name)


def serve(graph=None, host="127.0.0.1", port=8000, source_name=None):
    """Run the web interface until interrupted (Ctrl+C)."""
    server = make_server(graph, host, port, source_name)
    bound_host, bound_port = server.server_address[:2]
    print(f"ProductExtractor web interface: http://{bound_host}:{bound_port}/")
    print(f"Loaded file: {source_name or '(none — load a file in the browser)'}")
    print("Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
