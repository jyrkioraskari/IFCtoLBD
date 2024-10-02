
/*
 *  Copyright (c) 2017,2023, 2024 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
 * 
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

/*
 * To compile this, Java 8 is needed. jfxrt.jar is included, so, the the plugin should not be mandatory
 * but installing the http://www.eclipse.org/efxclipse/index.html and http://gluonhq.com/open-source/scene-builder/
 * make coding easier. 
 * 
   Royalty Free Stock Image: Blue Glass web icons, buttons
   The File image is implemented using:
   http://www.dreamstime.com/royalty-free-stock-image-blue-glass-web-icons-buttons-image8270526
 */

package org.linkedbuildingdata.ifc2lbd.desktop;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;

import org.apache.jena.rdf.model.Resource;
import org.controlsfx.control.CheckTreeView;
import org.controlsfx.control.MaskerPane;
import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.control.textfield.CustomTextField;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemErrorEvent;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.messages.ProcessReadyEvent;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/*
 
Main Components
- Event Handling: Uses Google Guava's EventBus for handling application events.
- File Handling: Uses FileChooser for selecting IFC and target RDF files.

- Conversion Process
  -- Reads IFC files.
  -- Converts them to RDF format.

Methods
- initialize(): Initializes the UI components and sets up event handlers.
- selectIFCFile(): Handles the selection of IFC files.
- selectTargetFile(): Handles the selection of target RDF files.
- convertIFCToRDF(): Initiates the conversion process.
- readInIFC(): Reads the IFC file and prepares for conversion.
- handle_notification(), handleEvent(): Methods to handle various events and update the UI accordingly.
 
 */

public class IFCtoLBDController implements Initializable, FxInterface {
	private Preferences prefs = Preferences.userNodeForPackage(IFCtoLBDController.class);

	private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
	private ExecutorService executor = Executors.newFixedThreadPool(1);

	@FXML
	private AnchorPane root;
	@FXML
	private Rectangle border;

	@FXML
	private MaskerPane masker_panel;

	@FXML
	MenuBar myMenuBar;

	@FXML
	private ToggleSwitch building_elements;
	@FXML
	private Hyperlink elements_link;

	@FXML
	private ToggleSwitch building_elements_separate_file;

	@FXML
	private ToggleSwitch geolocation;

	@FXML
	private ToggleSwitch building_props;

	@FXML
	private Hyperlink props_link;

	@FXML
	private ToggleSwitch building_props_blank_nodes;
	@FXML
	private ToggleSwitch building_props_separate_file;

	@FXML
	private RadioButton level1;
	@FXML
	private RadioButton level2;
	@FXML
	private RadioButton level3;

	@FXML
	private Hyperlink opm_link;

	@FXML
	private TextArea handleOnTxt;

	@FXML
	private Button selectIFCFileButton;

	@FXML
	private Label labelIFCFile;

	@FXML
	private Button selectTargetFileButton;

	@FXML
	private Label labelTargetFile;

	@FXML
	private Button convert2RDFButton;

	@FXML
	private TextArea conversionTxt;

	@FXML
	private ImageView owl_fileIcon;
	@FXML
	private ImageView rdf_fileIcon;

	@FXML
	private CustomTextField labelBaseURI;

	Image fileimage = new Image(getClass().getResourceAsStream("file.png"));

	FileChooser fc_ifc;
	FileChooser fc_target;

	@FXML
	private ToggleSwitch geometry_elements;

	@FXML
	private ToggleSwitch geometry_interfaces;

	
	@FXML
	private ToggleSwitch hasBoundingBox_WKT;

	@FXML
	private ToggleSwitch ifcOWL_elements;

	@FXML
	private ToggleSwitch hasPerformanceBoost;

	@FXML
	private ToggleSwitch createUnits;

	@FXML
	private CheckTreeView<String> element_types_checkbox;
	
	@FXML
	private CheckTreeView<String> propertysets_checkbox;
	

	@FXML
	private ToggleSwitch hasHierarchicalNaming;

	
	@FXML
	private ToggleSwitch hasSimpleProperties;

	@FXML
	private ToggleSwitch ifc_based_elements;
	
	
	@FXML
	private TitledPane options_panel;
	
