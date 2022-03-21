package de.rwth_aachen.dc.mvdXMLOnlineChecker.events;

import java.io.File;

public class New_ifcSTEPFile {
	private final String userId;
	private final File file;

	public New_ifcSTEPFile(String userId, File file) {
		super();
		this.file = file;
		this.userId = userId;
	}

	public File getFile() {
		return this.file;
	}

	public String getUserId() {
		return userId;
	}
}
