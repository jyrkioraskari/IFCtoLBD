# !/usr/bin/env python3
#  To install:   pip install open3d


#-------------------------------------------------------------------------------
# Name:        lbd 3D visualization for an interface
# Purpose:
# This is demonstrating how interfaces can be used to find doors between
# spaces.
#
# Author:      Jyrki Oraskari
#
# Created:     20/09/2024
# Copyright:   (c) Jyrki Oraskari 2024
# Licence:     Apache 2.0
#-------------------------------------------------------------------------------


import open3d as o3d
import open3d.visualization as viss
import base64
import tempfile
import jpype
import numpy as np

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
props.setHasInterfaces(True);

model=lbdconverter.convert("Duplex_A_20110505.ifc",props)
queryString = """
PREFIX fog: <https://w3id.org/fog#>
PREFIX beo: <https://pi.pauwel.be/voc/buildingelement#>
PREFIX bot: <https://w3id.org/bot#>
PREFIX ifc: <https://standards.buildingsmart.org/IFC/DEV/IFC2x3/TC1/OWL#>

SELECT ?obj1 ?obj2 WHERE {
  {
    SELECT ?e1 ?obj1
   WHERE {
      ?e1 a beo:Door .
      ?e1 <https://w3id.org/omg#hasGeometry> ?g1 .
      ?g1 fog:asObj_v3.0-obj ?obj1 .
    }
    LIMIT 1
  }
  ?i bot:interfaceOf ?e1 .
  ?i bot:interfaceOf ?e2 .
  ?e2 a bot:Space .
  ?e2 <https://w3id.org/omg#hasGeometry> ?g2 .
  ?g2 fog:asObj_v3.0-obj ?obj2 .
  FILTER (lcase(str(?obj1)) != lcase(str(?obj2)))
}
"""

query = QueryFactory.create(queryString)
qexec = QueryExecutionFactory.create(query, model)
results = qexec.execSelect()

# Create a material with transparency
material = o3d.visualization.rendering.MaterialRecord()
material.base_color = [0.5, 0.5, 0.5, 0.5]  # RGBA values, where A is the alpha (transparency)

while results.hasNext() :
    soln = results.nextSolution()
    x1 = soln.get("obj1")
    print(x1)
    base64_bytes1 = str(x1.asLiteral().getLexicalForm()).encode("ascii")
    decoded_bytes1 = base64.b64decode(base64_bytes1)
    decoded_string1 = decoded_bytes1.decode("ascii")
    print(decoded_string1)

    virtual_file1 = tempfile.NamedTemporaryFile(suffix='.obj',mode='w',delete=False)
    print(virtual_file1.name)

    virtual_file1.write(decoded_string1)
    virtual_file1.close()

    door_mesh =  o3d.io.read_triangle_mesh(virtual_file1.name,True,True)
    door_mesh.paint_uniform_color([1.0, 0.0, 0.0])
    try:
        mesh_doors = mesh_doors + door_mesh
    except NameError:
        mesh_doors = door_mesh


    x2 = soln.get("obj2")
    print(x2)
    base64_bytes2 = str(x2.asLiteral().getLexicalForm()).encode("ascii")
    decoded_bytes2 = base64.b64decode(base64_bytes2)
    decoded_string2 = decoded_bytes2.decode("ascii")
    print(decoded_string2)

    virtual_file2 = tempfile.NamedTemporaryFile(suffix='.obj',mode='w',delete=False)
    print(virtual_file2.name)

    virtual_file2.write(decoded_string2)
    virtual_file2.close()


    space_mesh =  o3d.io.read_triangle_mesh(virtual_file2.name,True,True)
    space_mesh.paint_uniform_color([0.0, 0.0, 1.0])

    try:
        mesh_spaces = mesh_spaces + space_mesh
    except NameError:
        mesh_spaces = space_mesh


R = o3d.geometry.get_rotation_matrix_from_axis_angle([-np.pi / 2, 0, 0])

mesh_doors.rotate(R, center=(0, 0, 0))
mesh_spaces.rotate(R, center=(0, 0, 0))

#mesh.paint_uniform_color([1, 0.5, 0.5])
mesh_doors.compute_vertex_normals()
mesh_spaces.compute_vertex_normals()


mat_door = viss.rendering.MaterialRecord()
mat_door.shader = 'defaultLit'
mat_door.base_color = [1, 0.8, 0.8, 0.5]

mat_space = viss.rendering.MaterialRecord()
mat_space.shader = 'defaultLitSSR'
mat_space.base_color = [0, 0, 1., 0.1]
mat_space.base_roughness = 0.0
mat_space.base_reflectance = 0.0
mat_space.base_clearcoat = 1.0
mat_space.thickness = 1.0
mat_space.transmission = 0.6
mat_space.absorption_distance = 10
mat_space.absorption_color = [0, 0, 1.0]

geoms = [{'name': 'mesh_doors', 'geometry': mesh_doors, 'material': mat_door},{'name': 'mesh_spaces', 'geometry': mesh_spaces, 'material': mat_space}]
viss.draw(geoms)

jpype.shutdownJVM()
