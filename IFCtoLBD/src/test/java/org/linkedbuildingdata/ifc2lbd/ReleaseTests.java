package org.linkedbuildingdata.ifc2lbd;

import java.util.Comparator;

import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive, offline release gate for the Java converter.
 *
 * <p>This is a native JUnit Platform suite. IDEs therefore display every
 * selected class and test as a child of the suite instead of reporting one
 * launcher-wrapper test. New tests below {@code org.linkedbuildingdata.ifc2lbd}
 * are included automatically.</p>
 *
 * <p>These explicitly opt-in checks are intentionally outside this offline
 * gate:</p>
 * <ul>
 *   <li>{@code BsddLiveCompatibilityTest}: calls the public bSDD service;</li>
 *   <li>{@code ConversionPerformanceBenchmarkTest}: measures performance and
 *       optionally consumes a caller-provided model;</li>
 *   <li>{@code IfcOpenShellStructuredGeometryTest}: requires the separately
 *       distributed ISO specification archive.</li>
 * </ul>
 *
 * <p>Run from the repository root with:</p>
 * <pre>
 * ./mvnw -pl IFCtoLBD -am -Dtest=ReleaseTests \
 *   -Dsurefire.failIfNoSpecifiedTests=false test
 * </pre>
 */
@Suite(failIfNoTests = true)
@SuiteDisplayName("IFCtoLBD offline release requirements")
@SelectPackages("org.linkedbuildingdata.ifc2lbd")
@ExcludeClassNamePatterns({
		".*ReleaseTests",
		".*BsddLiveCompatibilityTest",
		".*ConversionPerformanceBenchmarkTest",
		".*IfcOpenShellStructuredGeometryTest"
})
@ConfigurationParameter(key = "jqwik.tries.default", value = "100")
@ConfigurationParameter(key = "junit.jupiter.testclass.order.default",
		value = "org.linkedbuildingdata.ifc2lbd.ReleaseTests$LegacyCompatibilityFirst")
public final class ReleaseTests {
	private ReleaseTests() {
	}

	/** Keeps the legacy ontology-consistency checks ahead of modern converters
	 * that initialize process-wide Jena compatibility state. */
	public static final class LegacyCompatibilityFirst implements ClassOrderer {
		@Override
		public void orderClasses(ClassOrdererContext context) {
			context.getClassDescriptors().sort(Comparator
					.comparingInt(ReleaseTests.LegacyCompatibilityFirst::priority)
					.thenComparing(descriptor -> descriptor.getTestClass().getName()));
		}

		private static int priority(ClassDescriptor descriptor) {
			return descriptor.getTestClass().equals(ConverterRunsUnitTests.class) ? 0 : 1;
		}
	}
}
