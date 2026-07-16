package de.rwth_aachen.dc.excelreport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.namespace.BOT;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

@Route("")
@PageTitle("IFC Properties to Excel Online")
public class IFCPropsTOExcelOnlineUI extends VerticalLayout {
	private static final long serialVersionUID = 1L;

	private final Paragraph selectedFile = new Paragraph("No IFC file uploaded yet.");
	private final Paragraph status = new Paragraph("Upload an IFC STEP file to generate an Excel workbook.");
	private final Anchor downloadAnchor = new Anchor();
	private final Button downloadButton = new Button("Download Excel");

	public IFCPropsTOExcelOnlineUI() {
		setWidthFull();
		setMaxWidth("1100px");
		setPadding(true);
		setSpacing(true);

		H2 title = new H2("IFC Properties to Excel Online");
		Paragraph description = new Paragraph(
				"Upload an IFC STEP file, then download the generated Excel workbook.");

		MemoryBuffer buffer = new MemoryBuffer();
		Upload upload = new Upload(buffer);
		upload.setMaxFiles(1);
		upload.setAcceptedFileTypes(".ifc", ".xml", ".mvdxml");
		upload.setMaxFileSize(250 * 1024 * 1024);
		upload.addSucceededListener(event -> {
			try (InputStream inputStream = buffer.getInputStream()) {
				byte[] uploadedBytes = inputStream.readAllBytes();
				processUploadedFile(buffer.getFileName(), uploadedBytes);
			} catch (IOException e) {
				showError("Unable to read the uploaded file.", e);
			}
		});
		upload.addFailedListener(event -> showError("The upload failed.", event.getReason()));
		upload.addFileRejectedListener(event -> showError(event.getErrorMessage(), null));

		downloadButton.setEnabled(false);
		downloadAnchor.add(downloadButton);
		downloadAnchor.getElement().setAttribute("download", true);

		add(title, description, upload, selectedFile, status, downloadAnchor);
	}

	private void processUploadedFile(String originalFileName, byte[] uploadedBytes) {
		String uploadName = originalFileName == null || originalFileName.isBlank() ? "uploaded.ifc" : originalFileName;
		selectedFile.setText("IFC: " + uploadName);
		status.setText("Processing file...");
		downloadButton.setEnabled(false);

		Path tempInput = null;
		try {
			tempInput = Files.createTempFile("ifcprops2excel-", getSuffix(uploadName));
			Files.write(tempInput, uploadedBytes);

			byte[] workbookBytes = extractIFCtoLBD(tempInput.toFile());
			String downloadName = replaceExtension(uploadName, ".xlsx");
			StreamResource resource = new StreamResource(downloadName,
					() -> new ByteArrayInputStream(workbookBytes));
			downloadAnchor.setHref(resource);
			downloadButton.setEnabled(true);
			status.setText("Workbook ready.");
			showInfo("Excel workbook generated.");
		} catch (Exception e) {
			showError("Could not generate the Excel workbook.", e);
		} finally {
			if (tempInput != null) {
				try {
					Files.deleteIfExists(tempInput);
				} catch (IOException ignored) {
					// Best-effort cleanup.
				}
			}
		}
	}

	private byte[] extractIFCtoLBD(File ifcFile) throws IOException {
		Map<String, Set<String>> propertiesMap = new LinkedHashMap<>();
		Map<String, Map<String, Map<String, RDFNode>>> valueMap = new LinkedHashMap<>();

		IFCtoLBDConverter lbdconverter = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset", false, 2);
		Model model = lbdconverter.convert(ifcFile.getAbsolutePath());
		ResIterator subIt = model.listSubjectsWithProperty(RDF.type, BOT.element);
		while (subIt.hasNext()) {
			org.apache.jena.rdf.model.Resource element = subIt.next();
			String localName = element.getLocalName();
			if (localName == null || localName.isBlank()) {
				continue;
			}
			String type = localName.split("_")[0];
			Map<String, Map<String, RDFNode>> typeValues = valueMap.computeIfAbsent(type,
					key -> new LinkedHashMap<>());
			Set<String> properties = propertiesMap.computeIfAbsent(type, key -> new LinkedHashSet<>());

			Map<String, RDFNode> values = new LinkedHashMap<>();
			typeValues.put(localName, values);
			StmtIterator it = element.listProperties();
			while (it.hasNext()) {
				Statement st = it.next();
				String predicateName = st.getPredicate().getLocalName().replaceFirst("_simple", "");
				if (!st.getObject().isURIResource()) {
					continue;
				}

				StmtIterator pit = st.getObject().asResource().listProperties();
				String psetNamePname = null;
				RDFNode value = null;
				while (pit.hasNext()) {
					Statement pval = pit.next();
					String localPredicate = pval.getPredicate().getLocalName();
					if ("label".equals(localPredicate)) {
						psetNamePname = pval.getObject().asLiteral().getLexicalForm();
					}
					if ("value".equals(localPredicate)) {
						value = pval.getObject();
					}
				}
				if (value != null) {
					String propertyName = psetNamePname != null ? psetNamePname : predicateName;
					properties.add(propertyName);
					values.put(propertyName, value);
				}
			}
		}

		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			CellStyle headerStyle = workbook.createCellStyle();
			CellStyle valueStyle = workbook.createCellStyle();
			valueStyle.setWrapText(true);

			XSSFFont headerFont = ((XSSFWorkbook) workbook).createFont();
			headerFont.setFontName("Arial");
			headerFont.setFontHeightInPoints((short) 16);
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);