	@FXML
	private void closeApplicationAction() {
		this.eventBus.post(new IFCtoLBD_SystemExit("User selected the application exit."));
		Stage stage = (Stage) this.myMenuBar.getScene().getWindow();
		stage.close();
		System.exit(0);
	}

	@FXML
	private void aboutAction() {
		// get a handle to the stage
		Stage stage = (Stage) this.myMenuBar.getScene().getWindow();
		new About(stage).show();
	}

	@FXML
	public void hyperlink_product_handle(ActionEvent event) {
		try {
			URI u = new URI("https://github.com/w3c-lbd-cg/product");
			java.awt.Desktop.getDesktop().browse(u);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void hyperlink_opm_handle(ActionEvent event) {
		try {
			URI u = new URI("https://github.com/w3c-lbd-cg/opm");
			java.awt.Desktop.getDesktop().browse(u);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void hyperlink_towards_props(ActionEvent event) {
		try {
			URI u = new URI(
					"https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf");
			java.awt.Desktop.getDesktop().browse(u);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@FXML
	public void ifcOWLSelectionChange(MouseEvent event) {
		if (this.ifcOWL_elements.isSelected()) {
			this.hasPerformanceBoost.setSelected(false);
			this.hasPerformanceBoost.setDisable(true);
		} else {
			this.hasPerformanceBoost.setSelected(true);
			this.hasPerformanceBoost.setDisable(false);
		}

	}

	final Tooltip openExpressFileButton_tooltip = new Tooltip();
	final Tooltip saveIfcOWLButton_tooltip = new Tooltip();

	private String ifcFileName = null;
	private String rdfTargetName = null;

	@FXML
	private void selectIFCFile() {
		Stage stage = (Stage) this.myMenuBar.getScene().getWindow();
		File file = null;

		if (this.fc_ifc == null) {
			this.fc_ifc = new FileChooser();
			String work_directory = this.prefs.get("ifc_work_directory", ".");
			System.out.println("workdir got:" + work_directory);
			File fwd = new File(work_directory);
			if (fwd.exists())
				this.fc_ifc.setInitialDirectory(fwd.getParentFile());
			else
				this.fc_ifc.setInitialDirectory(new File("."));
		}
		FileChooser.ExtensionFilter ef1;
		ef1 = new FileChooser.ExtensionFilter("IFC documents (*.ifc)", "*.ifc");
		FileChooser.ExtensionFilter ef2;
		ef2 = new FileChooser.ExtensionFilter("IFC zip documents (*.ifczip)", "*.ifczip");
		FileChooser.ExtensionFilter ef3;
		ef3 = new FileChooser.ExtensionFilter("All Files", "*.*");
		this.fc_ifc.getExtensionFilters().clear();
		this.fc_ifc.getExtensionFilters().addAll(ef1, ef2, ef3);

		file = this.fc_ifc.showOpenDialog(stage);
		if (file == null)
			return;
		this.fc_ifc.setInitialDirectory(file.getParentFile());
		this.labelIFCFile.setText(file.getName());
		this.ifcFileName = file.getAbsolutePath();
		int i = file.getName().lastIndexOf(".");
		if (i > 0) {
			String target_directory = this.prefs.get("ifc_target_directory", file.getParentFile().getAbsolutePath());
			if (!new File(target_directory).exists())
				target_directory = file.getParent();
			if (target_directory.endsWith("\\"))
				this.rdfTargetName = target_directory + file.getName().substring(0, i) + "_LBD.ttl";
			else
				this.rdfTargetName = target_directory + File.separator + file.getName().substring(0, i) + "_LBD.ttl";
			this.labelTargetFile.setText(this.rdfTargetName);
		}
		if (this.ifcFileName != null && this.rdfTargetName != null) {
			this.selectTargetFileButton.setDisable(false);
			this.convert2RDFButton.setDefaultButton(true);
			this.convert2RDFButton.setDisable(false);
			System.out.println("workdir put:" + this.ifcFileName);
			this.prefs.put("ifc_work_directory", this.ifcFileName);
			readInIFC();
		}
		this.selectIFCFileButton.setDefaultButton(false);
		// rdf_fileIcon.setDisable(false);
		// rdf_fileIcon.setImage(fileimage);
	}

	/*
	 * These forces the user interface to be coherent as the Toggle Grpup does not
	 * work
	 */

	@FXML
	private void selectPropertyLevel1() {
		this.level1.setSelected(true);
		this.level2.setSelected(false);
		this.level3.setSelected(false);
	}

	@FXML
	private void selectPropertyLevel2() {
		this.level1.setSelected(false);
		this.level2.setSelected(true);
		this.level3.setSelected(false);
	}

	@FXML
	private void selectPropertyLevel3() {
		this.level1.setSelected(false);
		this.level2.setSelected(false);
		this.level3.setSelected(true);

	}

	@FXML
	private void selectTargetFile() {
		Stage stage = (Stage) this.myMenuBar.getScene().getWindow();
		File file = null;

		this.fc_target = new FileChooser();
		File fwd = new File(this.rdfTargetName);
		this.fc_target.setInitialFileName(this.rdfTargetName);
		if (!fwd.getParentFile().exists()) {
			this.fc_target.setInitialDirectory(new File(this.ifcFileName).getParentFile());
			String filename = new File(this.ifcFileName).getParentFile() + File.pathSeparator + fwd.getName();
			System.out.println("Initial Filename to: " + filename);
			this.fc_target.setInitialFileName(filename);
			System.out.println("SET");
		} else
			this.fc_target.setInitialDirectory(fwd.getParentFile());

		FileChooser.ExtensionFilter ef;
		ef = new FileChooser.ExtensionFilter("All Files", ".ttl");
		this.fc_ifc.getExtensionFilters().clear();
		this.fc_ifc.getExtensionFilters().addAll(ef);

		try {
			file = this.fc_target.showSaveDialog(stage);
		} catch (Exception e) {
			fwd = new File(this.rdfTargetName);
			System.err.println(
					"fwd parent: " + fwd.getParentFile() + " -> " + new File(this.ifcFileName).getParentFile());
			System.err.println("path was: " + this.fc_target.getInitialDirectory().getAbsolutePath());

			e.printStackTrace();
		}
		if (file == null)
			return;
		this.fc_target.setInitialDirectory(file.getParentFile());
		this.prefs.put("ifc_target_directory", file.getParentFile().getAbsolutePath());
		this.labelTargetFile.setText(file.getAbsolutePath());

		this.rdfTargetName = file.getAbsolutePath();
	}

	Future<IFCtoLBDConverter> running_read_in;
	Future<Integer> running_conversion;

	private String fingerprint="";
	private String selections_fingerprint() {
		String uri_base = this.labelBaseURI.getText().trim();
		int props_level = 2;
		if (this.level1.isSelected())
				props_level = 1;
			if (this.level3.isSelected())
				props_level = 3;
			
			String fingerprint=this.ifcFileName + uri_base + this.rdfTargetName + props_level +  this.building_elements.isSelected() +
			this.building_elements_separate_file.isSelected() + this.building_props.isSelected() + this.building_props_separate_file.isSelected() + this.building_props_blank_nodes.isSelected()+
			this.geolocation.isSelected() + this.geometry_elements.isSelected()+ this.ifcOWL_elements.isSelected()+ this.ifcOWL_elements.isSelected()+
			this.hasPerformanceBoost.isSelected() + this.hasBoundingBox_WKT.isSelected();
			return fingerprint;
	}
	
	private void readInIFC() {
		this.fingerprint=selections_fingerprint();
		readInIFC_execute();
	}
	
	
	private void readInIFC_execute() {
		this.prefs.putBoolean("lbd_building_elements", this.building_elements.isSelected());
		this.prefs.putBoolean("lbd_building_elements_separate_file", this.building_elements_separate_file.isSelected());

		this.prefs.putBoolean("lbd_building_props", this.building_props.isSelected());

		this.prefs.putBoolean("lbd_building_props_blank_nodes", this.building_props_blank_nodes.isSelected());
		this.prefs.putBoolean("lbd_building_props_separate_file", this.building_props_separate_file.isSelected());
		this.prefs.put("lbd_props_base_url", this.labelBaseURI.getText());

		this.prefs.putBoolean("lbd_boundinbox_elements", this.geometry_elements.isSelected());
		this.prefs.putBoolean("lbd_boundinbox_interfaces", this.geometry_interfaces.isSelected());

		this.prefs.putBoolean("lbd_ifcOWL_elements", this.ifcOWL_elements.isSelected());
		this.prefs.putBoolean("lbd_createUnits", this.createUnits.isSelected());

		this.conversionTxt.setText("");
		try {
			String uri_base = this.labelBaseURI.getText().trim();
			int props_level = 2;
			if (this.level1.isSelected())
				props_level = 1;
			if (this.level3.isSelected())
				props_level = 3;
			this.prefs.putInt("lbd_props_level", props_level);
			this.masker_panel.setVisible(true);
			if (this.running_read_in != null && !this.running_read_in.isDone()) {
				this.conversionTxt.appendText("\nThe last conversion is still running. \n");
				return;
			}

			this.running_read_in = this.executor.submit(new ReadinInThread(this.ifcFileName, uri_base,
					this.rdfTargetName, props_level, this.building_elements.isSelected(),
					this.building_elements_separate_file.isSelected(), this.building_props.isSelected(),
					this.building_props_separate_file.isSelected(), this.building_props_blank_nodes.isSelected(),
					this.geolocation.isSelected(), this.geometry_elements.isSelected(),
					this.ifcOWL_elements.isSelected(), this.ifcOWL_elements.isSelected(),
					this.hasPerformanceBoost.isSelected(), this.hasBoundingBox_WKT.isSelected(),this.geometry_interfaces.isSelected()));
		} catch (Exception e) {
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}
		//this.options_panel.setDisable(true);

	}

	@FXML
	private void convertIFCToRDF() {
		
		if(!this.fingerprint.equals(selections_fingerprint()))
		{
			try {
				// Wait and run
				this.running_read_in.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}	
			Platform.runLater(() -> this.conversionTxt.appendText("Re-run the initial read"));
			readInIFC_execute();
		}
		
		
		this.options_panel.setDisable(false);


		this.prefs.putBoolean("lbd_building_elements", this.building_elements.isSelected());
		this.prefs.putBoolean("lbd_building_elements_separate_file", this.building_elements_separate_file.isSelected());

		this.prefs.putBoolean("lbd_building_props", this.building_props.isSelected());

		this.prefs.putBoolean("lbd_building_props_blank_nodes", this.building_props_blank_nodes.isSelected());
		this.prefs.putBoolean("lbd_building_props_separate_file", this.building_props_separate_file.isSelected());
		this.prefs.put("lbd_props_base_url", this.labelBaseURI.getText());

		this.prefs.putBoolean("lbd_boundinbox_elements", this.geometry_elements.isSelected());
		this.prefs.putBoolean("lbd_boundinbox_interfaces", this.geometry_interfaces.isSelected());
		this.prefs.putBoolean("lbd_ifcOWL_elements", this.ifcOWL_elements.isSelected());
		this.prefs.putBoolean("lbd_createUnits", this.createUnits.isSelected());
		this.prefs.putBoolean("lbd_geolocation", this.geolocation.isSelected());

		this.prefs.putBoolean("lbd_hasHierarchicalNaming", this.hasHierarchicalNaming.isSelected());
		this.prefs.putBoolean("lbd_hasSimpleProperties", this.hasSimpleProperties.isSelected());

		this.prefs.putBoolean("ifc_based_elements", this.ifc_based_elements.isSelected());
		
		this.conversionTxt.setText("");
		try {
			String uri_base = this.labelBaseURI.getText().trim();
			int props_level = 2;
			if (this.level1.isSelected())
				props_level = 1;
			if (this.level3.isSelected())
				props_level = 3;
			this.prefs.putInt("lbd_props_level", props_level);
			this.masker_panel.setVisible(true);
			if (this.running_conversion != null && !this.running_conversion.isDone()) {
				this.conversionTxt.appendText("\nThe last conversion is still running. \n");
				return;
			}

			Set<String> selected_types = new HashSet<>();
			for (TreeItem<String> item : this.element_types_checkbox.getCheckModel().getCheckedItems()) {
				selected_types.add(item.getValue());

			}
			
			Set<String> selected_psets = new HashSet<>();
			for (TreeItem<String> item : this.propertysets_checkbox.getCheckModel().getCheckedItems()) {
				selected_psets.add(item.getValue());

			}
			
			IFCtoLBDConverter converter = this.running_read_in.get();		
			converter.setHasSimplified_properties(this.hasSimpleProperties.isSelected());
			this.running_conversion = this.executor.submit(new ConversionThread(converter,
					selected_types,selected_psets, this.ifcFileName, uri_base, this.rdfTargetName, props_level,
					this.building_elements.isSelected(), this.building_elements_separate_file.isSelected(),
					this.building_props.isSelected(), this.building_props_separate_file.isSelected(),
					this.building_props_blank_nodes.isSelected(), this.geolocation.isSelected(), this.geometry_elements.isSelected(),
					this.ifcOWL_elements.isSelected(), this.ifcOWL_elements.isSelected(), this.hasPerformanceBoost.isSelected(),
					this.hasBoundingBox_WKT.isSelected(), this.hasHierarchicalNaming.isSelected(),this.ifc_based_elements .isSelected(),this.geometry_interfaces.isSelected()));
		} catch (Exception e) {
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}

	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.eventBus.register(this);
		this.border.widthProperty().bind(this.root.widthProperty());
		this.border.heightProperty().bind(this.root.heightProperty());
		// Accepts dropping
		new EventHandler<DragEvent>() {
			@Override
			public void handle(DragEvent event) {
				Dragboard db = event.getDragboard();
				if (db.hasFiles()) {
					event.acceptTransferModes(TransferMode.COPY);
				} else {
					event.consume();
				}
			}

		};

		// Accepts dropping
		EventHandler<DragEvent> ad_conversion = new EventHandler<>() {
			public void handle(DragEvent event) {
				Dragboard db = event.getDragboard();
				if (db.hasFiles()) {
					event.acceptTransferModes(TransferMode.COPY);
				} else {
					event.consume();
				}
			}
		};

		// Dropping over surface
		EventHandler<DragEvent> dh_conversion = new EventHandler<>() {
			public void handle(DragEvent event) {
				Dragboard db = event.getDragboard();
				boolean success = false;
				if (db.hasFiles()) {
					success = true;
					for (File file : db.getFiles()) {
						IFCtoLBDController.this.labelIFCFile.setText(file.getName());
						IFCtoLBDController.this.ifcFileName = file.getAbsolutePath();
						if (IFCtoLBDController.this.ifcFileName != null && IFCtoLBDController.this.rdfTargetName != null) {
							IFCtoLBDController.this.convert2RDFButton.setDefaultButton(true);
							IFCtoLBDController.this.selectIFCFileButton.setDefaultButton(false);
							IFCtoLBDController.this.convert2RDFButton.setDisable(false);
						}
						IFCtoLBDController.this.rdf_fileIcon.setDisable(false);
						IFCtoLBDController.this.rdf_fileIcon.setImage(IFCtoLBDController.this.fileimage);
					}
				}
				event.setDropCompleted(success);
				event.consume();
			}
		};

		this.selectIFCFileButton.setOnDragOver(ad_conversion);
		this.selectIFCFileButton.setOnDragDropped(dh_conversion);
		this.convert2RDFButton.setOnDragOver(ad_conversion);
		this.convert2RDFButton.setOnDragDropped(dh_conversion);
		this.labelIFCFile.setOnDragOver(ad_conversion);
		this.labelIFCFile.setOnDragDropped(dh_conversion);
		this.conversionTxt.setOnDragOver(ad_conversion);
		this.conversionTxt.setOnDragDropped(dh_conversion);

		/*
		 * rdf_fileIcon.setOnDragDetected(new EventHandler<MouseEvent>() { public void
		 * handle(MouseEvent me) {
		 * 
		 * if (!rdf_fileIcon.isDisabled()) { Dragboard db =
		 * handleOnTxt.startDragAndDrop(TransferMode.ANY);
		 * 
		 * ClipboardContent content = new ClipboardContent(); Clipboard clipboard =
		 * Clipboard.getSystemClipboard(); try { File temp = File.createTempFile("rdf",
		 * ".ttl");
		 * 
		 * conversionTxt.setText(""); try { String uri_base =
		 * labelBaseURI.getText().trim(); int props_level = 2; if (level1.isSelected())
		 * props_level = 1; if (level3.isSelected()) props_level = 3;
		 * masker_panel.setVisible(true); executor.submit(new
		 * ConversionThread(ifcFileName, uri_base, temp.getAbsolutePath(), props_level,
		 * building_elements.isSelected(), building_elements_separate_file.isSelected(),
		 * building_props.isSelected(), building_props_separate_file.isSelected(),
		 * building_props_blank_nodes.isSelected(), geolocation.isSelected(),
		 * geometry_elements.isSelected(), ifcOWL_elements.isSelected(),
		 * ifcOWL_elements.isSelected(), hasPerformanceBoost.isSelected(),
		 * hasBoundingBox_WKT.isSelected())); } catch (Exception e) {
		 * conversionTxt.appendText(e.getMessage()); }
		 * 
		 * content.putFiles(java.util.Collections.singletonList(temp));
		 * db.setContent(content); clipboard.setContent(content); } catch (IOException
		 * e) {
		 * 
		 * e.printStackTrace(); } } me.consume(); } });
		 * 
		 * rdf_fileIcon.setOnDragDone(new EventHandler<DragEvent>() { public void
		 * handle(DragEvent me) { me.consume(); } });
		 */
		this.labelBaseURI
				.setText(this.prefs.get("lbd_props_base_url", "https://www.ugent.be/myAwesomeFirstBIMProject#"));
		this.building_elements.setSelected(this.prefs.getBoolean("lbd_building_elements", true));
		this.building_elements_separate_file
				.setSelected(this.prefs.getBoolean("lbd_building_elements_separate_file", false));
		this.building_props.setSelected(this.prefs.getBoolean("lbd_building_props", true));
		this.building_props_blank_nodes.setSelected(this.prefs.getBoolean("lbd_building_props_blank_nodes", false));
		this.building_props_separate_file.setSelected(this.prefs.getBoolean("lbd_building_props_separate_file", false));

		this.geometry_elements.setSelected(this.prefs.getBoolean("lbd_boundinbox_elements", true));
		this.geometry_interfaces.setSelected(this.prefs.getBoolean("lbd_boundinbox_interfaces", false));
		this.ifcOWL_elements.setSelected(this.prefs.getBoolean("lbd_ifcOWL_elements", false));
		this.createUnits.setSelected(this.prefs.getBoolean("lbd_createUnits", false));
		this.geolocation.setSelected(this.prefs.getBoolean("lbd_geolocation", true));
		
		this.hasHierarchicalNaming.setSelected(this.prefs.getBoolean("lbd_hasHierarchicalNaming", false));
		this.hasSimpleProperties.setSelected(this.prefs.getBoolean("lbd_hasSimpleProperties", false));

		this.ifc_based_elements.setSelected(this.prefs.getBoolean("ifc_based_elements", false));
		
		this.hasPerformanceBoost.setSelected(this.prefs.getBoolean("lbd_performance", true));
		if (this.ifcOWL_elements.isSelected()) {
			this.hasPerformanceBoost.setSelected(false);
			this.hasPerformanceBoost.setDisable(true);
		} else {
			this.hasPerformanceBoost.setSelected(true);
			this.hasPerformanceBoost.setDisable(false);
		}

		int props_level = this.prefs.getInt("lbd_props_level", 3);
		switch (props_level) {
		case 1:
			this.level1.setSelected(true);
			this.level2.setSelected(false);
			this.level3.setSelected(false);
			break;
		case 2:
			this.level1.setSelected(false);
			this.level2.setSelected(true);
			this.level3.setSelected(false);
			break;
		case 3:
			this.level1.setSelected(false);
			this.level2.setSelected(false);
			this.level3.setSelected(true);
			break;
		default:
			this.level1.setSelected(true);
			this.level2.setSelected(false);
			this.level3.setSelected(false);
			break;

		}

		this.building_elements.setTooltip(new Tooltip(
				"Building Product Ontology instances. \nThis is described in: https://github.com/w3c-lbd-cg/product"));
		this.building_elements_separate_file.setTooltip(new Tooltip("Create the content in separate files."));
		this.building_props.setTooltip(new Tooltip(
				"Building related properties\nThis is dedcribed in: https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf"));
		this.building_props_separate_file
				.setTooltip(new Tooltip("Create the content in separate files (Only levels 2 or 3)."));

		this.elements_link.setTooltip(new Tooltip("Opens a link that describes the Building Product Ontology."));

		this.props_link.setTooltip(new Tooltip("Opens a link to the Towards a PROPS ontology presentation."));
		this.opm_link.setTooltip(new Tooltip("Opens a link that describes the Ontology for Property Management."));

		this.selectIFCFileButton.setTooltip(new Tooltip(
				"Select an IFC Step formatted file to convert.\nThe supported IFC versions are\n2x3 TC1 & Final, 4 ADD1, 4 ADD2, 4  "));
		this.selectTargetFileButton.setTooltip(new Tooltip(
				"Select an target file for the conversion.\nIf there will be many files, the separate files are named accorrdingly."));
		this.convert2RDFButton.setTooltip(new Tooltip("Press this button to start the conversion process."));
		this.conversionTxt.setTooltip(
				new Tooltip("This shows the conversion process related messages\nand any error that occurs. "));

		this.labelIFCFile.setTooltip(new Tooltip("The selected IFC file. "));

		this.labelTargetFile.setTooltip(new Tooltip("The selected target RDF file. "));

		this.labelBaseURI
				.setTooltip(new Tooltip("The base URL is the consistent part of your links generated in the output. "));

		this.hasPerformanceBoost.setTooltip(
				new Tooltip("When used, the memory consumption is saved by removing the ifcOWL geometry. "));

	}

	@Override
	public void handle_notification(String txt) {
		this.conversionTxt.insertText(0, txt + "\n");
	}

	@Subscribe
	public void handleEvent(final IFCtoLBD_SystemStatusEvent event) {
		System.out.println("message: " + event.getStatus_message());
		Platform.runLater(() -> this.conversionTxt.appendText(event.getStatus_message() + "\n"));
	}

	@Subscribe
	public void handleEvent(final IFCtoLBD_SystemErrorEvent event) {
		System.out.println("error: " + event.getStatus_message());
		Platform.runLater(
				() -> this.conversionTxt.appendText(event.getClass_name() + ": " + event.getStatus_message() + "\n"));
	}

	@Subscribe
	public void handleEvent(final ProcessReadyEvent event) {
		this.masker_panel.setVisible(false);

		if (event.getPhase() == ProcessReadyEvent.READ_IN) {
			Platform.runLater(() -> {
				// prepare tree items
				try {
					IFCtoLBDConverter converter = this.running_read_in.get();
					Set<Resource> element_types = converter.getElementTypes();
					CheckBoxTreeItem<String> types_checkbox_values = new CheckBoxTreeItem<>("Model");

					types_checkbox_values.getChildren().clear();
					// add items to the root
					for (Resource et : element_types) {
						CheckBoxTreeItem<String> item = new CheckBoxTreeItem<>(et.getLocalName());
						item.setSelected(true);
						types_checkbox_values.getChildren().add(item);

					}
					types_checkbox_values.setExpanded(true);
					
					Set<String> pset_names = converter.getPropertySetNames();

					this.element_types_checkbox.setRoot(types_checkbox_values);
					this.element_types_checkbox.setShowRoot(true);
					
					
					CheckBoxTreeItem<String> psets_checkbox_values = new CheckBoxTreeItem<>("PSets");

					psets_checkbox_values.getChildren().clear();
					// add items to the root
					for (String ps_name : pset_names) {
						CheckBoxTreeItem<String> item = new CheckBoxTreeItem<>(ps_name);
						item.setSelected(true);
						psets_checkbox_values.getChildren().add(item);

					}
					psets_checkbox_values.setExpanded(true);
					
					this.propertysets_checkbox.setRoot(psets_checkbox_values);
					this.propertysets_checkbox.setShowRoot(true);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			});
		}
	}
}
