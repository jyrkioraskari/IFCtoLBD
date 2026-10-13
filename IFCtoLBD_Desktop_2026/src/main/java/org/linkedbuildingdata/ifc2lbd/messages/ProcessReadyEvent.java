package org.linkedbuildingdata.ifc2lbd.messages;

/*
 * The GNU Affero General Public License
 * 
 * Copyright (c) 2018 Jyrki Oraskari (Jyrki.Oraskari@aalto.fi / jyrki.oraskari@aalto.fi)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

public class ProcessReadyEvent {
	static public int ERROR=-1;
	static public int READ_IN=0;
	static public int CONVERT=1;

	int phase=READ_IN;
	private final long operationId;
	
	public ProcessReadyEvent() {
		this(READ_IN, -1);
	}
	
	public ProcessReadyEvent(int phase) {
		this(phase, -1);
	}

	public ProcessReadyEvent(int phase, long operationId) {
		this.phase=phase;
		this.operationId = operationId;
	}

	public int getPhase() {
		return phase;
	}

	public long getOperationId() {
		return operationId;
	}


}
