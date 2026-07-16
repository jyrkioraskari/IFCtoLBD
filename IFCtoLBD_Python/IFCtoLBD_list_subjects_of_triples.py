# !/usr/bin/env python3

from IFCtoLBD_wrapper import IFCtoLBDConverter, shutdown_jvm

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



#model.write(...)

shutdown_jvm()

