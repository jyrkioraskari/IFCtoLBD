package de.rwth_aachen.dc.lbd;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.vecmath.Point3d;

import org.bimserver.geometry.Matrix;

/**
 * Backend-neutral triangle mesh for one IFC product.
 * <p>
 * Positions and normals contain three values per vertex, indices contain three
 * values per triangle, and {@code localToWorld} is a column-major 4x4 matrix.
 * Backends that already return world-space vertices use the identity matrix.
 */
public final class TessellatedMesh {
	private static final double[] IDENTITY = {
			1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1
	};

	private final String guid;
	private final double[] positions;
	private final double[] normals;
	private final int[] indices;
	private final List<Material> materials;
	private final int[] materialIndices;
	private final double[] localToWorld;

	public TessellatedMesh(String guid, double[] positions, double[] normals, int[] indices,
			List<Material> materials, int[] materialIndices, double[] localToWorld) {
		this.guid = Objects.requireNonNull(guid, "guid");
		this.positions = Objects.requireNonNull(positions, "positions").clone();
		this.normals = normals == null ? new double[0] : normals.clone();
		this.indices = Objects.requireNonNull(indices, "indices").clone();
		this.materials = List.copyOf(materials == null ? List.of() : materials);
		this.materialIndices = materialIndices == null ? new int[0] : materialIndices.clone();
		this.localToWorld = localToWorld == null ? identityTransform() : localToWorld.clone();
		validate();
	}

	private void validate() {
		if (this.guid.isBlank()) throw new IllegalArgumentException("guid must not be blank");
		if (this.positions.length % 3 != 0) throw new IllegalArgumentException("positions must contain xyz triples");
		if (this.normals.length != 0 && this.normals.length != this.positions.length)
			throw new IllegalArgumentException("normals must be empty or match positions");
		if (this.indices.length % 3 != 0) throw new IllegalArgumentException("indices must contain triangles");
		if (this.localToWorld.length != 16) throw new IllegalArgumentException("localToWorld must contain 16 values");
		if (this.materialIndices.length != 0 && this.materialIndices.length != triangleCount())
			throw new IllegalArgumentException("materialIndices must be empty or contain one value per triangle");
		int vertexCount = vertexCount();
		for (int index : this.indices) {
			if (index < 0 || index >= vertexCount) throw new IllegalArgumentException("mesh index out of range: " + index);
		}
	}

	public String guid() { return this.guid; }
	public double[] positions() { return this.positions.clone(); }
	public double[] normals() { return this.normals.clone(); }
	public int[] indices() { return this.indices.clone(); }
	public List<Material> materials() { return this.materials; }
	public int[] materialIndices() { return this.materialIndices.clone(); }
	public double[] localToWorld() { return this.localToWorld.clone(); }
	public int vertexCount() { return this.positions.length / 3; }
	public int triangleCount() { return this.indices.length / 3; }
	public boolean hasNormals() { return this.normals.length != 0; }

	/** Computes an axis-aligned world-space bounding box. */
	public BoundingBox worldBoundingBox() {
		if (this.positions.length == 0) return null;
		BoundingBox result = new BoundingBox();
		double[] transformed = new double[4];
		for (int i = 0; i < this.positions.length; i += 3) {
			Matrix.multiplyMV(transformed, 0, this.localToWorld, 0,
					new double[] { this.positions[i], this.positions[i + 1], this.positions[i + 2], 1 }, 0);
			result.add(new Point3d(transformed[0], transformed[1], transformed[2]));
		}
		return result;
	}

	public static double[] identityTransform() { return IDENTITY.clone(); }

	/** Simple render material. Color channels and alpha are in the range 0..1. */
	public static final class Material {
		private final String name;
		private final double[] diffuse;
		private final double alpha;

		public Material(String name, double[] diffuse, double alpha) {
			this.name = Objects.requireNonNull(name, "name");
			this.diffuse = Objects.requireNonNull(diffuse, "diffuse").clone();
			if (this.diffuse.length != 3) throw new IllegalArgumentException("diffuse must contain rgb values");
			this.alpha = alpha;
		}

		public String name() { return this.name; }
		public double[] diffuse() { return this.diffuse.clone(); }
		public double alpha() { return this.alpha; }

		@Override public boolean equals(Object other) {
			if (!(other instanceof Material material)) return false;
			return this.name.equals(material.name) && Arrays.equals(this.diffuse, material.diffuse)
					&& Double.doubleToLongBits(this.alpha) == Double.doubleToLongBits(material.alpha);
		}

		@Override public int hashCode() {
			return 31 * (31 * this.name.hashCode() + Arrays.hashCode(this.diffuse)) + Double.hashCode(this.alpha);
		}
	}
}
