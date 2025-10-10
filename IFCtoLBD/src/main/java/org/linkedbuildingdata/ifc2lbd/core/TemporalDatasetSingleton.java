package org.linkedbuildingdata.ifc2lbd.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.tdb2.TDB2Factory;

public class TemporalDatasetSingleton {

    // The single instance (volatile for thread safety)
    private static volatile Dataset instance;

    // Private constructor prevents instantiation
    private TemporalDatasetSingleton() {}

    /**
     * Returns the singleton Dataset.
     * This dataset is in-memory only (not persisted).
     */
    public static Dataset getInstance() {
        if (instance == null) {
            synchronized (TemporalDatasetSingleton.class) {
                if (instance == null) {
                	Path tempDir;
					try {
						tempDir = Files.createTempDirectory("IFCtoLBD_");
	        			System.out.println("Temporary directory created at: " + tempDir.toAbsolutePath());
	        			instance = TDB2Factory.connectDataset(tempDir.toAbsolutePath().toString());
					} catch (IOException e) {
						e.printStackTrace();
					}
                }
            }
        }
        return instance;
    }

    public static void main(String[] args) {
        Dataset dataset = TemporalDatasetSingleton.getInstance();

        // Write transaction
        dataset.begin(ReadWrite.WRITE);
        try {
            Model model = dataset.getDefaultModel();
            model.createResource("http://example.org/thing")
                 .addProperty(model.createProperty("http://example.org/name"), "Temporal Singleton");
            dataset.commit();
        } finally {
            dataset.end();
        }

        // Read transaction
        dataset.begin(ReadWrite.READ);
        try {
            dataset.getDefaultModel().listStatements().forEachRemaining(System.out::println);
        } finally {
            dataset.end();
        }
    }
}
