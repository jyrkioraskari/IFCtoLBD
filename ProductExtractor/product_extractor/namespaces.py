"""Namespace and term constants for IFCtoLBD Linked Building Data (LBD) output.

The constants mirror the converter's own namespace classes under
``IFCtoLBD/src/main/java/org/linkedbuildingdata/ifc2lbd/namespace/``.
"""

from rdflib import Namespace

BOT = Namespace("https://w3id.org/bot#")
BEO = Namespace("https://pi.pauwel.be/voc/buildingelement#")
FURN = Namespace("http://pi.pauwel.be/voc/furniture#")
MEP = Namespace("http://pi.pauwel.be/voc/distributionelement#")
OMG = Namespace("https://w3id.org/omg#")
GEO = Namespace("http://www.opengis.net/ont/geosparql#")
PROPS = Namespace("http://lbd.arch.rwth-aachen.de/props#")
LBD = Namespace("https://linkedbuildingdata.org/LBD#")
BMO = Namespace("https://w3id.org/digitalconstruction/BuildingMaterials#")
IFCTOLBD = Namespace("https://w3id.org/ifctolbd/mapping#")
BSDD_META = Namespace("https://w3id.org/ifc2lbd/bsdd-meta#")
BSDD_PROP = Namespace("https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/prop/")
OPM = Namespace("https://w3.org/opm#")
SCHEMA = Namespace("http://schema.org/")
QUDT = Namespace("http://qudt.org/schema/qudt/")
UNIT = Namespace("http://qudt.org/vocab/unit/")
UNIT_META = Namespace("https://w3id.org/ifctolbd/unit#")
IFC_OWL = Namespace("https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL#")

#: Element classes that mark a resource as a building product in the LBD output.
ELEMENT_CLASS = BOT.Element

#: Predicates on an element that carry structural/topological or export-specific
#: links rather than IFC attributes or property values.
STRUCTURAL_PREDICATES = {
    BOT.hasSubElement,
    BOT.containsElement,
    BOT.adjacentElement,
    BOT.hasStorey,
    BOT.hasSpace,
    BOT.hasElement,
    BOT.containsZone,
    LBD.hasBoundingBox,
    LBD.hasWireframe,
    LBD.containsInBoundingBox,
    OMG.hasGeometry,
    GEO.hasGeometry,
    IFCTOLBD.materialAssignment,
    BSDD_META.hasPropertySet,
}

#: Suffix of the generated level-1 IFC attribute predicates (AttributeSet.java).
ATTRIBUTE_SUFFIX = "_attribute_simple"

#: Suffix of the generated level-1 IFC property predicates (PropertySet.java).
PROPERTY_SUFFIX = "_property_simple"

#: Namespaces whose predicates hold generated IFC attributes or properties.
GENERATED_PROPERTY_NAMESPACES = (str(PROPS), str(LBD))

#: rdfs:comment prefix written on generated attribute predicates.
ATTRIBUTE_COMMENT_PREFIX = "IFC standard attribute "

#: rdfs:comment pattern written on generated property predicates:
#: "IFC property set <PsetName> property <PropertyName>".
PROPERTY_COMMENT_PREFIX = "IFC property set "
