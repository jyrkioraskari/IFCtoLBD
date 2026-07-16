
## IFCtoLBD Python Implementation

The example source code can be found in the IFCtoLBD_Python subfolder


### List Subjects

```
# !/usr/bin/env python3

import jpype

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")

# Convert the IFC file into LBD level 3 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  3)

model=lbdconverter.convert("Duplex_A_20110505.ifc");
statements=model.listStatements();


# List all suvjects of the statements in the LBD Model
while statements.hasNext() :
        triple=statements.next()
        subject=triple.getSubject().toString()
        predicate=triple.getPredicate().toString()
        object=triple.getObject().toString()
        print(subject)

#model.write(jpype.java.lang.System.out)

jpype.shutdownJVM()


```


### SPARQL query

```
# !/usr/bin/env python3

import jpype

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
QueryFactory= jpype.JClass("org.apache.jena.query.QueryFactory")
QueryExecutionFactory= jpype.JClass("org.apache.jena.query.QueryExecutionFactory")

# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

model=lbdconverter.convert("Duplex_A_20110505.ifc")
queryString = """PREFIX bot: <https://w3id.org/bot#>

SELECT ?building ?predicate ?object
WHERE {
   ?building a bot:Building .
   ?building ?predicate ?object
}"""

query = QueryFactory.create(queryString)
qexec = QueryExecutionFactory.create(query, model)
results = qexec.execSelect()
while results.hasNext() :
    soln = results.nextSolution()
    x = soln.get("building")
    print(x)

jpype.shutdownJVM()


```


### Get  elements and their geometry as an object array

```
# !/usr/bin/env python3

import jpype
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")

#-------------------------------------------------------------------------------
# Name:        Direct access as Python objects
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     29/01/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


props = ConversionProperties();
props.setHasGeometry(True);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_json = str(lbdconverter.getObjJSON("""
                    PREFIX bot: <https://w3id.org/bot#>
                    PREFIX fog: <https://w3id.org/fog#>

                    JSON { 'element' : ?element,'obj': ?obj } WHERE {
                      ?element a bot:Element .
                      ?element <https://w3id.org/omg#hasGeometry> ?g .
                      ?g fog:asObj_v3.0-obj ?obj
                    }"""))
lbd_objs = json.loads(lbd_json);

for o in lbd_objs:
  print(o["element"])
  print(o["obj"])   # base64 obj
jpype.shutdownJVM()

```
### How to list door elements using rdflib

```
# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")


#-------------------------------------------------------------------------------
# Name:        RDFLib access
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     29/01/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


props = ConversionProperties();
props.setHasGeometry(True);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

q = """
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>

SELECT ?element WHERE {
    ?element a beo:Door.
}"""

# Apply the query to the graph and iterate through results
for r in g.query(q):
    print(r["element"])

jpype.shutdownJVM()

```

### How to replace property URIs using aJSON map

```
# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")


#-------------------------------------------------------------------------------
# Name:        RDFLib Query with URI replace
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     02/02/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


props = ConversionProperties();
props.setHasGeometry(False);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

map="""
{
   "http://lbd.arch.rwth-aachen.de/props#globalIdIfcRoot_attribute_simple": "https://my.org/guid"
}
"""

lbdconverter.setProperty_replace_map(map)

lbdconverter.convert("Duplex_A_20110505.ifc",props)


lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

q = """
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>

SELECT ?element ?guid WHERE {
    ?element <https://my.org/guid> ?guid .
}"""

# Apply the query to the graph and iterate through results
for r in g.query(q):
    print(r["element"]," guid ",r["guid"])

if jpype.isJVMStarted():
            jpype.shutdownJVM()



```


### How to create a 3D visualization

