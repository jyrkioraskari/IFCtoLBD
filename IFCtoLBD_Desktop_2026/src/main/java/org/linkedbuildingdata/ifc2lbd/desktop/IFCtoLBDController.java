
/*
 *  Copyright (c) 2017,2023, 2024, 2025 Jyrki Oraskari (Jyrki.Oraskari@gmail.f)
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
   Royalty Free Stock Image: Blue Glass web icons, buttons
   The File image is implemented using:
   http://www.dreamstime.com/royalty-free-stock-image-blue-glass-web-icons-buttons-image8270526
 */

package org.linkedbuildingdata.ifc2lbd.desktop;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.rdf.model.Resource;
import org.controlsfx.control.CheckTreeView;
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

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.AmbientLight;
import javafx.scene.DepthTest;
import javafx.scene.DirectionalLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

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
	private StackPane geometryViewport;

	@FXML
	private AnchorPane floatingWorkspace;

	@FXML
	private TitledPane filtersCard;

	@FXML
	private TitledPane geometryCard;

	@FXML
	private CustomTextField labelBaseURI;

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
	private ToggleSwitch 	createTrig;
	
	@FXML
	private TitledPane options_panel;
	
	
	@FXML
	private ChoiceBox<String> outputJSONorTTL;

	private ConversionSettings readInSettings;

	private record ConversionSettings(String ifcFileName, String rdfTargetName, String baseUri, int propsLevel,
			boolean hasBuildingElements, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,
			boolean hasSeparatePropertiesModel, boolean hasPropertiesBlankNodes, boolean hasGeolocation,
			boolean hasGeometry, boolean exportIfcOwl, boolean hasPerformanceBoost, boolean hasBoundingBoxWkt,
			boolean hasInterfaces, boolean hasUnits, boolean hasHierarchicalNaming, boolean hasSimpleProperties,
			boolean hasIfcBasedElements, boolean createTrig, boolean exportAsJsonLd) {
	}

	private record ConversionRequest(ConversionSettings settings, Set<String> selectedTypes, Set<String> selectedPsets) {
	}

	private ConversionRequest lastSuccessfulConversionRequest;
	private ConversionRequest pendingConversionRequest;
	private SubScene geometryScene;
	private Group geometrySceneRoot;
	private Group geometryModelRoot;
	private PerspectiveCamera geometryCamera;
	private final Rotate geometryRotateX = new Rotate(-25, Rotate.X_AXIS);
	private final Rotate geometryRotateY = new Rotate(-35, Rotate.Y_AXIS);
	private final Translate geometryCameraDistance = new Translate(0, 0, -760);
	private PreviewMesh currentPreviewMesh;
	private double geometryMouseX;
	private double geometryMouseY;
	private double geometryZoom = 1.0;
	private boolean geometryPanning;
	private double floatingMouseX;
	private double floatingMouseY;
	private double floatingCardX;
	private double floatingCardY;
	private final Map<TitledPane, Double> floatingCardNormalHeights = new IdentityHashMap<>();
	private final Map<TitledPane, Timeline> floatingCardAnimations = new IdentityHashMap<>();

	private static final int MAX_PREVIEW_POINTS = 220_000;
	private static final int MAX_PREVIEW_TRIANGLES = 60_000;
	private static final double FLOATING_CARD_HEADER_HEIGHT = 38.0;
	private static final double GEOMETRY_CARD_WIDTH = 520.0;
	private static final double GEOMETRY_VIEWPORT_WIDTH = 500.0;
	private static final Pattern GEOMETRY_OBJ_LITERAL_PATTERN = Pattern.compile(
			"(?:fog:asObj_v3\\.0-obj|<https://w3id\\.org/fog#asObj_v3\\.0-obj>|\"(?:fog:)?asObj_v3\\.0-obj\"|\"https://w3id\\.org/fog#asObj_v3\\.0-obj\")\\s*:?\\s*\"([A-Za-z0-9+/=]+)\"");
	private static final Pattern GEOMETRY_MTL_KD_PATTERN = Pattern.compile(
			"(?:lbd:asMTL_kd|<https://lbd\\.org/#asMTL_kd>|\"(?:lbd:)?asMTL_kd\"|\"https://lbd\\.org/#asMTL_kd\")\\s*:?\\s*\"(#[0-9a-fA-F]{6})\"");
	private static final Pattern GEOMETRY_MTL_PATTERN = Pattern.compile(
			"(?:lbd:asMTL|<https://lbd\\.org/#asMTL>|\"(?:lbd:)?asMTL\"|\"https://lbd\\.org/#asMTL\")\\s*:?\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final Pattern MTL_KD_LINE_PATTERN = Pattern.compile(
			"(?m)^\\s*Kd\\s+([0-9]*\\.?[0-9]+)\\s+([0-9]*\\.?[0-9]+)\\s+([0-9]*\\.?[0-9]+)\\s*$");
	private static final int DEFAULT_PREVIEW_COLOR = 0x9aa8b8;

	@FXML
	private void closeApplicationAction() {
		this.eventBus.post(new IFCtoLBD_SystemExit("User selected the application exit."));
		Platform.exit();
	}

	@FXML
	private void aboutAction() {
		// get a handle to the stage
		Stage stage = (Stage) this.myMenuBar.getScene().getWindow();
		new About(stage).show();
	}

	@FXML
	private void openSettingsWorkflow() {
		toggleFloatingCard(this.options_panel);
	}

	@FXML
	private void openFiltersWorkflow() {
		toggleFloatingCard(this.filtersCard);
	}

	@FXML
	private void testGeometryWorkflow() {
		toggleFloatingCard(this.geometryCard);
	}

	@FXML
	private void validateWorkflow() {
		this.conversionTxt.appendText("Validate test is not implemented yet.\n");
	}

	@FXML
	private void queryWorkflow() {
		this.conversionTxt.appendText("Query test is not implemented yet.\n");
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
			if (fwd.exists()) {
				this.fc_ifc.setInitialDirectory(fwd.isDirectory() ? fwd : fwd.getParentFile());
			} else {
				this.fc_ifc.setInitialDirectory(new File("."));
			}
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
				this.rdfTargetName = target_directory + file.getName().substring(0, i) + "_LBD"
						+ selectedOutputExtension();
			else
				this.rdfTargetName = target_directory + File.separator + file.getName().substring(0, i) + "_LBD"
						+ selectedOutputExtension();
			this.labelTargetFile.setText(this.rdfTargetName);
		}
		if (this.ifcFileName != null && this.rdfTargetName != null) {
			this.selectTargetFileButton.setDisable(false);
			System.out.println("workdir put:" + file.getParentFile().getAbsolutePath());
			this.prefs.put("ifc_work_directory", file.getParentFile().getAbsolutePath());
			readInIFC();
		}
		this.selectIFCFileButton.setDefaultButton(false);
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
		this.fc_target.setInitialFileName(fwd.getName());
		if (fwd.getParentFile() == null || !fwd.getParentFile().exists()) {
			this.fc_target.setInitialDirectory(new File(this.ifcFileName).getParentFile());
			System.out.println("Initial Filename to: " + fwd.getName());
			this.fc_target.setInitialFileName(fwd.getName());
			System.out.println("SET");
		} else
			this.fc_target.setInitialDirectory(fwd.getParentFile());

		FileChooser.ExtensionFilter ttlFilter = new FileChooser.ExtensionFilter("Turtle files (*.ttl)", "*.ttl");
		FileChooser.ExtensionFilter jsonLdFilter = new FileChooser.ExtensionFilter("JSON-LD files (*.jsonld)", "*.jsonld");
		this.fc_target.getExtensionFilters().clear();
		this.fc_target.getExtensionFilters().addAll(ttlFilter, jsonLdFilter);
		this.fc_target.setSelectedExtensionFilter(isJsonLdOutputSelected() ? jsonLdFilter : ttlFilter);

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

	private int selectedPropertyLevel() {
		if (this.level1.isSelected()) {
			return 1;
		}
		if (this.level3.isSelected()) {
			return 3;
		}
		return 2;
	}

	private boolean isJsonLdOutputSelected() {
		String outputFormat = this.outputJSONorTTL.getValue();
		return outputFormat != null && outputFormat.toLowerCase(Locale.ROOT).contains("json");
	}

	private String selectedOutputExtension() {
		return isJsonLdOutputSelected() ? ".jsonld" : ".ttl";
	}

	private void updateTargetFileExtension() {
		if (this.rdfTargetName == null) {
			return;
		}
		String extension = selectedOutputExtension();
		int separatorIndex = Math.max(this.rdfTargetName.lastIndexOf(File.separatorChar), this.rdfTargetName.lastIndexOf('/'));
		int dotIndex = this.rdfTargetName.lastIndexOf('.');
		if (dotIndex > separatorIndex) {
			this.rdfTargetName = this.rdfTargetName.substring(0, dotIndex) + extension;
		} else {
			this.rdfTargetName = this.rdfTargetName + extension;
		}
		this.labelTargetFile.setText(this.rdfTargetName);
	}

	private ConversionSettings currentSettings() {
		return new ConversionSettings(this.ifcFileName, this.rdfTargetName, this.labelBaseURI.getText().trim(),
				selectedPropertyLevel(), this.building_elements.isSelected(),
				this.building_elements_separate_file.isSelected(), this.building_props.isSelected(),
				this.building_props_separate_file.isSelected(), this.building_props_blank_nodes.isSelected(),
				this.geolocation.isSelected(), this.geometry_elements.isSelected(), this.ifcOWL_elements.isSelected(),
				this.hasPerformanceBoost.isSelected(), this.hasBoundingBox_WKT.isSelected(),
				this.geometry_interfaces.isSelected(), this.createUnits.isSelected(),
				this.hasHierarchicalNaming.isSelected(), this.hasSimpleProperties.isSelected(),
				this.ifc_based_elements.isSelected(), this.createTrig.isSelected(), isJsonLdOutputSelected());
	}

	private boolean hasReadInSettingsChanged(ConversionSettings previous, ConversionSettings current) {
		if (previous == null) {
			return true;
		}
		return !Objects.equals(previous.ifcFileName(), current.ifcFileName())
				|| !Objects.equals(previous.baseUri(), current.baseUri())
				|| previous.propsLevel() != current.propsLevel()
				|| previous.hasBuildingElements() != current.hasBuildingElements()
				|| previous.hasBuildingProperties() != current.hasBuildingProperties()
				|| previous.hasPropertiesBlankNodes() != current.hasPropertiesBlankNodes()
				|| previous.hasGeolocation() != current.hasGeolocation()
				|| previous.hasGeometry() != current.hasGeometry()
				|| previous.exportIfcOwl() != current.exportIfcOwl()
				|| previous.hasPerformanceBoost() != current.hasPerformanceBoost()
				|| previous.hasBoundingBoxWkt() != current.hasBoundingBoxWkt()
				|| previous.hasInterfaces() != current.hasInterfaces()
				|| previous.hasUnits() != current.hasUnits();
	}

	private boolean hasConversionRequestChanged(ConversionRequest previous, ConversionRequest current) {
		if (previous == null) {
			return true;
		}
		ConversionSettings previousSettings = previous.settings();
		ConversionSettings currentSettings = current.settings();
		return hasReadInSettingsChanged(previousSettings, currentSettings)
				|| previousSettings.hasSeparateBuildingElementsModel() != currentSettings.hasSeparateBuildingElementsModel()
				|| previousSettings.hasSeparatePropertiesModel() != currentSettings.hasSeparatePropertiesModel()
				|| previousSettings.hasHierarchicalNaming() != currentSettings.hasHierarchicalNaming()
				|| previousSettings.hasSimpleProperties() != currentSettings.hasSimpleProperties()
				|| previousSettings.hasIfcBasedElements() != currentSettings.hasIfcBasedElements()
				|| !Objects.equals(previous.selectedTypes(), current.selectedTypes())
				|| !Objects.equals(previous.selectedPsets(), current.selectedPsets());
	}

	private Set<String> selectedElementTypes() {
		Set<String> selectedTypes = new HashSet<>();
		for (TreeItem<String> item : this.element_types_checkbox.getCheckModel().getCheckedItems()) {
			selectedTypes.add(item.getValue());
		}
		return selectedTypes;
	}

	private Set<String> selectedPropertySets() {
		Set<String> selectedPsets = new HashSet<>();
		for (TreeItem<String> item : this.propertysets_checkbox.getCheckModel().getCheckedItems()) {
			selectedPsets.add(item.getValue());
		}
		return selectedPsets;
	}

	private void persistSettings(ConversionSettings settings) {
		this.prefs.putBoolean("lbd_building_elements", settings.hasBuildingElements());
		this.prefs.putBoolean("lbd_building_elements_separate_file", settings.hasSeparateBuildingElementsModel());
		this.prefs.putBoolean("lbd_building_props", settings.hasBuildingProperties());
		this.prefs.putBoolean("lbd_building_props_blank_nodes", settings.hasPropertiesBlankNodes());
		this.prefs.putBoolean("lbd_building_props_separate_file", settings.hasSeparatePropertiesModel());
		this.prefs.put("lbd_props_base_url", settings.baseUri());
		this.prefs.putBoolean("lbd_boundinbox_elements", settings.hasGeometry());
		this.prefs.putBoolean("lbd_boundinbox_interfaces", settings.hasInterfaces());
		this.prefs.putBoolean("lbd_boundinbox_wkt", settings.hasBoundingBoxWkt());
		this.prefs.putBoolean("lbd_ifcOWL_elements", settings.exportIfcOwl());
		this.prefs.putBoolean("lbd_performance", settings.hasPerformanceBoost());
		this.prefs.putBoolean("lbd_createUnits", settings.hasUnits());
		this.prefs.putBoolean("lbd_geolocation", settings.hasGeolocation());
		this.prefs.putBoolean("lbd_hasHierarchicalNaming", settings.hasHierarchicalNaming());
		this.prefs.putBoolean("lbd_hasSimpleProperties", settings.hasSimpleProperties());
		this.prefs.putBoolean("ifc_based_elements", settings.hasIfcBasedElements());
		this.prefs.putBoolean("createTrig", settings.createTrig());
		this.prefs.putBoolean("export_as_jsonld", settings.exportAsJsonLd());
		this.prefs.putInt("lbd_props_level", settings.propsLevel());
	}

	private void readInIFC() {
		ConversionSettings settings = currentSettings();
		this.readInSettings = settings;
		readInIFCExecute(settings);
	}

	private void readInIFCExecute(ConversionSettings settings) {
		persistSettings(settings);
		setRunReady(false);
		this.conversionTxt.setText("");
		clearGeometryPreview("Convert with geometry enabled to preview the model.");
		try {
				if (this.running_read_in != null && !this.running_read_in.isDone()) {
					this.conversionTxt.appendText("\nThe last conversion is still running. \n");
					return;
				}
			this.running_read_in = this.executor
					.submit(new ReadinInThread(settings.ifcFileName(), settings.baseUri(), settings.rdfTargetName(),
							settings.propsLevel(), settings.hasBuildingElements(),
							settings.hasSeparateBuildingElementsModel(), settings.hasBuildingProperties(),
							settings.hasSeparatePropertiesModel(), settings.hasPropertiesBlankNodes(),
							settings.hasGeolocation(), settings.hasGeometry(), settings.exportIfcOwl(),
							settings.hasUnits(), settings.hasPerformanceBoost(), settings.hasBoundingBoxWkt(),
							settings.hasInterfaces()));
		} catch (Exception e) {
			this.options_panel.setDisable(false);
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}
	}

	private void setupGeometryPreview() {
		this.geometryCard.setMinWidth(GEOMETRY_CARD_WIDTH);
		this.geometryCard.setPrefWidth(GEOMETRY_CARD_WIDTH);
		this.geometryCard.setMaxWidth(GEOMETRY_CARD_WIDTH);
		this.geometryViewport.setMinWidth(GEOMETRY_VIEWPORT_WIDTH);
		this.geometryViewport.setPrefWidth(GEOMETRY_VIEWPORT_WIDTH);
		this.geometryViewport.setMaxWidth(GEOMETRY_VIEWPORT_WIDTH);

		this.geometryModelRoot = new Group();
		this.geometryModelRoot.setDepthTest(DepthTest.ENABLE);
		this.geometryModelRoot.getTransforms().addAll(this.geometryRotateX, this.geometryRotateY);

		AmbientLight ambientLight = new AmbientLight(Color.rgb(190, 190, 190));
		DirectionalLight keyLight = new DirectionalLight(Color.rgb(235, 235, 235));
		keyLight.setTranslateX(-360);
		keyLight.setTranslateY(-420);
		keyLight.setTranslateZ(-520);

		this.geometrySceneRoot = new Group(this.geometryModelRoot, ambientLight, keyLight);
		this.geometrySceneRoot.setDepthTest(DepthTest.ENABLE);

		this.geometryCamera = new PerspectiveCamera(true);
		this.geometryCamera.setNearClip(0.1);
		this.geometryCamera.setFarClip(10_000);
		this.geometryCamera.getTransforms().add(this.geometryCameraDistance);

		this.geometryScene = new SubScene(this.geometrySceneRoot, 320, 240, true, SceneAntialiasing.BALANCED);
		this.geometryScene.widthProperty().bind(this.geometryViewport.widthProperty());
		this.geometryScene.heightProperty().bind(this.geometryViewport.heightProperty());
		this.geometryScene.setCamera(this.geometryCamera);
		this.geometryScene.setFill(Color.rgb(248, 250, 252));
		this.geometryScene.setFocusTraversable(true);
		this.geometryScene.setOnMousePressed(event -> {
			bringFloatingCardToFront(this.geometryCard);
			this.geometryMouseX = event.getSceneX();
			this.geometryMouseY = event.getSceneY();
			this.geometryPanning = event.getButton() == MouseButton.MIDDLE || event.getButton() == MouseButton.SECONDARY;
			this.geometryScene.requestFocus();
		});
		this.geometryScene.setOnMouseDragged(event -> {
			double dx = event.getSceneX() - this.geometryMouseX;
			double dy = event.getSceneY() - this.geometryMouseY;
			if (this.geometryPanning) {
				this.geometryModelRoot.setTranslateX(this.geometryModelRoot.getTranslateX() + dx);
				this.geometryModelRoot.setTranslateY(this.geometryModelRoot.getTranslateY() + dy);
			} else {
				this.geometryRotateY.setAngle(this.geometryRotateY.getAngle() + dx * 0.35);
				this.geometryRotateX.setAngle(clamp(this.geometryRotateX.getAngle() - dy * 0.35, -90, 90));
			}
			this.geometryMouseX = event.getSceneX();
			this.geometryMouseY = event.getSceneY();
			event.consume();
		});
		this.geometryScene.setOnScroll(event -> {
			double zoomFactor = event.getDeltaY() > 0 ? 1.12 : 0.89;
			this.geometryZoom = clamp(this.geometryZoom * zoomFactor, 0.25, 6.0);
			updateGeometryCamera();
			event.consume();
		});

		Button fitButton = new Button("Fit");
		fitButton.getStyleClass().add("geometry-tool-button");
		fitButton.setFocusTraversable(false);
		fitButton.setOnAction(event -> {
			fitGeometryPreview();
			event.consume();
		});
		StackPane.setAlignment(fitButton, Pos.TOP_RIGHT);

		this.geometryViewport.getChildren().setAll(this.geometryScene, fitButton);
		clearGeometryPreview("Select geometry and convert to preview the model.");
	}

	private void setupFloatingCards() {
		setupFloatingCard(this.options_panel, "Settings");
		setupFloatingCard(this.filtersCard, "Filters");
		setupFloatingCard(this.geometryCard, "Geometry Preview");
		this.floatingWorkspace.widthProperty().addListener((observable, oldValue, newValue) -> clampFloatingCardsToWorkspace());
		this.floatingWorkspace.heightProperty().addListener((observable, oldValue, newValue) -> clampFloatingCardsToWorkspace());
		Platform.runLater(this::clampFloatingCardsToWorkspace);
	}

	private void setRunReady(boolean ready) {
		if (this.convert2RDFButton == null) {
			return;
		}
		this.convert2RDFButton.setDisable(!ready);
		this.convert2RDFButton.setDefaultButton(ready);
		this.convert2RDFButton.getStyleClass().removeAll("workflow-button", "workflow-run-button");
		this.convert2RDFButton.getStyleClass().add(ready ? "workflow-run-button" : "workflow-button");
		if (ready) {
			this.selectIFCFileButton.setDefaultButton(false);
		}
	}

	private void setupFloatingCard(TitledPane card, String title) {
		if (card == null) {
			return;
		}
		card.setText("");
		card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		card.setGraphic(createFloatingCardHeader(card, title));
		this.floatingCardNormalHeights.put(card, card.getPrefHeight());
		if (!card.isExpanded()) {
			minimizeFloatingCard(card, false);
		}
		addFloatingCardBehavior(card);
	}

	private HBox createFloatingCardHeader(TitledPane card, String title) {
		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("floating-card-title-label");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Button minimizeButton = new Button("_");
		minimizeButton.getStyleClass().add("floating-card-window-button");
		minimizeButton.setFocusTraversable(false);
		minimizeButton.setOnAction(event -> {
			card.toFront();
			minimizeFloatingCard(card);
			event.consume();
		});

		Button maximizeButton = new Button("□");
		maximizeButton.getStyleClass().add("floating-card-window-button");
		maximizeButton.setFocusTraversable(false);
		maximizeButton.setOnAction(event -> {
			bringFloatingCardToFront(card);
			event.consume();
		});

		HBox header = new HBox(6, titleLabel, spacer, minimizeButton, maximizeButton);
		header.getStyleClass().add("floating-card-title-bar");
		header.setAlignment(Pos.CENTER_LEFT);
		header.minWidthProperty().bind(card.widthProperty().subtract(34));
		header.prefWidthProperty().bind(card.widthProperty().subtract(34));
		header.maxWidthProperty().bind(card.widthProperty().subtract(34));
		return header;
	}

	private void addFloatingCardBehavior(TitledPane card) {
		if (card == null) {
			return;
		}
		card.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			if (event.getButton() == MouseButton.PRIMARY && !(event.getTarget() instanceof Button)
					&& !isGeometrySceneEventTarget(event.getTarget())) {
				bringFloatingCardToFront(card);
			}
			if (!isCardHeaderEvent(event)) {
				return;
			}
			this.floatingMouseX = event.getSceneX();
			this.floatingMouseY = event.getSceneY();
			this.floatingCardX = card.getLayoutX();
			this.floatingCardY = card.getLayoutY();
			event.consume();
		});
		card.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
			if (!isCardHeaderEvent(event)) {
				return;
			}
			double nextX = this.floatingCardX + event.getSceneX() - this.floatingMouseX;
			double nextY = this.floatingCardY + event.getSceneY() - this.floatingMouseY;
			card.setLayoutX(clamp(nextX, 0, Math.max(0, this.floatingWorkspace.getWidth() - card.getWidth())));
			card.setLayoutY(clamp(nextY, 0, Math.max(0, this.floatingWorkspace.getHeight() - FLOATING_CARD_HEADER_HEIGHT)));
			event.consume();
		});
		card.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
			if (isCardHeaderEvent(event)) {
				event.consume();
			}
		});
		card.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
			if (isCardHeaderEvent(event)) {
				event.consume();
			}
		});
	}

	private void clampFloatingCardsToWorkspace() {
		clampFloatingCardToWorkspace(this.options_panel);
		clampFloatingCardToWorkspace(this.filtersCard);
		clampFloatingCardToWorkspace(this.geometryCard);
	}

	private void clampFloatingCardToWorkspace(TitledPane card) {
		if (card == null || this.floatingWorkspace == null) {
			return;
		}
		double workspaceWidth = this.floatingWorkspace.getWidth();
		double workspaceHeight = this.floatingWorkspace.getHeight();
		if (workspaceWidth <= 0 || workspaceHeight <= 0) {
			return;
		}
		double cardWidth = card.getWidth() > 0 ? card.getWidth() : card.getPrefWidth();
		double cardHeight = card.getHeight() > 0 ? card.getHeight() : card.getPrefHeight();
		double maxX = Math.max(0, workspaceWidth - cardWidth);
		double maxY = Math.max(0, workspaceHeight - Math.max(FLOATING_CARD_HEADER_HEIGHT, cardHeight));
		card.setLayoutX(clamp(card.getLayoutX(), 0, maxX));
		card.setLayoutY(clamp(card.getLayoutY(), 0, maxY));
	}

	private boolean isCardHeaderEvent(MouseEvent event) {
		return event.getButton() == MouseButton.PRIMARY && event.getY() <= FLOATING_CARD_HEADER_HEIGHT
				&& !isButtonEventTarget(event.getTarget());
	}

	private boolean isButtonEventTarget(Object target) {
		if (!(target instanceof Node node)) {
			return target instanceof Button;
		}
		while (node != null) {
			if (node instanceof Button) {
				return true;
			}
			node = node.getParent();
		}
		return false;
	}

	private boolean isGeometrySceneEventTarget(Object target) {
		if (!(target instanceof Node node)) {
			return false;
		}
		while (node != null) {
			if (node == this.geometryScene) {
				return true;
			}
			node = node.getParent();
		}
		return false;
	}

	private void toggleFloatingCard(TitledPane card) {
		if (card == null) {
			return;
		}
		card.setDisable(false);
		if (card.isExpanded() || card.getPrefHeight() > FLOATING_CARD_HEADER_HEIGHT + 1) {
			minimizeFloatingCard(card);
		} else {
			bringFloatingCardToFront(card);
		}
	}

	private void bringFloatingCardToFront(TitledPane card) {
		if (card == null) {
			return;
		}
		card.setDisable(false);
		if (!card.isExpanded() || card.getPrefHeight() <= FLOATING_CARD_HEADER_HEIGHT + 1) {
			restoreFloatingCard(card, false);
		}
		card.toFront();
	}

	private void minimizeFloatingCard(TitledPane card) {
		minimizeFloatingCard(card, true);
	}

	private void minimizeFloatingCard(TitledPane card, boolean animate) {
		if (card == null) {
			return;
		}
		stopFloatingCardAnimation(card);
		if (card.isExpanded() && card.getPrefHeight() > FLOATING_CARD_HEADER_HEIGHT) {
			this.floatingCardNormalHeights.put(card, card.getPrefHeight());
		}
		if (!animate) {
			card.setExpanded(false);
			card.setMinHeight(FLOATING_CARD_HEADER_HEIGHT);
			card.setPrefHeight(FLOATING_CARD_HEADER_HEIGHT);
			card.setMaxHeight(FLOATING_CARD_HEADER_HEIGHT);
			renderGeometryScene();
			return;
		}

		double currentHeight = card.getHeight() > FLOATING_CARD_HEADER_HEIGHT ? card.getHeight() : card.getPrefHeight();
		if (currentHeight <= FLOATING_CARD_HEADER_HEIGHT) {
			currentHeight = this.floatingCardNormalHeights.getOrDefault(card, FLOATING_CARD_HEADER_HEIGHT);
		}
		card.setExpanded(true);
		card.setMinHeight(FLOATING_CARD_HEADER_HEIGHT);
		card.setMaxHeight(Double.MAX_VALUE);
		card.setPrefHeight(currentHeight);
		animateFloatingCardHeight(card, FLOATING_CARD_HEADER_HEIGHT, () -> {
			card.setExpanded(false);
			card.setMinHeight(FLOATING_CARD_HEADER_HEIGHT);
			card.setPrefHeight(FLOATING_CARD_HEADER_HEIGHT);
			card.setMaxHeight(FLOATING_CARD_HEADER_HEIGHT);
			renderGeometryScene();
		});
	}

	private void restoreFloatingCard(TitledPane card) {
		restoreFloatingCard(card, true);
	}

	private void restoreFloatingCard(TitledPane card, boolean animate) {
		if (card == null) {
			return;
		}
		stopFloatingCardAnimation(card);
		double targetHeight = this.floatingCardNormalHeights.getOrDefault(card, card.getPrefHeight());
		if (targetHeight <= FLOATING_CARD_HEADER_HEIGHT) {
			targetHeight = 320.0;
		}
		if (!animate) {
			card.setMinHeight(Region.USE_COMPUTED_SIZE);
			card.setMaxHeight(Region.USE_COMPUTED_SIZE);
			card.setPrefHeight(targetHeight);
			card.setExpanded(true);
			renderGeometryScene();
			return;
		}

		card.setExpanded(true);
		card.setMinHeight(FLOATING_CARD_HEADER_HEIGHT);
		card.setMaxHeight(Double.MAX_VALUE);
		card.setPrefHeight(Math.max(FLOATING_CARD_HEADER_HEIGHT, card.getHeight()));
		double finalTargetHeight = targetHeight;
		animateFloatingCardHeight(card, finalTargetHeight, () -> {
			card.setMinHeight(Region.USE_COMPUTED_SIZE);
			card.setMaxHeight(Region.USE_COMPUTED_SIZE);
			card.setPrefHeight(finalTargetHeight);
			renderGeometryScene();
		});
	}

	private void stopFloatingCardAnimation(TitledPane card) {
		Timeline timeline = this.floatingCardAnimations.remove(card);
		if (timeline != null) {
			timeline.stop();
		}
	}

	private void animateFloatingCardHeight(TitledPane card, double targetHeight, Runnable onFinished) {
		Timeline timeline = new Timeline(new KeyFrame(Duration.millis(180),
				new KeyValue(card.prefHeightProperty(), targetHeight, Interpolator.EASE_BOTH)));
		this.floatingCardAnimations.put(card, timeline);
		timeline.setOnFinished(event -> {
			if (this.floatingCardAnimations.get(card) != timeline) {
				return;
			}
			this.floatingCardAnimations.remove(card);
			if (onFinished != null) {
				onFinished.run();
			}
		});
		timeline.play();
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private void clearGeometryPreview(String message) {
		this.currentPreviewMesh = null;
		renderGeometryScene();
		appendGeometryPreviewMessage(message);
	}

	private void scheduleGeometryPreview(ConversionSettings settings) {
		if (settings == null || !settings.hasGeometry()) {
			Platform.runLater(() -> clearGeometryPreview("Geometry was not selected for this conversion."));
			return;
		}
		File rdfFile = new File(settings.rdfTargetName());
		if (!rdfFile.isFile()) {
			Platform.runLater(() -> clearGeometryPreview("Geometry preview unavailable: output RDF file was not found."));
			return;
		}
		Platform.runLater(() -> appendGeometryPreviewMessage("Loading geometry preview..."));
		this.executor.submit(() -> {
			try {
				PreviewMesh previewMesh = loadPreviewMesh(rdfFile);
				Platform.runLater(() -> {
					try {
						showPreviewMesh(previewMesh);
					} catch (Exception e) {
						clearGeometryPreview("Geometry preview rendering failed: " + e.getMessage());
					}
				});
			} catch (Exception e) {
				Platform.runLater(() -> clearGeometryPreview("Geometry preview failed: " + e.getMessage()));
			}
		});
	}

	private PreviewMesh loadPreviewMesh(File rdfFile) throws IOException {
		ObjMeshBuilder builder = new ObjMeshBuilder(MAX_PREVIEW_POINTS, MAX_PREVIEW_TRIANGLES);
		try (BufferedReader reader = Files.newBufferedReader(rdfFile.toPath(), StandardCharsets.UTF_8)) {
			String line;
			String pendingObj = null;
			int pendingColor = DEFAULT_PREVIEW_COLOR;
			while ((line = reader.readLine()) != null && !builder.clipped()) {
				if (pendingObj != null && startsNewRdfSubject(line)) {
					builder.addObj(pendingObj, pendingColor);
					pendingObj = null;
					pendingColor = DEFAULT_PREVIEW_COLOR;
				}
				Matcher objMatcher = GEOMETRY_OBJ_LITERAL_PATTERN.matcher(line);
				while (objMatcher.find() && !builder.clipped()) {
					if (pendingObj != null) {
						builder.addObj(pendingObj, pendingColor);
						pendingColor = DEFAULT_PREVIEW_COLOR;
					}
					try {
						pendingObj = new String(Base64.getDecoder().decode(objMatcher.group(1)), StandardCharsets.UTF_8);
					} catch (IllegalArgumentException ignored) {
						// Ignore malformed geometry literals and continue scanning the output.
						pendingObj = null;
					}
				}
				Matcher colorMatcher = GEOMETRY_MTL_KD_PATTERN.matcher(line);
				if (colorMatcher.find()) {
					pendingColor = parseHexColor(colorMatcher.group(1));
				} else {
					Matcher materialMatcher = GEOMETRY_MTL_PATTERN.matcher(line);
					if (materialMatcher.find()) {
						pendingColor = parseMtlDiffuseColor(materialMatcher.group(1), pendingColor);
					}
				}
			}
			if (pendingObj != null && !builder.clipped()) {
				builder.addObj(pendingObj, pendingColor);
			}
		}
		return builder.toPreviewMesh();
	}

	private static boolean startsNewRdfSubject(String line) {
		return !line.isBlank() && !Character.isWhitespace(line.charAt(0));
	}

	private void showPreviewMesh(PreviewMesh previewMesh) {
		if (previewMesh.triangleCount() == 0) {
			if (previewMesh.objectCount() == 0) {
				clearGeometryPreview("No OBJ geometry literals were found in the converted RDF.");
			} else {
				clearGeometryPreview("Found " + previewMesh.objectCount() + " OBJ geometry objects, but no faces were parsed.");
			}
			return;
		}

		this.currentPreviewMesh = previewMesh;
		fitGeometryPreview();
		renderGeometryScene();
		appendGeometryPreviewMessage(previewMesh.statusText());
	}

	private void appendGeometryPreviewMessage(String message) {
		if (this.conversionTxt != null && message != null && !message.isBlank()) {
			this.conversionTxt.appendText("Geometry preview: " + message + "\n");
		}
	}

	private void fitGeometryPreview() {
		this.geometryZoom = 1.0;
		this.geometryRotateX.setAngle(-25);
		this.geometryRotateY.setAngle(-35);
		if (this.geometryModelRoot != null) {
			this.geometryModelRoot.setTranslateX(0);
			this.geometryModelRoot.setTranslateY(0);
		}
		updateGeometryCamera();
	}

	private void updateGeometryCamera() {
		this.geometryCameraDistance.setZ(-760.0 / this.geometryZoom);
	}

	private void renderGeometryScene() {
		if (this.geometryModelRoot == null) {
			return;
		}
		this.geometryModelRoot.getChildren().clear();
		if (this.currentPreviewMesh == null || this.currentPreviewMesh.triangleCount() == 0) {
			return;
		}

		float[] points = this.currentPreviewMesh.points();
		int[] faces = this.currentPreviewMesh.faces();
		int[] triangleColors = this.currentPreviewMesh.triangleColors();
		Map<Integer, List<Integer>> facesByColor = new LinkedHashMap<>();
		for (int faceOffset = 0; faceOffset + 5 < faces.length; faceOffset += 6) {
			int colorIndex = faceOffset / 6;
			int color = colorIndex < triangleColors.length ? triangleColors[colorIndex] : DEFAULT_PREVIEW_COLOR;
			facesByColor.computeIfAbsent(color, ignored -> new ArrayList<>()).add(faceOffset);
		}

		for (Map.Entry<Integer, List<Integer>> entry : facesByColor.entrySet()) {
			TriangleMesh mesh = new TriangleMesh();
			mesh.getPoints().setAll(points);
			mesh.getTexCoords().setAll(0, 0);
			int[] colorFaces = new int[entry.getValue().size() * 6];
			int writeIndex = 0;
			for (int faceOffset : entry.getValue()) {
				colorFaces[writeIndex++] = faces[faceOffset];
				colorFaces[writeIndex++] = 0;
				colorFaces[writeIndex++] = faces[faceOffset + 2];
				colorFaces[writeIndex++] = 0;
				colorFaces[writeIndex++] = faces[faceOffset + 4];
				colorFaces[writeIndex++] = 0;
			}
			mesh.getFaces().setAll(colorFaces);
			mesh.getFaceSmoothingGroups().setAll(new int[entry.getValue().size()]);

			MeshView meshView = new MeshView(mesh);
			meshView.setCullFace(CullFace.NONE);
			meshView.setDrawMode(DrawMode.FILL);
			meshView.setMaterial(createPreviewMaterial(entry.getKey()));
			this.geometryModelRoot.getChildren().add(meshView);
		}
	}

	private static int parseHexColor(String value) {
		if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
			return DEFAULT_PREVIEW_COLOR;
		}
		return Integer.parseInt(value.substring(1), 16);
	}

	private static int parseMtlDiffuseColor(String escapedMtl, int fallbackColor) {
		String material = escapedMtl.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"")
				.replace("\\\\", "\\");
		Matcher matcher = MTL_KD_LINE_PATTERN.matcher(material);
		if (!matcher.find()) {
			return fallbackColor;
		}
		return toRgb(parseUnitColorChannel(matcher.group(1)), parseUnitColorChannel(matcher.group(2)),
				parseUnitColorChannel(matcher.group(3)));
	}

	private static int toRgb(int red, int green, int blue) {
		return (red << 16) | (green << 8) | blue;
	}

	private static int parseUnitColorChannel(String value) {
		double channel = Double.parseDouble(value);
		return (int) Math.round(clamp(channel, 0, 1) * 255.0);
	}

	private static PhongMaterial createPreviewMaterial(int rgb) {
		int red = (rgb >> 16) & 0xff;
		int green = (rgb >> 8) & 0xff;
		int blue = rgb & 0xff;
		Color diffuse = Color.rgb(softenColorChannel(red), softenColorChannel(green), softenColorChannel(blue));
		PhongMaterial material = new PhongMaterial(diffuse);
		material.setSpecularColor(Color.rgb(42, 42, 42));
		material.setSpecularPower(4);
		return material;
	}

	private static int softenColorChannel(int channel) {
		return (int) Math.round(channel * 0.78 + 245 * 0.22);
	}

	private record PreviewMesh(float[] points, int[] faces, int[] triangleColors, int objectCount, int triangleCount,
			boolean clipped) {
		private String statusText() {
			if (clipped) {
				return "Preview clipped at " + triangleCount + " triangles from " + objectCount + " geometry objects.";
			}
			return objectCount + " geometry objects, " + triangleCount + " triangles.";
		}
	}

	private static class ObjMeshBuilder {
		private final int maxPoints;
		private final int maxTriangles;
		private final List<double[]> rawPoints = new ArrayList<>();
		private final List<int[]> triangles = new ArrayList<>();
		private final List<Integer> triangleColors = new ArrayList<>();
		private int objectCount;
		private boolean clipped;
		private double minX = Double.POSITIVE_INFINITY;
		private double minY = Double.POSITIVE_INFINITY;
		private double minZ = Double.POSITIVE_INFINITY;
		private double maxX = Double.NEGATIVE_INFINITY;
		private double maxY = Double.NEGATIVE_INFINITY;
		private double maxZ = Double.NEGATIVE_INFINITY;

		private ObjMeshBuilder(int maxPoints, int maxTriangles) {
			this.maxPoints = maxPoints;
			this.maxTriangles = maxTriangles;
		}

		private boolean clipped() {
			return this.clipped;
		}

		private void addObj(String obj, int color) {
			if (this.clipped) {
				return;
			}
			this.objectCount++;
			List<double[]> localVertices = new ArrayList<>();
			for (String rawLine : obj.split("\\R")) {
				String line = rawLine.strip();
				if (line.startsWith("v ")) {
					addLocalVertex(line, localVertices);
				} else if (line.startsWith("f ")) {
					addFace(line, localVertices, color);
				}
				if (this.clipped) {
					return;
				}
			}
		}

		private void addLocalVertex(String line, List<double[]> localVertices) {
			if (this.rawPoints.size() >= this.maxPoints) {
				this.clipped = true;
				return;
			}
			String[] parts = line.split("\\s+");
			if (parts.length < 4) {
				return;
			}
			double x = parseDouble(parts[1]);
			double y = parseDouble(parts[2]);
			double z = parseDouble(parts[3]);
			double[] point = new double[] { x, y, z };
			localVertices.add(point);
			this.rawPoints.add(point);
			this.minX = Math.min(this.minX, x);
			this.minY = Math.min(this.minY, y);
			this.minZ = Math.min(this.minZ, z);
			this.maxX = Math.max(this.maxX, x);
			this.maxY = Math.max(this.maxY, y);
			this.maxZ = Math.max(this.maxZ, z);
		}

		private void addFace(String line, List<double[]> localVertices, int color) {
			String[] parts = line.split("\\s+");
			if (parts.length < 4) {
				return;
			}
			List<Integer> indexes = new ArrayList<>();
			int globalBase = this.rawPoints.size() - localVertices.size();
			for (int i = 1; i < parts.length; i++) {
				int pointIndex = parseObjIndex(parts[i], localVertices.size(), globalBase, this.rawPoints.size());
				if (pointIndex >= 0) {
					indexes.add(pointIndex);
				}
			}
			for (int i = 1; i < indexes.size() - 1; i++) {
				if (this.triangles.size() >= this.maxTriangles) {
					this.clipped = true;
					return;
				}
				this.triangles.add(new int[] { indexes.get(0), indexes.get(i), indexes.get(i + 1) });
				this.triangleColors.add(color);
			}
		}

		private PreviewMesh toPreviewMesh() {
			if (this.rawPoints.isEmpty() || this.triangles.isEmpty()) {
				return new PreviewMesh(new float[0], new int[0], new int[0], this.objectCount, 0, this.clipped);
			}
			double centerX = (this.minX + this.maxX) / 2.0;
			double centerY = (this.minY + this.maxY) / 2.0;
			double centerZ = (this.minZ + this.maxZ) / 2.0;
			double span = Math.max(this.maxX - this.minX, Math.max(this.maxY - this.minY, this.maxZ - this.minZ));
			double scale = span == 0 ? 1 : 260.0 / span;

			float[] points = new float[this.rawPoints.size() * 3];
			for (int i = 0; i < this.rawPoints.size(); i++) {
				double[] point = this.rawPoints.get(i);
				points[i * 3] = (float) ((point[0] - centerX) * scale);
				points[i * 3 + 1] = (float) (-(point[2] - centerZ) * scale);
				points[i * 3 + 2] = (float) ((point[1] - centerY) * scale);
			}

			int[] faces = new int[this.triangles.size() * 6];
			int[] colors = new int[this.triangles.size()];
			for (int i = 0; i < this.triangles.size(); i++) {
				int[] triangle = this.triangles.get(i);
				faces[i * 6] = triangle[0];
				faces[i * 6 + 1] = 0;
				faces[i * 6 + 2] = triangle[1];
				faces[i * 6 + 3] = 0;
				faces[i * 6 + 4] = triangle[2];
				faces[i * 6 + 5] = 0;
				colors[i] = this.triangleColors.get(i);
			}
			return new PreviewMesh(points, faces, colors, this.objectCount, this.triangles.size(), this.clipped);
		}

		private static int parseObjIndex(String token, int localVertexCount, int globalBase, int globalPointCount) {
			String indexPart = token.split("/", -1)[0];
			if (indexPart.isBlank()) {
				return -1;
			}
			int objIndex = Integer.parseInt(indexPart);
			if (objIndex < 0) {
				int localIndex = localVertexCount + objIndex;
				return localIndex >= 0 && localIndex < localVertexCount ? globalBase + localIndex : -1;
			}
			if (objIndex >= 1 && objIndex <= localVertexCount) {
				return globalBase + objIndex - 1;
			}
			if (objIndex >= 0 && objIndex < localVertexCount) {
				return globalBase + objIndex;
			}
			if (objIndex >= 0 && objIndex < globalPointCount) {
				return objIndex;
			}
			if (objIndex >= 1 && objIndex <= globalPointCount) {
				return objIndex - 1;
			}
			return -1;
		}

		private static double parseDouble(String value) {
			return Double.parseDouble(value.replace(',', '.'));
		}
	}

	@FXML
	private void convertIFCToRDF() {
		ConversionSettings currentSettings = currentSettings();
		if (currentSettings.ifcFileName() == null || currentSettings.rdfTargetName() == null) {
			this.conversionTxt.appendText("Select IFC and target files before conversion.\n");
			return;
		}
		if (this.running_read_in == null) {
			this.conversionTxt.appendText("Read-in has not started yet.\n");
			return;
		}
		if (!this.running_read_in.isDone()) {
			this.conversionTxt.appendText("Read-in is still running. Start conversion after it finishes.\n");
			return;
		}
		if (hasReadInSettingsChanged(this.readInSettings, currentSettings)) {
			try {
				this.running_read_in.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				this.conversionTxt.appendText("Read-in was interrupted.\n");
				return;
			} catch (ExecutionException e) {
				this.conversionTxt.appendText("Read-in failed: " + e.getMessage() + "\n");
				return;
			}
			this.conversionTxt.appendText("Re-running initial read due to changed settings.\n");
			this.readInSettings = currentSettings;
			readInIFCExecute(currentSettings);
			this.conversionTxt.appendText("Start conversion after read-in finishes.\n");
			return;
		}

		persistSettings(currentSettings);
			this.conversionTxt.setText("");
			try {
				if (this.running_conversion != null && !this.running_conversion.isDone()) {
					this.conversionTxt.appendText("\nThe last conversion is still running. \n");
					return;
				}
					this.options_panel.setDisable(true);

			Set<String> selected_types = selectedElementTypes();
			Set<String> selected_psets = selectedPropertySets();
					IFCtoLBDConverter converter = this.running_read_in.get();
					if (converter == null) {
						this.conversionTxt.appendText("Read-in failed. Conversion cannot continue.\n");
						this.options_panel.setDisable(false);
						return;
					}
				converter.setTargetFile(currentSettings.rdfTargetName());
				converter.setHasSimplified_properties(currentSettings.hasSimpleProperties());
				ConversionRequest conversionRequest = new ConversionRequest(currentSettings, selected_types, selected_psets);
				this.pendingConversionRequest = conversionRequest;
				if (!currentSettings.hasSeparateBuildingElementsModel()
						&& !hasConversionRequestChanged(this.lastSuccessfulConversionRequest, conversionRequest)) {
					this.running_conversion = this.executor.submit(() -> {
						try {
							converter.exportExistingOutput(currentSettings.rdfTargetName(),
									currentSettings.hasSeparatePropertiesModel(), currentSettings.createTrig(),
									currentSettings.exportAsJsonLd());
							this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.CONVERT));
						} catch (Exception e) {
							this.eventBus.post(new IFCtoLBD_SystemStatusEvent(e.getMessage()));
							this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR));
						}
						return 0;
					});
					return;
				}
			this.running_conversion = this.executor.submit(new ConversionThread(converter, selected_types, selected_psets,
					currentSettings.ifcFileName(), currentSettings.baseUri(), currentSettings.rdfTargetName(),
					currentSettings.propsLevel(), currentSettings.hasBuildingElements(),
					currentSettings.hasSeparateBuildingElementsModel(), currentSettings.hasBuildingProperties(),
					currentSettings.hasSeparatePropertiesModel(), currentSettings.hasPropertiesBlankNodes(),
					currentSettings.hasGeolocation(), currentSettings.hasGeometry(), currentSettings.exportIfcOwl(),
					currentSettings.hasUnits(), currentSettings.hasPerformanceBoost(),
					currentSettings.hasBoundingBoxWkt(), currentSettings.hasHierarchicalNaming(),
					currentSettings.hasIfcBasedElements(), currentSettings.hasInterfaces(), currentSettings.createTrig(),
					currentSettings.exportAsJsonLd()));
		} catch (Exception e) {
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.eventBus.register(this);
		this.border.widthProperty().bind(this.root.widthProperty());
		this.border.heightProperty().bind(this.root.heightProperty());
		setupGeometryPreview();
		setupFloatingCards();
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
							int dotIndex = file.getName().lastIndexOf(".");
							if (dotIndex > 0) {
								String targetDirectory = IFCtoLBDController.this.prefs.get("ifc_target_directory",
										file.getParentFile().getAbsolutePath());
								if (!new File(targetDirectory).exists()) {
									targetDirectory = file.getParent();
								}
								if (targetDirectory.endsWith("\\")) {
									IFCtoLBDController.this.rdfTargetName = targetDirectory
											+ file.getName().substring(0, dotIndex) + "_LBD"
											+ IFCtoLBDController.this.selectedOutputExtension();
								} else {
									IFCtoLBDController.this.rdfTargetName = targetDirectory + File.separator
											+ file.getName().substring(0, dotIndex) + "_LBD"
											+ IFCtoLBDController.this.selectedOutputExtension();
								}
								IFCtoLBDController.this.labelTargetFile.setText(IFCtoLBDController.this.rdfTargetName);
							}
							if (IFCtoLBDController.this.ifcFileName != null && IFCtoLBDController.this.rdfTargetName != null) {
								IFCtoLBDController.this.selectTargetFileButton.setDisable(false);
								IFCtoLBDController.this.selectIFCFileButton.setDefaultButton(false);
								IFCtoLBDController.this.prefs.put("ifc_work_directory", file.getParentFile().getAbsolutePath());
								IFCtoLBDController.this.readInIFC();
							}
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
		this.hasBoundingBox_WKT.setSelected(this.prefs.getBoolean("lbd_boundinbox_wkt", false));
		this.ifcOWL_elements.setSelected(this.prefs.getBoolean("lbd_ifcOWL_elements", false));
		this.createUnits.setSelected(this.prefs.getBoolean("lbd_createUnits", false));
		this.geolocation.setSelected(this.prefs.getBoolean("lbd_geolocation", true));
		
		this.hasHierarchicalNaming.setSelected(this.prefs.getBoolean("lbd_hasHierarchicalNaming", false));
		this.hasSimpleProperties.setSelected(this.prefs.getBoolean("lbd_hasSimpleProperties", false));

		this.ifc_based_elements.setSelected(this.prefs.getBoolean("ifc_based_elements", false));
		this.createTrig.setSelected(this.prefs.getBoolean("createTrig", false));
		
		this.hasPerformanceBoost.setSelected(this.prefs.getBoolean("lbd_performance", true));
		if (this.ifcOWL_elements.isSelected()) {
			this.hasPerformanceBoost.setSelected(false);
			this.hasPerformanceBoost.setDisable(true);
		} else {
			this.hasPerformanceBoost.setDisable(false);
		}

		int props_level = this.prefs.getInt("lbd_props_level", 1);
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

		
		
		outputJSONorTTL.getItems().addAll("Turtle TTL", "JSON-LD");
		outputJSONorTTL.setValue(this.prefs.getBoolean("export_as_jsonld", false) ? "JSON-LD" : "Turtle TTL");
		outputJSONorTTL.valueProperty().addListener((observable, oldValue, newValue) -> updateTargetFileExtension());
        
        
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

		this.geometry_interfaces.setTooltip(
				new Tooltip("Creates bounding box based BOT interfaces. "));
		
		this.createTrig.setTooltip(
				new Tooltip("If selected, creates also a RDF 1.1. TriG file (https://www.w3.org/TR/trig/). "));
	
	}

	@Override
	public void handle_notification(String txt) {
		this.conversionTxt.insertText(0, txt + "\n");
	}

	public void shutdown() {
		if (this.running_read_in != null && !this.running_read_in.isDone()) {
			this.running_read_in.cancel(true);
		}
		if (this.running_conversion != null && !this.running_conversion.isDone()) {
			this.running_conversion.cancel(true);
		}
		this.executor.shutdownNow();
		try {
			this.eventBus.unregister(this);
		} catch (IllegalArgumentException ignored) {
			// Already unregistered or not registered.
		}
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
			Platform.runLater(() -> {
				this.options_panel.setDisable(false);
			});

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
					setRunReady(true);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					setRunReady(false);
					e.printStackTrace();
				}
			});
		}
		if (event.getPhase() == ProcessReadyEvent.CONVERT) {
			ConversionRequest successfulRequest = this.pendingConversionRequest;
			this.lastSuccessfulConversionRequest = successfulRequest;
			if (successfulRequest != null) {
				scheduleGeometryPreview(successfulRequest.settings());
			}
		}
		if (event.getPhase() == ProcessReadyEvent.ERROR) {
			this.pendingConversionRequest = null;
			Platform.runLater(() -> setRunReady(false));
			Platform.runLater(() -> clearGeometryPreview("Geometry preview unavailable because conversion failed."));
		}
	}
}
