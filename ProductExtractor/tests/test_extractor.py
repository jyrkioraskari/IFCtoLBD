"""Tests for the ProductExtractor element summaries."""

import json
from pathlib import Path

import pytest
from rdflib import URIRef

from product_extractor import (
    element_type_counts,
    extract_attributes,
    extract_materials,
    extract_property_sets,
    find_elements,
    load_graph,
    parse_type_filter,
    summarize_file,
    summarize_graph,
)
from product_extractor.cli import main as cli_main

FIXTURES = Path(__file__).parent / "fixtures"
SAMPLE = FIXTURES / "sample-lbd.ttl"
SAMPLE_OPM = FIXTURES / "sample-opm-lbd.ttl"
REPO_DEMO = (
    Path(__file__).parents[2] / "IFCtoLBD_MCP" / "demo" / "output" / "duplex-demo.ttl"
)


@pytest.fixture(scope="module")
def graph():
    return load_graph(SAMPLE)


@pytest.fixture(scope="module")
def summaries(graph):
    return summarize_graph(graph)


def _by_iri(summaries, iri):
    matches = [s for s in summaries if s["iri"] == iri]
    assert len(matches) == 1, f"expected exactly one element with IRI {iri}"
    return matches[0]


WALL_1 = "https://example.com/building/wall-1"
WALL_2 = "https://example.com/building/wall-2"
SLAB_1 = "https://example.com/building/slab-1"


def _ref(iri):
    return URIRef(iri)


# --- element discovery --------------------------------------------------------


def test_finds_only_building_elements(graph):
    iris = {str(e) for e in find_elements(graph)}
    assert iris == {WALL_1, WALL_2, SLAB_1}


def test_summary_count_and_order(summaries):
    assert [s["iri"] for s in summaries] == sorted([WALL_1, WALL_2, SLAB_1])


def test_summary_schema(summaries):
    for summary in summaries:
        assert set(summary) == {
            "iri",
            "label",
            "types",
            "ifcType",
            "attributes",
            "propertySets",
            "materials",
        }
        json.dumps(summary)  # must be JSON-serialisable


# --- identity, types, IFC type ------------------------------------------------


def test_element_types_and_label(summaries):
    wall = _by_iri(summaries, WALL_1)
    assert wall["label"] == "Basic Wall:Exterior:1"
    assert wall["types"] == [
        "https://pi.pauwel.be/voc/buildingelement#Wall",
        "https://w3id.org/bot#Element",
    ]


def test_ifc_type_from_local_name(summaries):
    # owl:sameAs target inst:IfcWallStandardCase_12429 has no rdf:type triples;
    # the IFC type is parsed from the local name.
    assert _by_iri(summaries, WALL_1)["ifcType"] == "IfcWallStandardCase"


def test_ifc_type_from_typed_resource(summaries):
    # inst:IfcWall_100 carries an explicit ifc:IfcWall type.
    assert _by_iri(summaries, WALL_2)["ifcType"] == "IfcWall"


def test_predefined_type_present(summaries):
    slab = _by_iri(summaries, SLAB_1)
    assert "https://pi.pauwel.be/voc/buildingelement#Slab-FLOOR" in slab["types"]


# --- IFC attributes -----------------------------------------------------------


def test_attributes_extracted(graph):
    attributes = extract_attributes(graph, _ref(WALL_1))
    assert attributes == {
        "globalIdIfcRoot": "GUID-WALL-1",
        "batid": "1",
        "objectTypeIfcObject": "Basic Wall:Exterior",
    }


def test_attribute_values_keep_types(graph):
    attributes = extract_attributes(graph, _ref(WALL_1))
    assert attributes["batid"] == "1"  # plain literal stays a string


# --- property sets (level 1) --------------------------------------------------


def test_property_sets_grouped(graph):
    psets = extract_property_sets(graph, _ref(WALL_1))
    by_name = {p["name"]: p["properties"] for p in psets}
    assert by_name["Pset_WallCommon"] == {"isExternal": True, "loadBearing": False}
    assert by_name["Pset_Custom"] == {"customProp": "custom value"}


def test_shared_property_predicate_lists_each_candidate_pset(graph):
    # props:isExternal_property_simple is declared for Pset_WallCommon and
    # Pset_BeamCommon; at level 1 the value appears under each candidate set.
    psets = extract_property_sets(graph, _ref(WALL_1))
    by_name = {p["name"]: p["properties"] for p in psets}
    assert by_name["Pset_BeamCommon"] == {"isExternal": True}


def test_property_sets_sorted(graph):
    psets = extract_property_sets(graph, _ref(WALL_1))
    assert [p["name"] for p in psets] == sorted(p["name"] for p in psets)


def test_slab_property_value_is_boolean(graph):
    psets = extract_property_sets(graph, _ref(SLAB_1))
    by_name = {p["name"]: p["properties"] for p in psets}
    assert by_name["Pset_WallCommon"] == {"isExternal": False}
    assert by_name["Pset_BeamCommon"] == {"isExternal": False}


