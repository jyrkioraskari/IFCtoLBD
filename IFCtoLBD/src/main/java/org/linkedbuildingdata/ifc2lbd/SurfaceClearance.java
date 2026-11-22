package org.linkedbuildingdata.ifc2lbd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.linkedbuildingdata.ifc2lbd.namespace.IFCtoLBDMapping;

import de.rwth_aachen.dc.lbd.TessellatedMesh;

/** Minimum triangle-surface distance and solid-intersection check for two selected products. */
public final class SurfaceClearance {
	public enum Status { SEPARATED, CONTACT, PENETRATION, INDETERMINATE, UNAVAILABLE }
	public record Element(String modelScope, String iri, String revision, String guid) {
		public Element {
			if (modelScope == null || modelScope.isBlank() || iri == null || iri.isBlank()
					|| revision == null || revision.isBlank() || guid == null || guid.isBlank())
				throw new IllegalArgumentException("Element needs model scope, IRI, revision, and IFC GlobalId");
		}
	}
	/** Column-major transform from the second model's world coordinates into the first model's. */
	public record Alignment(double[] secondToFirst) {
		public Alignment { secondToFirst = secondToFirst.clone();
			if (secondToFirst.length != 16 || java.util.Arrays.stream(secondToFirst).anyMatch(v -> !Double.isFinite(v))
					|| Math.abs(secondToFirst[3]) > 1e-12 || Math.abs(secondToFirst[7]) > 1e-12
					|| Math.abs(secondToFirst[11]) > 1e-12 || Math.abs(secondToFirst[15]-1) > 1e-12)
				throw new IllegalArgumentException("Alignment needs a finite affine 4x4 matrix"); }
		@Override public double[] secondToFirst() { return secondToFirst.clone(); }
		public static Alignment identity() { return new Alignment(TessellatedMesh.identityTransform()); }
	}
	public record Result(Element first, Element second, Double distanceMetres, Status status,
			double toleranceMetres, String method, String limitation) {
		public String unit() { return "m"; }
		/** Companion RDF, separate from ordinary conversion output. Caller closes the model. */
		public Model toModel() {
			Model model = ModelFactory.createDefaultModel();
			String ns = IFCtoLBDMapping.ns;
			model.setNsPrefix("ifctolbd", ns);
			Resource result = model.createResource("urn:ifctolbd:clearance:" + hash(
					first.modelScope() + "|" + first.iri() + "|" + first.revision() + "|"
							+ second.modelScope() + "|" + second.iri() + "|" + second.revision()))
					.addProperty(RDF.type, model.createResource(ns + "ClearanceMeasurement"))
					.addProperty(model.createProperty(ns + "firstElement"), model.createResource(first.iri()))
					.addProperty(model.createProperty(ns + "secondElement"), model.createResource(second.iri()))
					.addLiteral(model.createProperty(ns + "firstModelScope"), first.modelScope())
					.addLiteral(model.createProperty(ns + "secondModelScope"), second.modelScope())
					.addLiteral(model.createProperty(ns + "firstRevision"), first.revision())
					.addLiteral(model.createProperty(ns + "secondRevision"), second.revision())
					.addLiteral(model.createProperty(ns + "clearanceMethod"), method)
					.addLiteral(model.createProperty(ns + "clearanceStatus"), status.name().toLowerCase())
					.addProperty(model.createProperty(ns + "distanceUnit"),
						model.createResource("http://qudt.org/vocab/unit/M"))
					.addLiteral(model.createProperty(ns + "toleranceMetres"), toleranceMetres);
			if (distanceMetres != null) result.addLiteral(model.createProperty(ns + "distanceMetres"),
					model.createTypedLiteral(distanceMetres, XSD.xdouble.getURI()));
			if (limitation != null) result.addLiteral(model.createProperty(ns + "limitation"), limitation);
			return model;
		}
	}

	/** Uses the deterministic geometry provider's meshes; caller owns both GeometryResult objects. */
	public Result measure(Element first, GeometryResult firstGeometry, Element second,
			GeometryResult secondGeometry, Alignment alignment, double toleranceMetres) {
		java.util.Objects.requireNonNull(first, "first");
		java.util.Objects.requireNonNull(second, "second");
		if (!Double.isFinite(toleranceMetres) || toleranceMetres < 0)
			throw new IllegalArgumentException("toleranceMetres must be finite and nonnegative");
		if (firstGeometry == null || secondGeometry == null
				|| alignment == null) return unavailable(first, second, toleranceMetres, "Missing selection, geometry, or model alignment");
		TessellatedMesh a = firstGeometry.getTessellatedMesh(first.guid());
		TessellatedMesh b = secondGeometry.getTessellatedMesh(second.guid());
		return measureMeshes(first, a, firstGeometry.metadata(), second, b,
				secondGeometry.metadata(), alignment, toleranceMetres);
	}

