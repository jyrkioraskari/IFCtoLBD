package examples;

import java.nio.file.Path;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.linkedbuildingdata.ifc2lbd.ConversionProfiles;
import org.linkedbuildingdata.ifc2lbd.ConversionRequest;
import org.linkedbuildingdata.ifc2lbd.ConversionSession;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.VersionedClassificationResolverCache;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** Converts an IFC model with optional, cached ETIM enrichment and prints the resulting evidence. */
public final class Example15 {
	private Example15() { }

	public static void main(String[] args) throws Exception {
		if (args.length != 1) throw new IllegalArgumentException("Usage: Example15 <model.ifc>");

		var resolver = new VersionedClassificationResolverCache(
				Path.of(".ifctolbd", "classification-cache"), "etim-10-mc-2025");
		var request = new ConversionRequest(Path.of(args[0]).toAbsolutePath().toString(),
				ConversionProfiles.SUPPLY_CHAIN)
				.withClassificationResolver(resolver)
				.withEtimClassificationSuggestions();

		try (var session = new ConversionSession();
				var converter = new IFCtoLBDConverter(session, "https://example.com/building/");
				var result = converter.convert(request)) {
			String sparql = """
					PREFIX supply: <https://w3id.org/ifctolbd/supply-chain#>
					SELECT ?element ?status ?concept ?evidence ?property ?value WHERE {
					  {
					    ?element supply:hasClassification ?classification .
					    ?classification supply:classificationStatus ?status .
					    OPTIONAL { ?classification supply:resolvedConcept ?concept }
					  }
					  UNION
					  {
					    ?element supply:hasClassificationSuggestion ?suggestion .
					    ?suggestion supply:classificationStatus ?status ;
					                supply:suggestedConcept ?concept .
					    OPTIONAL { ?suggestion supply:evidence ?evidence }
					  }
					  OPTIONAL {
					    ?element supply:hasPropertyAnnotation ?annotation .
					    ?annotation supply:property ?property ; supply:observedValue ?value .
					  }
					}
					ORDER BY ?element ?status ?property
					""";
			try (var execution = QueryExecutionFactory.create(sparql, result.getModel())) {
				ResultSetFormatter.out(System.out, execution.execSelect());
			}
			RDFDataMgr.write(System.out, result.getModel(), RDFFormat.TURTLE_PRETTY);
		}
	}
}