# --- materials and material layers --------------------------------------------


def test_layer_set_material_assignment(summaries):
    materials = _by_iri(summaries, WALL_1)["materials"]
    assert len(materials) == 1
    assignment = materials[0]
    assert assignment["iri"] == "https://example.com/building/assignment-1"
    assert assignment["inherited"] is False
    assert assignment["material"] is None

    layer_set = assignment["layerSet"]
    assert layer_set["name"] == "Ordered wall set"
    assert layer_set["description"] == "Two ordered layers"
    assert [layer["position"] for layer in layer_set["layers"]] == [1, 2]

    first, second = layer_set["layers"]
    assert first["material"]["name"] == "Brick"
    assert first["material"]["description"] == "Fired clay"
    assert first["material"]["category"] == "Masonry"
    assert first["thickness"] == 0.1
    assert first["thicknessUnit"] == "http://qudt.org/vocab/unit/M"
    assert first["thicknessSourceValue"] == 100.0

    assert second["material"]["name"] == "Insulation"
    assert second["material"]["category"] is None
    assert second["thickness"] == 0.05
    assert second["thicknessSourceValue"] is None

    usage = assignment["usage"]
    assert usage["layerSetDirection"] == "AXIS2"
    assert usage["directionSense"] == "POSITIVE"
    assert usage["offsetFromReferenceLine"] == 12.5


def test_inherited_direct_material(summaries):
    materials = _by_iri(summaries, WALL_2)["materials"]
    assert len(materials) == 1
    assignment = materials[0]
    assert assignment["inherited"] is True
    assert assignment["sourceIfcType"] == "IfcWallType"
    assert assignment["sourceIfcTypeResource"] == "https://example.com/building/IfcWallType_41"
    assert assignment["layerSet"] is None
    assert assignment["usage"] is None
    assert assignment["material"]["name"] == "Concrete"
    assert assignment["material"]["category"] == "Structural"


def test_element_without_materials_has_empty_list(summaries):
    assert _by_iri(summaries, SLAB_1)["materials"] == []


# --- type filtering -----------------------------------------------------------


def test_parse_type_filter():
    assert parse_type_filter("Wall, beo:Slab ,Door") == ["wall", "slab", "door"]
    assert parse_type_filter("") == []
    assert parse_type_filter(None) == []
    assert parse_type_filter(["beo:Wall", "furn:Furniture"]) == ["wall", "furniture"]


def test_type_filter_wall(graph):
    result = summarize_graph(graph, "Wall")
    assert {s["iri"] for s in result} == {WALL_1, WALL_2}


def test_type_filter_predefined_type(graph):
    result = summarize_graph(graph, "beo:Slab-FLOOR")
    assert {s["iri"] for s in result} == {SLAB_1}


def test_type_filter_case_insensitive(graph):
    result = summarize_graph(graph, "wAlL")
    assert {s["iri"] for s in result} == {WALL_1, WALL_2}


def test_type_filter_multiple(graph):
    result = summarize_graph(graph, "Wall,Slab")
    assert len(result) == 3


def test_type_filter_unknown_type(graph):
    assert summarize_graph(graph, "Chimney") == []


def test_element_type_counts(graph):
    counts = element_type_counts(graph)
    assert counts["Wall"] == 2
    assert counts["Slab"] == 1
    assert counts["Slab-FLOOR"] == 1
    assert counts["Element"] == 3
    assert "Space" not in counts


# --- determinism --------------------------------------------------------------


def test_output_is_deterministic():
    first = json.dumps(summarize_file(SAMPLE), indent=2, ensure_ascii=False)
    second = json.dumps(summarize_file(SAMPLE), indent=2, ensure_ascii=False)
    assert first == second


# --- OPM levels 2/3 and bSDD property sets -------------------------------------


@pytest.fixture(scope="module")
def opm_summaries():
    return summarize_file(SAMPLE_OPM)


def test_opm_level_3_property_state(opm_summaries):
    wall = _by_iri(opm_summaries, "https://example.com/building/wall-opm")
    psets = {p["name"]: p["properties"] for p in wall["propertySets"]}
    assert psets == {"Pset_WallCommon": {"loadBearing": True}}


def test_opm_level_2_direct_value(opm_summaries):
    beam = _by_iri(opm_summaries, "https://example.com/building/beam-opm")
    psets = {p["name"]: p["properties"] for p in beam["propertySets"]}
    assert psets == {"Pset_BeamCommon": {"span": 4.1}}


def test_bsdd_property_set_resources(opm_summaries):
    door = _by_iri(opm_summaries, "https://example.com/building/door-1")
    psets = {p["name"]: p["properties"] for p in door["propertySets"]}
    assert psets == {"Pset_DoorCommon": {"fireRating": "REI 60"}}


# --- CLI ----------------------------------------------------------------------