```
# !/usr/bin/env python3
#  To install:   pip install open3d


#-------------------------------------------------------------------------------
# Name:        lbd 3D visualization
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     26/01/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


import open3d as o3d
import open3d.visualization as viss
import base64
import tempfile
import jpype
import numpy as np
from open3d.cpu.pybind.visualization import MeshColorOption

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
QueryFactory= jpype.JClass("org.apache.jena.query.QueryFactory")
QueryExecutionFactory= jpype.JClass("org.apache.jena.query.QueryExecutionFactory")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")


# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)
props = ConversionProperties();
props.setHasGeometry(True);

model=lbdconverter.convert("Duplex_A_20110505.ifc",props)
queryString = """PREFIX fog: <https://w3id.org/fog#>
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>
PREFIX bot: <https://w3id.org/bot#>
PREFIX ifc: <https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL#>

SELECT ?e ?wkt ?obj WHERE {
  ?e <https://w3id.org/omg#hasGeometry> ?g .
  ?e a beo:Wall .\r
  ?g fog:asObj_v3.0-obj ?obj
  FILTER NOT EXISTS {
    ?e a beo:Window .
  }
  FILTER NOT EXISTS {
    ?e a  bot:Space .
  }
  FILTER NOT EXISTS {
    ?e a   ifc:IfcOpeningElement .
  }


} """

query = QueryFactory.create(queryString)
qexec = QueryExecutionFactory.create(query, model)
results = qexec.execSelect()


while results.hasNext() :
    soln = results.nextSolution()
    x = soln.get("obj")
    print(x)
    base64_bytes = str(x.asLiteral().getLexicalForm()).encode("ascii")
    decoded_bytes = base64.b64decode(base64_bytes)
    decoded_string = decoded_bytes.decode("ascii")
    print(decoded_string)

    virtual_file = tempfile.NamedTemporaryFile(suffix='.obj',mode='w',delete=False)
    print(virtual_file.name)

    virtual_file.write(decoded_string)
    virtual_file.close()

    try:
        mesh = mesh + o3d.io.read_triangle_mesh(virtual_file.name,True,True)
    except NameError:
        mesh = o3d.io.read_triangle_mesh(virtual_file.name,True,True)
#R = np.array([[1, 0, 0], [0, 1, 0], [0, 0, 1]])
R = o3d.geometry.get_rotation_matrix_from_axis_angle([-np.pi / 2, 0, 0])
mesh.rotate(R, center=(0, 0, 0))

mesh.paint_uniform_color([1, 0.5, 0.5])
mesh.compute_vertex_normals()

mat_mesh = viss.rendering.MaterialRecord()
mat_mesh.shader = 'defaultLit'
mat_mesh.base_color = [1, 0.8, 0.8, 0.5]
geoms = [{'name': 'mesh', 'geometry': mesh, 'material': mat_mesh}]
viss.draw(geoms)
jpype.shutdownJVM()


```


### How to draw the graph

```
# !/usr/bin/env python3
# pip install matplotlib

import jpype
from rdflib import Graph
import json

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")

import io
import numpy as np
import pydotplus
from IPython.display import display
from rdflib.tools.rdf2dot import rdf2dot
import matplotlib.pyplot as plt
from PIL import Image


#-------------------------------------------------------------------------------
# Name:        RDFLib Draw Graph
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     30/01/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


props = ConversionProperties()
props.setHasGeometry(False)
props.setHasBuildingProperties(False)
props.setHasBuildingElements(False)
props.setHasUnits(False)
props.setHasNonLBDElement(False)
props.setExportIfcOWL(False)

# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.domain.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

stream = io.StringIO()
rdf2dot(g, stream, opts = {display})
dg = pydotplus.graph_from_dot_data(stream.getvalue())
png_data=dg.create_png()


img = io.BytesIO(png_data)
img_pil = Image.open(img)
img_array = np.array(img_pil)
plt.imshow(img_array)
plt.show()
```


### How to select element types and conversion parameters

