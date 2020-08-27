package org.lbd.ifc2lbd.application_messaging;

import com.google.common.eventbus.EventBus;

/*
 * The GNU Affero General Public License
 * 
 * Copyright (c) 2018 Jyrki Oraskari (Jyrki.Oraskari@aalto.fi / rkiorri@gmail.com)
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


public class IFC2LBD_ApplicationEventBusService {
	private static final EventBus eventBus = new EventBus();

	public static EventBus getEventBus() {
		return eventBus;
	}
}
