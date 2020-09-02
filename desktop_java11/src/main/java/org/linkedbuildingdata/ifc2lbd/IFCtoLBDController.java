
/*
 *  Copyright (c) 2017 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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

package org.linkedbuildingdata.ifc2lbd;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import org.controlsfx.control.MaskerPane;
import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.control.textfield.CustomTextField;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

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
	private void closeApplicationAction() {
		// get a handle to the stage
		Stage stage = (Stage) myMenuBar.getScene().getWindow();
		stage.close();
	}

	@FXML
	private void aboutAction() {
		// get a handle to the stage
		Stage stage = (Stage) myMenuBar.getScene().getWindow();
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

	final Tooltip openExpressFileButton_tooltip = new Tooltip();
	final Tooltip saveIfcOWLButton_tooltip = new Tooltip();

	private String ifcFileName = null;
	private String rdfTargetName = null;

	@FXML
	private void selectIFCFile() {
		Stage stage = (Stage) myMenuBar.getScene().getWindow();
		File file = null;

		if (fc_ifc == null) {
			fc_ifc = new FileChooser();
			String work_directory = prefs.get("ifc_work_directory", ".");
			System.out.println("workdir got:" + work_directory);
			File fwd = new File(work_directory);
			if (fwd != null && fwd.exists())
				fc_ifc.setInitialDirectory(fwd.getParentFile());
			else
				fc_ifc.setInitialDirectory(new File("."));
		}
		FileChooser.ExtensionFilter ef1;
		ef1 = new FileChooser.ExtensionFilter("IFC documents (*.ifc)", "*.ifc");
		FileChooser.ExtensionFilter ef2;
		ef2 = new FileChooser.ExtensionFilter("All Files", "*.*");
		fc_ifc.getExtensionFilters().clear();
		fc_ifc.getExtensionFilters().addAll(ef1, ef2);

		if (file == null)
			file = fc_ifc.showOpenDialog(stage);
		if (file == null)
			return;
		fc_ifc.setInitialDirectory(file.getParentFile());
		labelIFCFile.setText(file.getName());
		ifcFileName = file.getAbsolutePath();
		int i = file.getName().lastIndexOf(".");
		if (i > 0) {
			String target_directory = prefs.get("ifc_target_directory", file.getParentFile().getAbsolutePath());
			if (!new File(target_directory).exists())
				target_directory = file.getParent();
			rdfTargetName = target_directory + File.separator + file.getName().substring(0, i) + "_LBD.ttl";
			labelTargetFile.setText(rdfTargetName);
		}
		if (ifcFileName != null && rdfTargetName != null) {
			selectTargetFileButton.setDisable(false);
			convert2RDFButton.setDefaultButton(true);
			convert2RDFButton.setDisable(false);
			System.out.println("workdir put:" + ifcFileName);
			prefs.put("ifc_work_directory", ifcFileName);

		}
		selectIFCFileButton.setDefaultButton(false);
		// rdf_fileIcon.setDisable(false);
		// rdf_fileIcon.setImage(fileimage);
	}

	@FXML
	private void selectTargetFile() {
		Stage stage = (Stage) myMenuBar.getScene().getWindow();
		File file = null;

		fc_target = new FileChooser();
		File fwd = new File(rdfTargetName);
		fc_target.setInitialFileName(rdfTargetName);
		if (!fwd.getParentFile().exists()) {
			fc_target.setInitialDirectory(new File(ifcFileName).getParentFile());
			fc_target.setInitialFileName(new File(ifcFileName).getParentFile() + File.pathSeparator + fwd.getName());
			System.out.println("SET");
		} else
			fc_target.setInitialDirectory(fwd.getParentFile());

		FileChooser.ExtensionFilter ef;
		ef = new FileChooser.ExtensionFilter("All Files", "*.*");
		fc_ifc.getExtensionFilters().clear();
		fc_ifc.getExtensionFilters().addAll(ef);

		try {
			file = fc_target.showSaveDialog(stage);
		} catch (Exception e) {
			fwd = new File(rdfTargetName);
			System.err.println("fwd parent: " + fwd.getParentFile() + " -> " + new File(ifcFileName).getParentFile());
			System.err.println("path was: " + fc_target.getInitialDirectory().getAbsolutePath());

			e.printStackTrace();
		}
		if (file == null)
			return;
		fc_target.setInitialDirectory(file.getParentFile());
		prefs.put("ifc_target_directory", file.getParentFile().getAbsolutePath());
		labelTargetFile.setText(file.getAbsolutePath());

		rdfTargetName = file.getAbsolutePath();
	}

	@FXML
	private void convertIFCToRDF() {
		prefs.putBoolean("lbd_building_elements", this.building_elements.isSelected());
		prefs.putBoolean("lbd_building_elements_separate_file", this.building_elements_separate_file.isSelected());

		prefs.putBoolean("lbd_building_props", this.building_props.isSelected());

		prefs.putBoolean("lbd_building_props_blank_nodes", this.building_props_blank_nodes.isSelected());
		prefs.putBoolean("lbd_building_props_separate_file", this.building_props_separate_file.isSelected());
		prefs.put("lbd_props_base_url", this.labelBaseURI.getText());

		conversionTxt.setText("");
		try {
			String uri_base = labelBaseURI.getText().trim();
			int props_level = 2;
			if (level1.isSelected())
				props_level = 1;
			if (level3.isSelected())
				props_level = 3;
			prefs.putInt("lbd_props_level", props_level);
			masker_panel.setVisible(true);
			executor.submit(new ConversionThread(ifcFileName, uri_base, rdfTargetName, props_level,
					building_elements.isSelected(), building_elements_separate_file.isSelected(),
					building_props.isSelected(), building_props_separate_file.isSelected(),
					building_props_blank_nodes.isSelected(), geolocation.isSelected()));
		} catch (Exception e) {
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}

	}

	public void initialize(URL location, ResourceBundle resources) {
		eventBus.register(this);
		border.widthProperty().bind(root.widthProperty());
		border.heightProperty().bind(root.heightProperty());
		// Accepts dropping
		new EventHandler<DragEvent>() {
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
		EventHandler<DragEvent> ad_conversion = new EventHandler<DragEvent>() {
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
		EventHandler<DragEvent> dh_conversion = new EventHandler<DragEvent>() {
			public void handle(DragEvent event) {
				Dragboard db = event.getDragboard();
				boolean success = false;
				if (db.hasFiles()) {
					success = true;
					for (File file : db.getFiles()) {
						labelIFCFile.setText(file.getName());
						ifcFileName = file.getAbsolutePath();
						if (ifcFileName != null && rdfTargetName != null) {
							convert2RDFButton.setDefaultButton(true);
							selectIFCFileButton.setDefaultButton(false);
							convert2RDFButton.setDisable(false);
						}
						rdf_fileIcon.setDisable(false);
						rdf_fileIcon.setImage(fileimage);
					}
				}
				event.setDropCompleted(success);
				event.consume();
			}
		};

		selectIFCFileButton.setOnDragOver(ad_conversion);
		selectIFCFileButton.setOnDragDropped(dh_conversion);
		convert2RDFButton.setOnDragOver(ad_conversion);
		convert2RDFButton.setOnDragDropped(dh_conversion);
		labelIFCFile.setOnDragOver(ad_conversion);
		labelIFCFile.setOnDragDropped(dh_conversion);
		conversionTxt.setOnDragOver(ad_conversion);
		conversionTxt.setOnDragDropped(dh_conversion);

		rdf_fileIcon.setOnDragDetected(new EventHandler<MouseEvent>() {
			public void handle(MouseEvent me) {

				if (!rdf_fileIcon.isDisabled()) {
					Dragboard db = handleOnTxt.startDragAndDrop(TransferMode.ANY);

					ClipboardContent content = new ClipboardContent();
					Clipboard clipboard = Clipboard.getSystemClipboard();
					try {
						File temp = File.createTempFile("rdf", ".ttl");

						conversionTxt.setText("");
						try {
							String uri_base = labelBaseURI.getText().trim();
							int props_level = 2;
							if (level1.isSelected())
								props_level = 1;
							if (level3.isSelected())
								props_level = 3;
							masker_panel.setVisible(true);
							executor.submit(new ConversionThread(ifcFileName, uri_base, temp.getAbsolutePath(),
									props_level, building_elements.isSelected(),
									building_elements_separate_file.isSelected(), building_props.isSelected(),
									building_props_separate_file.isSelected(), building_props_blank_nodes.isSelected(),
									geolocation.isSelected()));
						} catch (Exception e) {
							conversionTxt.appendText(e.getMessage());
						}

						content.putFiles(java.util.Collections.singletonList(temp));
						db.setContent(content);
						clipboard.setContent(content);
					} catch (IOException e) {

						e.printStackTrace();
					}
				}
				me.consume();
			}
		});

		rdf_fileIcon.setOnDragDone(new EventHandler<DragEvent>() {
			public void handle(DragEvent me) {
				me.consume();
			}
		});
		this.labelBaseURI.setText(prefs.get("lbd_props_base_url", "https://www.ugent.be/myAwesomeFirstBIMProject#"));
		this.building_elements.setSelected(prefs.getBoolean("lbd_building_elements", true));
		this.building_elements_separate_file
				.setSelected(prefs.getBoolean("lbd_building_elements_separate_file", false));
		this.building_props.setSelected(prefs.getBoolean("lbd_building_props", true));
		this.building_props_blank_nodes.setSelected(prefs.getBoolean("lbd_building_props_blank_nodes", false));
		this.building_props_separate_file.setSelected(prefs.getBoolean("lbd_building_props_separate_file", false));

		int props_level = prefs.getInt("lbd_props_level", 3);
		switch (props_level) {
		case 1:
			level1.setSelected(true);
			level2.setSelected(false);
			level3.setSelected(false);
			break;
		case 2:
			level1.setSelected(false);
			level2.setSelected(true);
			level3.setSelected(false);
			break;
		case 3:
			level1.setSelected(false);
			level2.setSelected(false);
			level3.setSelected(true);
			break;

		}

		building_elements.setTooltip(new Tooltip(
				"Building Product Ontology instances. \nThis is described in: https://github.com/w3c-lbd-cg/product"));
		building_elements_separate_file.setTooltip(new Tooltip("Create the content in separate files."));
		building_props.setTooltip(new Tooltip(
				"Building related properties\nThis is dedcribed in: https://github.com/w3c-lbd-cg/lbd/blob/gh-pages/presentations/props/presentation_LBDcall_20180312_final.pdf"));
		building_props_separate_file.setTooltip(new Tooltip("Create the content in separate files."));

		elements_link.setTooltip(new Tooltip("Opens a link that describes the Building Product Ontology."));

		props_link.setTooltip(new Tooltip("Opens a link to the Towards a PROPS ontology presentation."));
		opm_link.setTooltip(new Tooltip("Opens a link that describes the Ontology for Property Management."));

		selectIFCFileButton.setTooltip(new Tooltip(
				"Select an IFC Step formatted file to convert.\nThe supported IFC versions are\n2x3 TC1 & Final, 4 ADD1, 4 ADD2, 4  "));
		selectTargetFileButton.setTooltip(new Tooltip(
				"Select an target file for the conversion.\nIf there will be many files, the separate files are named accorrdingly."));
		convert2RDFButton.setTooltip(new Tooltip("Press this button to start the conversion process."));
		conversionTxt.setTooltip(
				new Tooltip("This shows the conversion process related messages\nand any error that occurs. "));

		labelIFCFile.setTooltip(new Tooltip("The selected IFC file. "));

		labelTargetFile.setTooltip(new Tooltip("The selected target RDF file. "));

		labelBaseURI
				.setTooltip(new Tooltip("The base URL is the consistent part of your links generated in the output. "));

	}

	public void handle_notification(String txt) {
		conversionTxt.insertText(0, txt + "\n");
	}

	@Subscribe
	public void handleEvent(final IFCtoLBD_SystemStatusEvent event) {
		System.out.println("message: " + event.getStatus_message());
		Platform.runLater(() -> this.conversionTxt.appendText(event.getStatus_message() + "\n"));
	}

	@Subscribe
	public void handleEvent(final ProcessReadyEvent event) {
		this.masker_panel.setVisible(false);
	}
}