```
# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json
# Enable Java imports
import jpype.imports
# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")

#-------------------------------------------------------------------------------
# Name:        RDFLib access
# Purpose:
#
# Author:      Jyrki Oraskari
#
# Created:     13/03/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------

# ConversionProperties:
# setHasBuildingElements(boolean)             	If set False, no building elements are included
# setHasSeparateBuildingElementsModel(boolean)  If True, a separate file for the building elements is created
# setHasBuildingProperties(boolean)      	If false, property sets are not included
# setHasSeparatePropertiesModel(boolean)  If true, properties are written into a separate file
# setHasGeolocation(boolean)    		If geolocation is included
# setHasGeometry(boolean)       		If geometry is included
# setExportIfcOWL(boolean)       		If the BOT elements are linked with ifcOWL elements and ifcOWL graph is created into a separate file
# setHasUnits(boolean)                	If the units for the values are included  (Level 2 ja Level 3 )
# setHasBoundingBoxWKT(boolean)		If the bounding boxes of the element are expressed as Well Known Text
# setHasHierarchicalNaming(boolean)		Affects how the URLs are formed
# setHasPerformanceBoost(boolean)		If set True, the converter tries to use conversion results of previous conversions and  filters out 
#  							data that is not used in the calculation later
props = ConversionProperties()
props.setHasGeometry(True)
props.setExportIfcOWL(False)
props.setHasPerformanceBoost(False)

# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://www.econom.one/",  1)

selected_types = "[\"Beam\", \"Wall\", \"Member\", \"Slab\", \"Stair\", \"Railing\"]"
lbdconverter.setSelected_types(selected_types)		# Exported elements can be selected using the JSON list syntax.

lbdconverter.setHasSimplified_properties(True)		# More clear naming,  but not recommended if rules or inferencing models are used (OWL full)
lbdconverter.setHasNonLBDElement(False)			#  Filters out IFC elements not specified in Linked Building Data ontologies.

# Model to be read and selected properties
lbdconverter.convert("AC20-FZK-Haus.ifc",props)

lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

#  Lists the triples:
print(g.serialize(format="turtle"))

jpype.shutdownJVM()

```

### How to list properties for each element

```
# !/usr/bin/env python3

import jpype
from rdflib import Graph
import json
from urllib.parse import urlparse

# Enable Java imports
import jpype.imports

# Pull in types
from jpype.types import *

jpype.startJVM(classpath = ['jars/*'])

IFCtoLBDConverter = jpype.JClass("org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter")
ConversionProperties = jpype.JClass("org.linkedbuildingdata.ifc2lbd.ConversionProperties")


#-------------------------------------------------------------------------------
# Name:        RDFLib Properties Listing
# Purpose:     Compatible version
#
# Author:      Jyrki Oraskari
#
# Created:     06/01/2025
# Copyright:   (c) Jyrki Oraskari 2025
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


def remove_property_simple_suffix(text):
    suffix = "_property_simple"
    if text.endswith(suffix):
        return text[:-len(suffix)]
    return text


props = ConversionProperties();
props.setHasGeometry(False);
# Convert the IFC file into LBD, OPM level 1 model
lbdconverter = IFCtoLBDConverter("https://example.de/",  1)

lbdconverter.convert("Duplex_A_20110505.ifc",props)
lbd_jsonld = str(lbdconverter.getJSONLD())
g = Graph()
g.parse(data=json.loads(lbd_jsonld), format='json-ld')

q = """
PREFIX bot: <https://w3id.org/bot#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX props: <http://lbd.arch.rwth-aachen.de/props#>

SELECT ?element ?guid ?property ?value WHERE {
   ?element rdf:type bot:Element .
   ?element ?predicate ?value .
   ?element  props:globalIdIfcRoot_attribute_simple ?guid .
   BIND(STRAFTER(STR(?predicate), "#") AS ?property)
   FILTER STRENDS(STR(?predicate), "_property_simple")
}"""

element_map = {}


# Apply the query to the graph and iterate through results
for r in g.query(q):
    element_map.setdefault(r["guid"], {})
    element =element_map[r["guid"]]
    element[remove_property_simple_suffix(r["property"])] = r["value"]

for key, values in element_map.items():
    print(f"\nElement: {key}")
    for props_key, props_value in values.items():
        print(f"-    {props_key}: {props_value}")


jpype.shutdownJVM()
```





