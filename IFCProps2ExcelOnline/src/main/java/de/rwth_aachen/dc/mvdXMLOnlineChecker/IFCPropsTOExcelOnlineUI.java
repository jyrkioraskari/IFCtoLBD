package de.rwth_aachen.dc.mvdXMLOnlineChecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

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

import com.google.common.eventbus.Subscribe;
import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.FileResource;
import com.vaadin.server.Page;
import com.vaadin.server.Resource;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import de.rwth_aachen.dc.mvdXMLOnlineChecker.events.EventBusCommunication;
import de.rwth_aachen.dc.mvdXMLOnlineChecker.events.New_ifcSTEPFile;
import de.rwth_aachen.dc.mvdXMLOnlineChecker.upload.WebFileHandler;

@Theme("rwth")
@Push
public class IFCPropsTOExcelOnlineUI extends UI {
	private static final long serialVersionUID = -4973383389013795451L;

	private EventBusCommunication communication = EventBusCommunication.getInstance();

	private Label ifcFileLabel = new Label("IFC STEP file");
	private File ifcFile = null;

	private Button save_as_excel_button;
	private FileDownloader fileDownloader;

	Image image;

	private UI ui_interface;
	private String vaadin_session;
	private Page vaadin_page;

	@Override
	protected void init(VaadinRequest vaadinRequest) {
		final VerticalLayout layout = new VerticalLayout();
		this.ui_interface = this.getUI();
		this.vaadin_session = VaadinSession.getCurrent().getSession().getId();
		this.vaadin_page = Page.getCurrent();

		Resource res = new ThemeResource("rwth_caad_en_schwarz_grau_rgb.svg");
		this.image = new Image("", res);
		this.image.setHeight("200px");
		this.image.setHeight("100px");
		layout.addComponent(this.image);
		final Label labelH1 = new Label("IFC Properties to Excel Online Interface 2022");
		labelH1.addStyleName(ValoTheme.LABEL_H1);
		layout.addComponent(labelH1);
		layout.addComponents(ifcFileLabel);

		WebFileHandler file_receiver = null;
		try {
			Path tempdirectory = Files.createTempDirectory("IFCPropsTOExcelOnlineUI");
			file_receiver = new WebFileHandler(VaadinSession.getCurrent().getSession().getId(),
					tempdirectory.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (file_receiver == null)
			return; // Cannot throw an exception

		Upload file_uploader = new Upload("Select and upload the ifc file here", file_receiver);
		file_uploader.addSucceededListener(file_receiver);
		file_uploader.addFailedListener(file_receiver);
		file_uploader.setButtonCaption("Upload IFC file");
		file_uploader.setImmediateMode(true);

		HorizontalLayout button_layout = new HorizontalLayout();
		button_layout.addComponents(file_uploader);
		button_layout.setComponentAlignment(file_uploader, Alignment.BOTTOM_CENTER);

		layout.addComponent(button_layout);

		HorizontalLayout button_layout2 = new HorizontalLayout();
		this.save_as_excel_button = new Button("Save the result as Excel.");
		this.save_as_excel_button.setEnabled(false);
		button_layout2.addComponent(save_as_excel_button);
		layout.addComponent(button_layout2);
		setContent(layout);
		communication.register(this);
	}

	@Subscribe
	public void onNew_ifcSTEPFile(New_ifcSTEPFile event) {
		if (event.getUserId().equals(VaadinSession.getCurrent().getSession().getId())) {
			this.ifcFileLabel.setValue("IFC: " + event.getFile().getName());
			this.ifcFile = event.getFile();

			if (this.ifcFile != null) {
				extractIFCtoLBD(this.ifcFile, RDFFormat.TURTLE_PRETTY);
			}
		}
	}

	private void extractIFCtoLBD(File ifcFile, RDFFormat rdfformat) {
		Map<String, Set<String>> properties_map = new HashMap<>();
		Map<String, Map<String, Map<String, RDFNode>>> value_map = new HashMap<>(); /// Element ->property,value

		IFCtoLBDConverter lbdconverter = new IFCtoLBDConverter("https://dot.dc.rwth-aachen.de/IFCtoLBDset", false, 2);
		Model m = lbdconverter.convert(ifcFile.getAbsolutePath());
		ResIterator subIt = m.listSubjectsWithProperty(RDF.type, BOT.element);
		while (subIt.hasNext()) {
			org.apache.jena.rdf.model.Resource element = subIt.next();
			String se = element.getLocalName();
			String type = se.split("_")[0];

			Map<String, Map<String, RDFNode>> type_values = value_map.getOrDefault(type,
					new HashMap<String, Map<String, RDFNode>>());
			value_map.put(type, type_values);
			Set<String> properties=properties_map.getOrDefault(type, new HashSet<String>());
			properties_map.put(type, properties);

			Map<String, RDFNode> values = new HashMap<>();
			type_values.put(se, values);
			StmtIterator it = element.listProperties();
			while (it.hasNext()) {
				Statement st = it.next();
				String predicate_name = st.getPredicate().getLocalName().replaceFirst("_simple", "");
				
				if (st.getObject().isURIResource()) {
					System.out.println("predicate_name 1: "+predicate_name);
					StmtIterator pit = st.getObject().asResource().listProperties();
					String pset_name_pname = null;
					RDFNode value = null;
					while (pit.hasNext()) {
						Statement pval = pit.next();
						if (pval.getPredicate().getLocalName().equals("label"))
							pset_name_pname = pval.getObject().asLiteral().getLexicalForm();
						if (pval.getPredicate().getLocalName().equals("value")) {
							value = pval.getObject();
						}
					}
					if (value != null && pset_name_pname != null)
					{
						properties.add(pset_name_pname);
						values.put(pset_name_pname, value);
					}
					else
						if(value!=null)
						{
							properties.add(predicate_name);
							values.put(predicate_name, value);
						}

				} 
			}
		}
		;

		Workbook workbook = new XSSFWorkbook();

		CellStyle headerStyle = workbook.createCellStyle();
		CellStyle style = workbook.createCellStyle();
		style.setWrapText(true);

		XSSFFont fontH = ((XSSFWorkbook) workbook).createFont();
		fontH.setFontName("Arial");
		fontH.setFontHeightInPoints((short) 16);
		fontH.setBold(true);
		headerStyle.setFont(fontH);

		XSSFFont fontC = ((XSSFWorkbook) workbook).createFont();
		fontC.setFontName("Arial");
		fontC.setFontHeightInPoints((short) 12);
		fontC.setBold(false);
		style.setFont(fontC);
		style.setWrapText(true);

		for (String type : value_map.keySet()) {
			Map<String, Map<String, RDFNode>> type_values = value_map.get(type);
			Sheet sheet = workbook.createSheet(type);
			for (int i = 0; i < 500; i++)
				sheet.setColumnWidth(i, 15000);
			int rowinx = 0;
			Row header = sheet.createRow(rowinx++);
			int hinx = 0;
			Cell headerCell_guid = header.createCell(hinx++);
			headerCell_guid.setCellValue("Global ID");
			headerCell_guid.setCellStyle(headerStyle);

			
			for (String prop : properties_map.get(type)) {
				if(prop.equals("globalIdIfcRoot"))
					continue;
				Cell headerCell = header.createCell(hinx++);
				headerCell.setCellValue(prop);
				headerCell.setCellStyle(headerStyle);
			}
			

			for (String element : value_map.get(type).keySet()) {
				int cinx = 1;
				Row row = sheet.createRow(rowinx++);

				Map<String, RDFNode> values = type_values.get(element);
				Cell cell_guid = row.createCell(0);
				cell_guid.setCellValue(values.getOrDefault("globalIdIfcRoot",ResourceFactory.createPlainLiteral("-")).toString());

				for (String prop : properties_map.get(type)) {
					if(prop.equals("globalIdIfcRoot"))
						continue;
					Cell cell = row.createCell(cinx++);
					RDFNode object = values.getOrDefault(prop, ResourceFactory.createPlainLiteral("-"));
					if (object.isLiteral()) {
						if (Number.class.isAssignableFrom(object.asLiteral().getValue().getClass())) {
							DataFormat format = workbook.createDataFormat();
							style.setDataFormat(format.getFormat("0.00"));
							cell.setCellValue(Double.parseDouble(object.asLiteral().getLexicalForm()));
							cell.setCellStyle(style);
						} else
							cell.setCellValue(object.asLiteral().getLexicalForm());
					} else
						cell.setCellValue(object.toString());
					cell.setCellStyle(style);
				}
			}
		}
		;

		try {
			File tempExcelFile = File.createTempFile("ifc2lbd-", ".xlsx");
			FileOutputStream outputStream = new FileOutputStream(tempExcelFile);
			workbook.write(outputStream);
			workbook.close();
			Resource res = new FileResource(tempExcelFile);
			if (this.fileDownloader == null) {
				this.fileDownloader = new FileDownloader(res);
				this.fileDownloader.extend(this.save_as_excel_button);
			} else {
				this.fileDownloader.setFileDownloadResource(res);
			}
			this.save_as_excel_button.setEnabled(true);

		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	@WebServlet(urlPatterns = "/*", name = "mvdXMLOnlineCheckerUIServlet", asyncSupported = true)
	@VaadinServletConfiguration(ui = IFCPropsTOExcelOnlineUI.class, productionMode = false)
	public static class mvdXMLOnlineCheckerUIServlet extends VaadinServlet {
	}

	public static void main(String[] args) {
		IFCPropsTOExcelOnlineUI uitest = new IFCPropsTOExcelOnlineUI();
		uitest.extractIFCtoLBD(new File("c:\\jo\\Duplex_A_20110907.ifc"), RDFFormat.TURTLE_PRETTY);
	}
}
