# IFCtoLBD Java distribution

Run `npm run build:converter` from `IFCtoLBD_MCP`. The reactor build writes one
shaded `ifctolbd-converter.jar` here. The MCP server adds
only that JAR to the JVM classpath; loose dependency directories are unsupported.
