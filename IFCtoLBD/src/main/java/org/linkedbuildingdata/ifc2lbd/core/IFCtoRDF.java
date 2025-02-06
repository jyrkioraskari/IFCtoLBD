package org.linkedbuildingdata.ifc2lbd.core;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import be.ugent.IfcSpfReader;

public class IFCtoRDF extends IfcSpfReader {
    protected final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
    private static final Logger LOG = LoggerFactory.getLogger(IFCtoRDF.class);
    private int counter = 0;

    /**
     * Converts IFC file into RDF format.
     *
     * @param ifcFile          Path to the IFC file.
     * @param outputFile       Path to the output RDF file.
     * @param baseURI          Base URI for the RDF.
     * @param hasPerformanceBoost Flag indicating if performance boost is enabled.
     * @return Optional containing the RDF URI if successful, otherwise empty.
     */
    public Optional<String> convert_into_rdf(String ifcFile, String outputFile, String baseURI, boolean hasPerformanceBoost) {
        this.counter = 0;
        PrintStream orgSystemOut = System.out;
        PrintStream orgSystemError = System.err;

        try {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF running  " + counter++));
                }
            }, 1000, 1000);

            setup(ifcFile);
            convert(ifcFile, outputFile, baseURI, hasPerformanceBoost);
            timer.cancel();
        } catch (Exception e) {
            LOG.error("Error during IFC to RDF conversion", e);
            return Optional.empty();
        } finally {
            System.setOut(orgSystemOut);
            System.setErr(orgSystemError);
        }
        return Optional.of(this.ontURI);
    }

    /**
     * Retrieves the ontology URI from an IFC file.
     *
     * @param ifcFile Path to the IFC file.
     * @return Optional containing the ontology URI if successful, otherwise empty.
     */
    public Optional<String> getOntologyURI(String ifcFile) {
        try {
            setup(ifcFile);
        } catch (IOException e) {
            LOG.error("Error during setup of IFC file", e);
            return Optional.empty();
        }
        return Optional.of(this.ontURI);
    }
}