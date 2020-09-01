package org.lbd.ifc2lbd.core;

import org.apache.jena.graph.Triple;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFWrapper;

public class JenaStreamIFCFilter extends StreamRDFWrapper {

    public JenaStreamIFCFilter(StreamRDF other) {
        super(other);
    }

    @Override
    public void triple(Triple triple) {
            other.triple(triple);
    }
}