def test_cli_writes_json_file(tmp_path):
    output = tmp_path / "out.json"
    exit_code = cli_main(["--input", str(SAMPLE), "--output", str(output)])
    assert exit_code == 0
    data = json.loads(output.read_text(encoding="utf-8"))
    assert isinstance(data, list)
    assert {s["iri"] for s in data} == {WALL_1, WALL_2, SLAB_1}


def test_cli_default_output_name(tmp_path):
    input_copy = tmp_path / "model.ttl"
    input_copy.write_text(SAMPLE.read_text(encoding="utf-8"), encoding="utf-8")
    exit_code = cli_main(["--input", str(input_copy)])
    assert exit_code == 0
    default_output = tmp_path / "model-elements.json"
    assert default_output.exists()
    assert len(json.loads(default_output.read_text(encoding="utf-8"))) == 3


def test_cli_type_filter(tmp_path):
    output = tmp_path / "walls.json"
    exit_code = cli_main(
        ["--input", str(SAMPLE), "--output", str(output), "--types", "Wall"]
    )
    assert exit_code == 0
    data = json.loads(output.read_text(encoding="utf-8"))
    assert {s["iri"] for s in data} == {WALL_1, WALL_2}


def test_cli_stdout(tmp_path, capsys):
    exit_code = cli_main(["--input", str(SAMPLE), "--output", "-", "--compact"])
    assert exit_code == 0
    data = json.loads(capsys.readouterr().out)
    assert len(data) == 3


def test_cli_list_types(capsys):
    exit_code = cli_main(["--input", str(SAMPLE), "--list-types"])
    assert exit_code == 0
    out = capsys.readouterr().out
    assert "Wall" in out and "Slab-FLOOR" in out
    assert "3 building elements" in out


def test_cli_missing_input(capsys):
    exit_code = cli_main(["--input", "does-not-exist.ttl"])
    assert exit_code == 1
    assert "not found" in capsys.readouterr().err


# --- TriG (named graphs are merged) -------------------------------------------

TRIG_SAMPLE = """@prefix beo: <https://pi.pauwel.be/voc/buildingelement#> .
@prefix bot: <https://w3id.org/bot#> .
@prefix inst: <https://example.com/building/> .
@prefix props: <http://lbd.arch.rwth-aachen.de/props#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .

<https://example.com/building/graph-product> {
  inst:wall-trig rdf:type bot:Element , beo:Wall ;
      rdfs:label "TriG wall" ;
      props:globalIdIfcRoot_attribute_simple "GUID-TRIG" .
}
<https://example.com/building/graph-property> {
  props:globalIdIfcRoot_attribute_simple rdf:type owl:DatatypeProperty ;
      rdfs:comment "IFC standard attribute globalIdIfcRoot" .
  props:loadBearing_property_simple rdf:type owl:DatatypeProperty ;
      rdfs:comment "IFC property set Pset_WallCommon property loadBearing" .
  inst:wall-trig props:loadBearing_property_simple true .
}
"""


def test_trig_named_graphs_are_merged(tmp_path):
    trig = tmp_path / "model.trig"
    trig.write_text(TRIG_SAMPLE, encoding="utf-8")
    summaries = summarize_file(trig)
    assert len(summaries) == 1
    wall = summaries[0]
    assert wall["attributes"] == {"globalIdIfcRoot": "GUID-TRIG"}
    assert wall["propertySets"] == [
        {"name": "Pset_WallCommon", "properties": {"loadBearing": True}}
    ]


# --- parse_graph_text (used for browser uploads) -------------------------------


def test_parse_graph_text_turtle():
    from product_extractor import parse_graph_text

    graph = parse_graph_text(SAMPLE.read_text(encoding="utf-8"))
    assert len(summarize_graph(graph)) == 3


def test_parse_graph_text_trig():
    from product_extractor import parse_graph_text

    graph = parse_graph_text(TRIG_SAMPLE)
    assert len(summarize_graph(graph)) == 1


def test_parse_graph_text_invalid_raises():
    from product_extractor import parse_graph_text

    with pytest.raises(Exception):
        parse_graph_text("not valid turtle {{{")


# --- real IFCtoLBD output from this repository --------------------------------


@pytest.mark.skipif(not REPO_DEMO.exists(), reason="repository demo output not present")
def test_repository_demo_output():
    summaries = summarize_file(REPO_DEMO)
    assert len(summaries) > 100
    for summary in summaries:
        assert summary["iri"]
        assert "https://w3id.org/bot#Element" in summary["types"]
        assert "globalIdIfcRoot" in summary["attributes"]
        json.dumps(summary)

    walls = summarize_file(REPO_DEMO, "Wall")
    assert walls
    assert all(
        "https://pi.pauwel.be/voc/buildingelement#Wall" in s["types"] for s in walls
    )
    wall = walls[0]
    psets = {p["name"]: p["properties"] for p in wall["propertySets"]}
    assert psets["Pset_WallCommon"]["isExternal"] in (True, False)
    assert psets["Pset_WallCommon"]["loadBearing"] in (True, False)