	/** Same measurement for meshes already returned by a deterministic geometry provider. */
	public Result measureMeshes(Element first, TessellatedMesh a, GeometryResult.Metadata firstMetadata,
			Element second, TessellatedMesh b, GeometryResult.Metadata secondMetadata,
			Alignment alignment, double toleranceMetres) {
		java.util.Objects.requireNonNull(first, "first");
		java.util.Objects.requireNonNull(second, "second");
		if (!Double.isFinite(toleranceMetres) || toleranceMetres < 0)
			throw new IllegalArgumentException("toleranceMetres must be finite and nonnegative");
		if (alignment == null || firstMetadata == null || secondMetadata == null)
			return unavailable(first, second, toleranceMetres, "Missing selection, metadata, or model alignment");
		if (a == null || b == null || a.triangleCount() == 0 || b.triangleCount() == 0)
			return unavailable(first, second, toleranceMetres, "No triangle mesh for one or both elements");
		double aUnit = firstMetadata.unitToMetres();
		double bUnit = secondMetadata.unitToMetres();
		if (!Double.isFinite(aUnit) || aUnit <= 0 || !Double.isFinite(bUnit) || bUnit <= 0)
			return unavailable(first, second, toleranceMetres, "Geometry length unit is unresolved");
		if (firstMetadata.coordinateSpace() != secondMetadata.coordinateSpace())
			return unavailable(first, second, toleranceMetres, "Geometry coordinate spaces differ");
		Mesh left = mesh(a, aUnit, TessellatedMesh.identityTransform());
		Mesh right = mesh(b, bUnit, alignment.secondToFirst());
		if (left == null || right == null)
			return unavailable(first, second, toleranceMetres, "Non-finite or degenerate triangle geometry");
		double minimum = Double.POSITIVE_INFINITY;
		boolean crosses = false;
		for (int[] ai : left.triangles) for (int[] bi : right.triangles) {
			Vec[] at = triangle(left, ai), bt = triangle(right, bi);
			minimum = Math.min(minimum, triangleDistance(at, bt));
			if (properCrossing(at, bt) || properCrossing(bt, at)) crosses = true;
		}
		boolean closed = watertight(left) && watertight(right);
		String limitation = closed ? null : "Open or non-manifold mesh: solid penetration is indeterminate";
		Status status;
		if (!closed) status = minimum > toleranceMetres ? Status.SEPARATED : Status.INDETERMINATE;
		else if (crosses || inside(left.vertices[left.triangles[0][0]], right)
				|| inside(right.vertices[right.triangles[0][0]], left)) status = Status.PENETRATION;
		else status = minimum <= toleranceMetres ? Status.CONTACT : Status.SEPARATED;
		return new Result(first, second, minimum, status, toleranceMetres,
				"triangle-surface-distance-and-closed-solid-test", limitation);
	}

