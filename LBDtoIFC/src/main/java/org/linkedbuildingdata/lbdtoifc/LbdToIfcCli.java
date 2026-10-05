package org.linkedbuildingdata.lbdtoifc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "lbd-to-ifc", mixinStandardHelpOptions = true, version = "LBDtoIFC 0.1.0",
        description = "Reconstruct an IFC4 semantic model from LBD RDF.")
public final class LbdToIfcCli implements Callable<Integer> {
    @Spec
    private CommandSpec commandSpec;

    @Parameters(index = "0", paramLabel = "INPUT",
            description = "LBD input (Turtle, JSON-LD, RDF/XML, N-Triples, or another Jena RDF syntax).")
    private Path input;

    @Parameters(index = "1", paramLabel = "OUTPUT", description = "IFC STEP output file.")
    private Path output;

    @Option(names = "--project-name", description = "IFC project name.",
            defaultValue = "LBD reconstructed project")
    private String projectName;

    @Option(names = {"-f", "--force"}, description = "Replace OUTPUT if it already exists.")
    private boolean force;

    @Option(names = "--artifact-root",
            description = "Directory containing content-addressed OBJ artifacts. Defaults to INPUT's directory.")
    private Path artifactRoot;

    @Override
    public Integer call() {
        if (!Files.isRegularFile(input)) {
            commandSpec.commandLine().getErr().println("Input does not exist or is not a regular file: " + input);
            return 2;
        }
        if (Files.exists(output) && !force) {
            commandSpec.commandLine().getErr()
                    .println("Output already exists (use --force to replace it): " + output);
            return 2;
        }
        try {
            ConversionReport report = new LbdToIfcConverter().convert(input, output,
                    new LbdToIfcConverter.Options(projectName, artifactRoot));
            commandSpec.commandLine().getOut().printf(
                    "Wrote %s: %d site(s), %d building(s), %d storey(s), "
                            + "%d space(s), %d element(s), %d property set(s), "
                            + "%d mesh(es), %d vertices, %d triangles.%n",
                    output, report.sites(), report.buildings(), report.storeys(), report.spaces(),
                    report.elements(), report.propertySets(), report.geometries(),
                    report.geometryVertices(), report.geometryTriangles());
            report.warnings().forEach(warning -> commandSpec.commandLine().getErr()
                    .println("Warning: " + warning));
            return 0;
        } catch (IOException | RuntimeException failure) {
            commandSpec.commandLine().getErr().println("Conversion failed: " + failure.getMessage());
            return 1;
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new LbdToIfcCli()).execute(args));
    }
}
