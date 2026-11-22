package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;

class DesktopConverterLifecycleTest {
	@Test
	void loadedConverterIsClosedOnceWhenReplaced() throws Exception {
		IFCtoLBDController controller = new IFCtoLBDController();
		TrackingConverter converter = new TrackingConverter();
		try {
			Field readIn = IFCtoLBDController.class.getDeclaredField("running_read_in");
			readIn.setAccessible(true);
			readIn.set(controller, CompletableFuture.completedFuture(converter));
			Method close = IFCtoLBDController.class.getDeclaredMethod("closeLoadedConverter");
			close.setAccessible(true);
			close.invoke(controller);
			close.invoke(controller);

			assertEquals(1, converter.closeCount);
			assertNull(readIn.get(controller));
		} finally {
			controller.shutdown();
		}
	}

	private static final class TrackingConverter extends IFCtoLBDConverter {
		private int closeCount;

		private TrackingConverter() {
			super("https://example.com/building/", false, 1);
		}

		@Override
		public void close() {
			this.closeCount++;
			super.close();
		}
	}
}
