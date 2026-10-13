/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.linkedbuildingdata.ifc2lbd.desktop;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** File paths and shape identities selected by the user for later sessions. */
final class RememberedShapes {
	record Entry(String filePath, String shapeNode) { }

	private RememberedShapes() { }

	static List<Entry> read(Preferences preferences) {
		Preferences node = preferences.node("addedShaclShapes");
		int count = Math.max(0, Math.min(node.getInt("count", 0), 10_000));
		List<Entry> entries = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			String path = node.get("file." + i, null);
			String shape = node.get("shape." + i, null);
			if (path != null && shape != null) entries.add(new Entry(path, shape));
		}
		return entries;
	}

	static void write(Preferences preferences, List<Entry> entries) {
		Preferences node = preferences.node("addedShaclShapes");
		int oldCount = node.getInt("count", 0);
		for (int i = 0; i < entries.size(); i++) {
			node.put("file." + i, entries.get(i).filePath());
			node.put("shape." + i, entries.get(i).shapeNode());
		}
		for (int i = entries.size(); i < oldCount; i++) {
			node.remove("file." + i);
			node.remove("shape." + i);
		}
		node.putInt("count", entries.size());
	}
}
