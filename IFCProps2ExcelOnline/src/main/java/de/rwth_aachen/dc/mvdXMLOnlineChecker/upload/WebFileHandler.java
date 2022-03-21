package de.rwth_aachen.dc.mvdXMLOnlineChecker.upload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import com.vaadin.server.Page;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Upload.FailedEvent;
import com.vaadin.ui.Upload.FailedListener;
import com.vaadin.ui.Upload.Receiver;
import com.vaadin.ui.Upload.SucceededEvent;
import com.vaadin.ui.Upload.SucceededListener;

import de.rwth_aachen.dc.mvdXMLOnlineChecker.events.EventBusCommunication;
import de.rwth_aachen.dc.mvdXMLOnlineChecker.events.New_ifcSTEPFile;


public class WebFileHandler implements Receiver, SucceededListener, FailedListener {
	private final String userId;
	private EventBusCommunication communication = EventBusCommunication.getInstance();
	private static final long serialVersionUID = -3353293504543369792L;
	private String uploads;

	public WebFileHandler(String userId, String uploads) {
		this.uploads = uploads;
		this.userId = userId;

		if (!uploads.endsWith(File.separator))
			this.uploads = this.uploads + File.separator;
	}

	public OutputStream receiveUpload(String filename, String mimeType) {
		if ((filename == null) || filename.length() == 0) {
			Notification n = new Notification("A file has to be selected", " ", Notification.Type.ERROR_MESSAGE);
			n.setDelayMsec(5000);
			n.show(Page.getCurrent());
			return null;
		}
		if (!(filename.toLowerCase().endsWith(".ifc") || filename.toLowerCase().endsWith(".mvdxml")
				|| filename.toLowerCase().endsWith(".xml"))) {
			Notification n = new Notification("The file extension has to be .ifc, .xml, or .mvdxml", " ",
					Notification.Type.ERROR_MESSAGE);
			n.setDelayMsec(5000);
			n.show(Page.getCurrent());
			return null;

		}

		// Create upload stream
		FileOutputStream fos = null; // Stream to write to
		try {
			// Open the file for writing.
			File file = new File(uploads + filename);
			fos = new FileOutputStream(file);
		} catch (final java.io.FileNotFoundException e) {
			Notification n = new Notification("Could not open file ", e.getMessage(), Notification.Type.ERROR_MESSAGE);
			n.setDelayMsec(5000);
			n.show(Page.getCurrent());
			return null;
		}
		return fos;
	}

	public void uploadSucceeded(SucceededEvent event) {
		File file = new File(this.uploads + event.getFilename());
		if (!file.exists()) {
			Notification n = new Notification("Upload not successed. The dile is not existing.",
					Notification.Type.TRAY_NOTIFICATION);
			n.setDelayMsec(1000);
			n.show(Page.getCurrent());
		}

		if (event.getFilename().toLowerCase().endsWith(".ifc")) {

			communication.post(new New_ifcSTEPFile(this.userId,file));
		}
		Notification n = new Notification("Uploading succeed.", Notification.Type.TRAY_NOTIFICATION);
		n.setDelayMsec(1000);
		n.show(Page.getCurrent());

	}

	@Override
	public void uploadFailed(FailedEvent event) {
		Notification n = new Notification("Uploading the file failed.", Notification.Type.ERROR_MESSAGE);
		n.setDelayMsec(1000);
		n.show(Page.getCurrent());
	}
};
