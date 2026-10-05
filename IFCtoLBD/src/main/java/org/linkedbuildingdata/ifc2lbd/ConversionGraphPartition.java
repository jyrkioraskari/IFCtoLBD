package org.linkedbuildingdata.ifc2lbd;

import java.util.Objects;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/** Creates physically disjoint snapshots of the three conversion payloads. */
public final class ConversionGraphPartition {

	private ConversionGraphPartition() {
	}

	public static Partition copyOf(Model general, Model products, Model properties) {
		Model generalCopy = copy(Objects.requireNonNull(general, "general"));
		Model productCopy = copy(Objects.requireNonNull(products, "products"));
		Model propertyCopy = copy(Objects.requireNonNull(properties, "properties"));
		makeDisjoint(generalCopy, productCopy, propertyCopy);
		return new Partition(generalCopy, productCopy, propertyCopy);
	}

	/**
	 * Applies the canonical precedence: properties, then products, then general.
	 * The supplied models are mutated, so callers should pass snapshots.
	 */
	public static void makeDisjoint(Model general, Model products, Model properties) {
		products.remove(properties);
		general.remove(properties);
		general.remove(products);
	}

	private static Model copy(Model source) {
		Model copy = ModelFactory.createDefaultModel();
		copy.setNsPrefixes(source.getNsPrefixMap());
		copy.add(source);
		return copy;
	}

	public record Partition(Model general, Model products, Model properties) implements AutoCloseable {
		@Override
		public void close() {
			general.close();
			products.close();
			properties.close();
		}
	}
}
