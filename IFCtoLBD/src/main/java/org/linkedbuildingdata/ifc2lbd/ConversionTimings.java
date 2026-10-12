package org.linkedbuildingdata.ifc2lbd;

/** Wall-clock phase timings from the converter's most recent structured request. */
public record ConversionTimings(long coreMillis, long modulesMillis, long validationMillis,
		long auditMillis, long manifestMillis, long totalMillis) { }
