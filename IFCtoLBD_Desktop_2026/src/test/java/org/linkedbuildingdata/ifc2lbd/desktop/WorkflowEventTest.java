package org.linkedbuildingdata.ifc2lbd.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.linkedbuildingdata.ifc2lbd.messages.ProcessReadyEvent;
import org.linkedbuildingdata.ifc2lbd.messages.WorkflowProgressEvent;

class WorkflowEventTest {
	@Test
	void completionEventCarriesItsOperationIdentity() {
		ProcessReadyEvent event = new ProcessReadyEvent(ProcessReadyEvent.CONVERT, 42L);

		assertEquals(ProcessReadyEvent.CONVERT, event.getPhase());
		assertEquals(42L, event.getOperationId());
	}

	@Test
	void progressAcceptsDeterminateAndIndeterminateValues() {
		assertEquals(0.5, new WorkflowProgressEvent(7L, "Converting", 0.5).progress());
		assertEquals(WorkflowProgressEvent.INDETERMINATE,
				new WorkflowProgressEvent(7L, "Reading", WorkflowProgressEvent.INDETERMINATE).progress());
	}

	@Test
	void progressRejectsInvalidInput() {
		assertThrows(IllegalArgumentException.class, () -> new WorkflowProgressEvent(7L, "", 0.5));
		assertThrows(IllegalArgumentException.class, () -> new WorkflowProgressEvent(7L, "Converting", 1.1));
	}
}
