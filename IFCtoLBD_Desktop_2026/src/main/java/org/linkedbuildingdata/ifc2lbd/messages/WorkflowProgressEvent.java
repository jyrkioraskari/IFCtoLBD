package org.linkedbuildingdata.ifc2lbd.messages;

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

/** Reports a user-visible stage for one desktop read or conversion operation. */
public record WorkflowProgressEvent(long operationId, String stage, double progress) {
	public static final double INDETERMINATE = -1.0;

	public WorkflowProgressEvent {
		if (stage == null || stage.isBlank()) {
			throw new IllegalArgumentException("stage must not be blank");
		}
		if (progress != INDETERMINATE && (progress < 0.0 || progress > 1.0)) {
			throw new IllegalArgumentException("progress must be between 0 and 1, or -1 for indeterminate");
		}
	}
}