	private static Result unavailable(Element a, Element b, double tolerance, String why) {
		return new Result(a, b, null, Status.UNAVAILABLE, tolerance,
				"triangle-surface-distance-and-closed-solid-test", why);
	}
	private record Vec(double x, double y, double z) {
		Vec add(Vec b) { return new Vec(x+b.x, y+b.y, z+b.z); }
		Vec sub(Vec b) { return new Vec(x-b.x, y-b.y, z-b.z); }
		Vec mul(double s) { return new Vec(x*s, y*s, z*s); }
		double dot(Vec b) { return x*b.x+y*b.y+z*b.z; }
		Vec cross(Vec b) { return new Vec(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x); }
		double norm2() { return dot(this); }
	}
	private record Mesh(Vec[] vertices, int[][] triangles) { }
	private static Mesh mesh(TessellatedMesh source, double unit, double[] alignment) {
		double[] positions = source.positions(), local = source.localToWorld();
		Vec[] vertices = new Vec[positions.length/3];
		for (int i=0; i<vertices.length; i++) {
			Vec point = transform(local, new Vec(positions[3*i], positions[3*i+1], positions[3*i+2]));
			point = transform(alignment, point.mul(unit));
			if (!Double.isFinite(point.norm2())) return null;
			vertices[i] = point;
		}
		int[] indices = source.indices();
		int[][] triangles = new int[indices.length/3][3];
		for (int i=0; i<triangles.length; i++) {
			triangles[i] = new int[]{indices[3*i], indices[3*i+1], indices[3*i+2]};
			Vec[] t = triangle(new Mesh(vertices, triangles), triangles[i]);
			if (t[1].sub(t[0]).cross(t[2].sub(t[0])).norm2() < 1e-24) return null;
		}
		return new Mesh(vertices, triangles);
	}
	private static Vec transform(double[] m, Vec p) {
		return new Vec(m[0]*p.x+m[4]*p.y+m[8]*p.z+m[12],
				m[1]*p.x+m[5]*p.y+m[9]*p.z+m[13], m[2]*p.x+m[6]*p.y+m[10]*p.z+m[14]);
	}
	private static Vec[] triangle(Mesh mesh, int[] ids) {
		return new Vec[]{mesh.vertices[ids[0]], mesh.vertices[ids[1]], mesh.vertices[ids[2]]};
	}
	private static double triangleDistance(Vec[] a, Vec[] b) {
		double min = Double.POSITIVE_INFINITY;
		for (Vec p : a) min = Math.min(min, pointTriangle(p,b));
		for (Vec p : b) min = Math.min(min, pointTriangle(p,a));
		for (int i=0;i<3;i++) for (int j=0;j<3;j++)
			min = Math.min(min, segmentDistance(a[i],a[(i+1)%3],b[j],b[(j+1)%3]));
		if (intersects(a,b) || intersects(b,a)) return 0;
		return min;
	}
	private static double pointTriangle(Vec p, Vec[] t) {
		Vec a=t[0], ab=t[1].sub(a), ac=t[2].sub(a), ap=p.sub(a);
		double d1=ab.dot(ap), d2=ac.dot(ap);
		if (d1<=0 && d2<=0) return Math.sqrt(ap.norm2());
		Vec bp=p.sub(t[1]); double d3=ab.dot(bp), d4=ac.dot(bp);
		if (d3>=0 && d4<=d3) return Math.sqrt(bp.norm2());
		double vc=d1*d4-d3*d2;
		if (vc<=0 && d1>=0 && d3<=0) return Math.sqrt(p.sub(a.add(ab.mul(d1/(d1-d3)))).norm2());
		Vec cp=p.sub(t[2]); double d5=ab.dot(cp), d6=ac.dot(cp);
		if (d6>=0 && d5<=d6) return Math.sqrt(cp.norm2());
		double vb=d5*d2-d1*d6;
		if (vb<=0 && d2>=0 && d6<=0) return Math.sqrt(p.sub(a.add(ac.mul(d2/(d2-d6)))).norm2());
		double va=d3*d6-d5*d4;
		if (va<=0 && d4-d3>=0 && d5-d6>=0)
			return Math.sqrt(p.sub(t[1].add(t[2].sub(t[1]).mul((d4-d3)/((d4-d3)+(d5-d6))))).norm2());
		Vec n=ab.cross(ac); return Math.abs(ap.dot(n))/Math.sqrt(n.norm2());
	}
	private static double segmentDistance(Vec p1, Vec q1, Vec p2, Vec q2) {
		Vec d1=q1.sub(p1), d2=q2.sub(p2), r=p1.sub(p2);
		double a=d1.norm2(), e=d2.norm2(), f=d2.dot(r), s, t;
		if (a<1e-24 && e<1e-24) return Math.sqrt(r.norm2());
		if (a<1e-24) { s=0; t=clamp(f/e); }
		else {
			double c=d1.dot(r);
			if (e<1e-24) { t=0; s=clamp(-c/a); }
			else {
				double b=d1.dot(d2), denom=a*e-b*b;
				s=denom==0 ? 0 : clamp((b*f-c*e)/denom);
				t=(b*s+f)/e;
				if (t<0) {t=0; s=clamp(-c/a);} else if (t>1) {t=1; s=clamp((b-c)/a);}
			}
		}
		return Math.sqrt(p1.add(d1.mul(s)).sub(p2.add(d2.mul(t))).norm2());
	}
	private static double clamp(double x) { return Math.max(0, Math.min(1,x)); }
	private static boolean intersects(Vec[] from, Vec[] target) {
		for (int i=0;i<3;i++) if (rayTriangle(from[i],from[(i+1)%3].sub(from[i]),target,1) != null)
			return true;
		return false;
	}
	private static boolean properCrossing(Vec[] from, Vec[] target) {
		for (int i=0;i<3;i++) {
			Double t=rayTriangle(from[i],from[(i+1)%3].sub(from[i]),target,1);
			if (t!=null && t>1e-9 && t<1-1e-9) return true;
		}
		return false;
	}
	private static Double rayTriangle(Vec origin, Vec direction, Vec[] t, double max) {
		Vec e1=t[1].sub(t[0]), e2=t[2].sub(t[0]), h=direction.cross(e2);
		double det=e1.dot(h);
		if (Math.abs(det)<1e-12) return null;
		double inv=1/det; Vec s=origin.sub(t[0]); double u=inv*s.dot(h);
		if (u<-1e-10 || u>1+1e-10) return null;
		Vec q=s.cross(e1); double v=inv*direction.dot(q);
		if (v<-1e-10 || u+v>1+1e-10) return null;
		double distance=inv*e2.dot(q);
		return distance>=-1e-10 && distance<=max+1e-10 ? distance : null;
	}
	private static boolean inside(Vec point, Mesh mesh) {
		Vec direction=new Vec(1,0.17320508075688773,0.1131370849898476);
		int hits=0;
		for (int[] ids : mesh.triangles) {
			Double t=rayTriangle(point,direction,triangle(mesh,ids),Double.POSITIVE_INFINITY);
			if (t!=null && t>1e-9) hits++;
		}
		return hits%2==1;
	}
	private static boolean watertight(Mesh mesh) {
		Map<Long,Integer> edges=new HashMap<>();
		for (int[] t : mesh.triangles) for (int i=0;i<3;i++) {
			int a=Math.min(t[i],t[(i+1)%3]), b=Math.max(t[i],t[(i+1)%3]);
			long key=((long)a<<32)|(b&0xffffffffL); edges.merge(key,1,Integer::sum);
		}
		return !edges.isEmpty() && edges.values().stream().allMatch(count -> count==2);
	}
	private static String hash(String value) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0,24); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}
