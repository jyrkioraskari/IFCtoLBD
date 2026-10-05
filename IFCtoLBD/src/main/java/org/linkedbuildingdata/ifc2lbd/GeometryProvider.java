package org.linkedbuildingdata.ifc2lbd;

import java.nio.file.Path;

/** Pluggable source of geometry for one conversion session. */
public interface GeometryProvider extends AutoCloseable {
	String id();
	String version();
	GeometryResult load(Path ifcFile);
	@Override default void close() { }
}
