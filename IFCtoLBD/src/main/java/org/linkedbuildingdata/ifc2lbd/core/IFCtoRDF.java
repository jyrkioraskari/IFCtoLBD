package org.linkedbuildingdata.ifc2lbd.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import be.ugent.IfcSpfReader;

public class IFCtoRDF extends IfcSpfReader {
    protected final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
    private static final Logger LOG = LoggerFactory.getLogger(IFCtoRDF.class);
    private static final Object ONTOLOGY_IMPORT_LOCK = new Object();
    private static boolean localOntologyImportsRegistered;
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
        Timer timer = new Timer();

        try {
            registerLocalOntologyImports();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    eventBus.post(new IFCtoLBD_SystemStatusEvent("IFCtoRDF running  " + counter++));
                }
            }, 1000, 1000);

            setup(ifcFile);
            convert(ifcFile, outputFile, baseURI, hasPerformanceBoost);
        } catch (Exception e) {
            LOG.error("Error during IFC to RDF conversion", e);
            return Optional.empty();
        } finally {
            timer.cancel();
            System.setOut(orgSystemOut);
            System.setErr(orgSystemError);
        }
        return Optional.of(this.ontURI);
    }

    private static void registerLocalOntologyImports() {
        synchronized (ONTOLOGY_IMPORT_LOCK) {
            if (localOntologyImportsRegistered)
                return;

            OntDocumentManager documentManager = OntDocumentManager.getInstance();
            registerLocalOntologyImport(documentManager, "https://w3id.org/express", "/express.ttl",
                    "/ifcOWL/express.ttl");
            registerLocalOntologyImport(documentManager, "https://w3id.org/list", "/list.ttl", "/ifcOWL/list.ttl");
            localOntologyImportsRegistered = true;
        }
    }

    private static void registerLocalOntologyImport(OntDocumentManager documentManager, String publicUri,
            String... resourcePaths) {
        Optional<Model> model = readFirstAvailableModel(resourcePaths);
        if (model.isEmpty()) {
            LOG.warn("Local ontology import resource not found for {}", publicUri);
            return;
        }

        documentManager.addModel(publicUri, model.get(), true);
        documentManager.addModel(publicUri + "#", model.get(), true);
    }

    private static Optional<Model> readFirstAvailableModel(String... resourcePaths) {
        for (String resourcePath : resourcePaths) {
            try (InputStream input = IFCtoRDF.class.getResourceAsStream(resourcePath)) {
                if (input == null)
                    continue;

                Model model = ModelFactory.createDefaultModel();
                RDFDataMgr.read(model, input, Lang.TTL);
                return Optional.of(model);
            } catch (IOException | RuntimeException e) {
                LOG.warn("Failed to read local ontology import {}", resourcePath, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves the ontology URI from an IFC file.
     *
     * @param ifcFile Path to the IFC file.
     * @return Optional containing the ontology URI if successful, otherwise empty.
     */
    public Optional<String> getOntologyURI(String ifcFile) {
        try {
            registerLocalOntologyImports();
            setup(ifcFile);
        } catch (IOException e) {
            LOG.error("Error during setup of IFC file", e);
            return Optional.empty();
        }
        return Optional.of(this.ontURI);
    }
}