			XSSFFont valueFont = ((XSSFWorkbook) workbook).createFont();
			valueFont.setFontName("Arial");
			valueFont.setFontHeightInPoints((short) 12);
			valueFont.setBold(false);
			valueStyle.setFont(valueFont);
			valueStyle.setWrapText(true);

			for (String type : valueMap.keySet()) {
				Map<String, Map<String, RDFNode>> typeValues = valueMap.get(type);
				Sheet sheet = workbook.createSheet(sanitizeSheetName(type));
				for (int i = 0; i < 500; i++) {
					sheet.setColumnWidth(i, 15000);
				}

				int rowIndex = 0;
				Row header = sheet.createRow(rowIndex++);
				int headerIndex = 0;
				Cell headerCellGuid = header.createCell(headerIndex++);
				headerCellGuid.setCellValue("Global ID");
				headerCellGuid.setCellStyle(headerStyle);

				for (String prop : propertiesMap.get(type)) {
					if ("globalIdIfcRoot".equals(prop)) {
						continue;
					}
					Cell headerCell = header.createCell(headerIndex++);
					headerCell.setCellValue(prop);
					headerCell.setCellStyle(headerStyle);
				}

				for (String element : typeValues.keySet()) {
					int cellIndex = 1;
					Row row = sheet.createRow(rowIndex++);
					Map<String, RDFNode> values = typeValues.get(element);

					Cell guidCell = row.createCell(0);
					RDFNode globalId = values.getOrDefault("globalIdIfcRoot", ResourceFactory.createPlainLiteral("-"));
					guidCell.setCellValue(globalId.isLiteral() ? globalId.asLiteral().getLexicalForm() : globalId.toString());
					guidCell.setCellStyle(valueStyle);

					for (String prop : propertiesMap.get(type)) {
						if ("globalIdIfcRoot".equals(prop)) {
							continue;
						}
						Cell cell = row.createCell(cellIndex++);
						RDFNode object = values.getOrDefault(prop, ResourceFactory.createPlainLiteral("-"));
						if (object.isLiteral()) {
							Object literalValue = object.asLiteral().getValue();
							if (literalValue instanceof Number) {
								DataFormat format = workbook.createDataFormat();
								valueStyle.setDataFormat(format.getFormat("0.00"));
								cell.setCellValue(Double.parseDouble(object.asLiteral().getLexicalForm()));
							} else {
								cell.setCellValue(object.asLiteral().getLexicalForm());
							}
						} else {
							cell.setCellValue(object.toString());
						}
						cell.setCellStyle(valueStyle);
					}
				}
			}

			workbook.write(outputStream);
			return outputStream.toByteArray();
		}
	}

	private void showInfo(String message) {
		Notification notification = Notification.show(message, 3000, Notification.Position.MIDDLE);
		notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
	}

	private void showError(String message, Throwable error) {
		status.setText(message);
		downloadButton.setEnabled(false);
		Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
		notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
		if (error != null) {
			error.printStackTrace();
		}
	}

	private static String replaceExtension(String fileName, String newExtension) {
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
		return baseName + newExtension;
	}

	private static String getSuffix(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex > 0 ? fileName.substring(dotIndex) : ".ifc";
	}

	private static String sanitizeSheetName(String type) {
		String sanitized = type.replaceAll("[\\\\/?*\\[\\]:]", "_");
		if (sanitized.isBlank()) {
			return "Sheet";
		}
		return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
	}
}
