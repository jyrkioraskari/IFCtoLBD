package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import org.junit.jupiter.api.Test;

class SparqlGeometryHighlightTest {
	@Test
	@SuppressWarnings("unchecked")
	void highlightsOnlyLatestUriWithoutRepositioningModel() throws Exception {
		IFCtoLBDController controller = new IFCtoLBDController();
		try {
			MeshView first = new MeshView();
			MeshView second = new MeshView();
			PhongMaterial firstOriginal = new PhongMaterial(Color.BLUE);
			PhongMaterial secondOriginal = new PhongMaterial(Color.GREEN);
			Map<String, List<MeshView>> views = (Map<String, List<MeshView>>) field("geometryViewsByResource").get(controller);
			views.put("urn:first", List.of(first));
			views.put("urn:second", List.of(second));
			Map<MeshView, PhongMaterial> materials = (Map<MeshView, PhongMaterial>) field("geometryMaterials").get(controller);
			materials.put(first, firstOriginal);
			materials.put(second, secondOriginal);
			Group modelRoot = new Group();
			modelRoot.setTranslateX(42);
			field("geometryModelRoot").set(controller, modelRoot);
			Method highlight = IFCtoLBDController.class.getDeclaredMethod("highlightSparqlResource", String.class);
			highlight.setAccessible(true);

			highlight.invoke(controller, "urn:first");
			assertBrightRed((PhongMaterial) first.getMaterial());
			assertSame(secondOriginal, second.getMaterial());
			highlight.invoke(controller, "urn:second");
			assertSame(firstOriginal, first.getMaterial());
			assertBrightRed((PhongMaterial) second.getMaterial());
			assertEquals(42, modelRoot.getTranslateX(), "SPARQL selection must not move the geometry");
		} finally {
			controller.shutdown();
		}
	}

	private static void assertBrightRed(PhongMaterial material) {
		Color color = material.getDiffuseColor();
		assertTrue(color.getRed() > 0.9);
		assertTrue(color.getGreen() < 0.4);
		assertTrue(color.getBlue() < 0.4);
	}

	private static Field field(String name) throws Exception {
		Field field = IFCtoLBDController.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
