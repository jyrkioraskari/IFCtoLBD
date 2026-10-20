"""Tests for the ProductExtractor web interface (stdlib HTTP API)."""

import json
import threading
import urllib.error
import urllib.request
from pathlib import Path

import pytest

from product_extractor import load_graph
from product_extractor.cli import main as cli_main
from product_extractor.webui import make_server

FIXTURE = Path(__file__).parent / "fixtures" / "sample-lbd.ttl"


@pytest.fixture()
def server():
    graph = load_graph(FIXTURE)
    srv = make_server(graph, host="127.0.0.1", port=0, source_name="sample-lbd.ttl")
    thread = threading.Thread(target=srv.serve_forever, daemon=True)
    thread.start()
    yield srv
    srv.shutdown()
    srv.server_close()
    thread.join(timeout=5)


def _url(server, path):
    host, port = server.server_address[:2]
    return f"http://{host}:{port}{path}"


def _get(server, path):
    try:
        with urllib.request.urlopen(_url(server, path)) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get("Content-Type", ""), error.read()


def _get_json(server, path):
    status, _, body = _get(server, path)
    return status, json.loads(body.decode("utf-8"))


def _post(server, path, body, content_type="application/json", headers=None):
    all_headers = {"Content-Type": content_type}
    all_headers.update(headers or {})
    request = urllib.request.Request(
        _url(server, path), data=body, headers=all_headers, method="POST"
    )
    try:
        with urllib.request.urlopen(request) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode("utf-8"))


# --- page and info ------------------------------------------------------------


def test_index_page_served(server):
    status, content_type, body = _get(server, "/")
    assert status == 200
    assert content_type.startswith("text/html")
    assert b"ProductExtractor" in body
    assert b"/api/extract" in body


def test_info(server):
    status, info = _get_json(server, "/api/info")
    assert status == 200
    assert info["source"] == "sample-lbd.ttl"
    assert info["elements"] == 3
    assert info["types"]["Wall"] == 2
    assert info["types"]["Slab"] == 1
    assert info["elementsWithMaterials"] == 2


def test_info_without_loaded_file():
    srv = make_server(host="127.0.0.1", port=0)
    thread = threading.Thread(target=srv.serve_forever, daemon=True)
    thread.start()
    try:
        status, info = _get_json(srv, "/api/info")
        assert status == 200
        assert info["elements"] == 0
        assert info["types"] == {}
        assert info["source"] is None
    finally:
        srv.shutdown()
        srv.server_close()
        thread.join(timeout=5)


def test_unknown_path_is_404(server):
    status, payload = _get_json(server, "/nope")
    assert status == 404
    assert "error" in payload


# --- extraction ---------------------------------------------------------------


def test_extract_get_all(server):
    status, data = _get_json(server, "/api/extract")
    assert status == 200
    assert len(data) == 3


def test_extract_get_with_type_filter(server):
    status, data = _get_json(server, "/api/extract?types=Wall")
    assert status == 200
    assert len(data) == 2
    assert all("buildingelement#Wall" in s["types"][0] or "#Wall" in s["types"][0] for s in data)


def test_extract_post_with_types(server):
    status, data = _post(server, "/api/extract", json.dumps({"types": ["Slab"]}).encode())
    assert status == 200
    assert len(data) == 1
    assert data[0]["label"] == "Floor:Finish Floor:3"


def test_extract_post_with_comma_string(server):
    status, data = _post(server, "/api/extract", json.dumps({"types": "Wall,Slab"}).encode())
    assert status == 200
    assert len(data) == 3


def test_extract_post_empty_body_means_all(server):
    status, data = _post(server, "/api/extract", json.dumps({}).encode())
    assert status == 200
    assert len(data) == 3


def test_extract_post_invalid_json_is_400(server):
    status, payload = _post(server, "/api/extract", b"{not json")
    assert status == 400
    assert "error" in payload


def test_extract_post_non_object_is_400(server):
    status, payload = _post(server, "/api/extract", b"[1,2,3]")
    assert status == 400
    assert "error" in payload


def test_extract_result_matches_cli_output(server):
    from product_extractor import summarize_file

    _, via_api = _get_json(server, "/api/extract?types=Wall")
    via_cli = summarize_file(FIXTURE, "Wall")
    assert via_api == via_cli


# --- file upload ----------------------------------------------------------------


def test_load_uploaded_turtle(server):
    turtle = FIXTURE.read_text(encoding="utf-8")
    status, info = _post(
        server,
        "/api/load",
        turtle.encode("utf-8"),
        content_type="text/turtle",
        headers={"X-Source-Name": "uploaded.ttl"},
    )
    assert status == 200
    assert info["source"] == "uploaded.ttl"
    assert info["elements"] == 3

    # The uploaded graph replaces the preloaded one for later requests.
    status, data = _get_json(server, "/api/extract?types=Wall")
    assert status == 200
    assert len(data) == 2


def test_load_invalid_turtle_is_400(server):
    status, payload = _post(server, "/api/load", b"not valid turtle {{{",
                            content_type="text/turtle")
    assert status == 400
    assert "error" in payload


def test_load_then_extract_uploaded_materials(server):
    turtle = FIXTURE.read_text(encoding="utf-8")
    _post(server, "/api/load", turtle.encode("utf-8"),
          content_type="text/turtle", headers={"X-Source-Name": "uploaded.ttl"})
    _, data = _get_json(server, "/api/extract")
    wall = next(s for s in data if s["label"] == "Basic Wall:Exterior:1")
    assert wall["materials"][0]["layerSet"]["layers"][0]["material"]["name"] == "Brick"


# --- CLI wiring -----------------------------------------------------------------


def test_cli_web_starts_server(monkeypatch, capsys):
    from product_extractor import find_elements

    calls = {}

    def fake_serve(graph, host, port, source_name):
        calls.update(host=host, port=port, source_name=source_name,
                     elements=len(find_elements(graph)))

    monkeypatch.setattr("product_extractor.cli.serve", fake_serve)
    exit_code = cli_main(["--input", str(FIXTURE), "--web", "--port", "0"])
    assert exit_code == 0
    assert calls["port"] == 0
    assert calls["source_name"] == str(FIXTURE)
    assert calls["elements"] == 3


def test_cli_web_without_input_is_allowed(monkeypatch):
    from product_extractor import find_elements

    calls = {}

    def fake_serve(graph, host, port, source_name):
        calls.update(source_name=source_name, elements=len(find_elements(graph)))

    monkeypatch.setattr("product_extractor.cli.serve", fake_serve)
    exit_code = cli_main(["--web", "--host", "127.0.0.1", "--port", "0"])
    assert exit_code == 0
    assert calls["source_name"] is None
    assert calls["elements"] == 0


def test_cli_requires_input_without_web():
    with pytest.raises(SystemExit):
        cli_main([])
