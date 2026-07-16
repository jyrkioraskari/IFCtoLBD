package org.linkedbuildingdata.ifc2lbd;

//Add dependency: testImplementation "net.jqwik:jqwik:1.8.2" (example version)
//Then:
import java.nio.file.Path;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

class TwoWallsConversionPropertyTest {
 @Property(seed = "123456789")
 void conversion_never_throws(
         @ForAll @IntRange(min = 0, max = 2) int level,
         @ForAll boolean p1, @ForAll boolean p2, @ForAll boolean p3,
         @ForAll boolean p4, @ForAll boolean p5, @ForAll boolean p6, @ForAll boolean p7,
         @ForAll @IntRange(min = 0, max = 1000) int salt, // just to vary output file name
         @ForAll("tempDir") Path tempDir,
         @ForAll("ifcPath") String ifcPath
 ) throws Exception {
     Path ttl = tempDir.resolve("twowalls-" + level + "-" + salt + ".ttl");
     try (IFCtoLBDConverter c = new IFCtoLBDConverter(
             ifcPath, "https://dot.dc.rwth-aachen.de/IFCtoLBDset#",
             ttl.toAbsolutePath().toString(),
             level, p1, p2, p3, p4, p5, p6, p7)) {
         // No exception is the property
     }
 }

 @Provide
 Arbitrary<Path> tempDir() {
     return Arbitraries.create(() -> {
         try {
             return java.nio.file.Files.createTempDirectory("ifc2lbd-pbt");
         } catch (Exception e) { throw new RuntimeException(e); }
     });
 }

 @Provide
 Arbitrary<String> ifcPath() {
     return Arbitraries.create(() -> {
         try {
             var url = ClassLoader.getSystemResource("TWO WALLS.ifc");
             org.junit.jupiter.api.Assumptions.assumeTrue(url != null);
             return new java.io.File(url.toURI()).getAbsolutePath();
         } catch (Exception e) { throw new RuntimeException(e); }
     });
 }
}