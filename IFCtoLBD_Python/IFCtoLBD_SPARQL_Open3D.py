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

from org.linkedbuildingdata.ifc2lbd import IFCtoLBDConverter
from org.linkedbuildingdata.ifc2lbd import ConversionProperties
from org.apache.jena.query import QueryFactory, QueryExecutionFactory


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
