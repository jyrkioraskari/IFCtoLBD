
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Deque;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.parser.Shape;
import org.apache.jena.shacl.validation.ReportEntry;
import org.controlsfx.control.CheckTreeView;
import org.controlsfx.control.ToggleSwitch;
import org.controlsfx.control.textfield.CustomTextField;
import org.linkedbuildingdata.ifc2lbd.IFCtoLBDConverter;
import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemErrorEvent;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemStatusEvent;
import org.linkedbuildingdata.ifc2lbd.core.IFCtoLBDConverterCore.IfcModelStatistics;
import org.linkedbuildingdata.ifc2lbd.messages.ProcessReadyEvent;
import org.linkedbuildingdata.ifc2lbd.messages.WorkflowProgressEvent;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
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
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
import javafx.scene.layout.VBox;
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

	private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getDefaultEventBus();
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
	private ToggleSwitch materialsBmo;

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
	private Button basicSelectIFCFileButton;

	@FXML
	private Label basicLabelIFCFile;

	@FXML
	private Button selectTargetFileButton;

	@FXML
	private TextField labelTargetFile;

	@FXML
	private Button convert2RDFButton;

	@FXML
	private Button basicConvert2RDFButton;

	@FXML
	private TextArea conversionTxt;

	@FXML
	private TabPane workflowTabs;

	@FXML
	private Region workflowStatusCard;

	@FXML
	private Label workflowStatusLabel;

	@FXML
	private Label workflowElapsedLabel;

	@FXML
	private ProgressBar workflowProgressBar;

	@FXML
	private Button cancelWorkflowButton;

	@FXML
	private TitledPane modelSummaryCard;
	private String collapsedModelSummaryTitle = "Model Summary";

	@FXML
	private Label summarySchemaLabel;

	@FXML
	private Label summaryFileSizeLabel;

	@FXML
	private Label summaryElementsLabel;

	@FXML
	private Label summaryPropertySetsLabel;

	@FXML
	private Label summaryStoreysLabel;

	@FXML
	private Label summarySpacesLabel;

	@FXML
	private Label summaryGeometryLabel;

	@FXML
	private Label summaryMajorTypesLabel;

	@FXML
	private Label summaryWarningsLabel;

	@FXML
	private Button filtersWorkflowButton;

	@FXML
	private Button geometryWorkflowButton;

	@FXML
	private Button validateWorkflowButton;

	@FXML
	private Button queryWorkflowButton;

	@FXML
	private Button copyCommandLineButton;

	@FXML
	private Label generatedResultsStatusLabel;

	@FXML
	private VBox proposedOutputSection;

	@FXML
	private Label proposedFilenamesLabel;

	@FXML
	private VBox commandLinePreviewSection;

	@FXML
	private TextArea commandLinePreviewTxt;

	@FXML
	private TitledPane sparqlQueryCard;

	@FXML
	private TitledPane validateCard;

	@FXML
	private Button loadShaclButton;

	@FXML
	private Label validateStatusLabel;

	@FXML
	private ListView<ShapeValidationItem> shaclShapesList;

	@FXML
	private TreeView<ValidationTreeEntry> validationFindingsTree;

	@FXML
	private Label validationCountsLabel;

	@FXML
	private ChoiceBox<String> validationSeverityFilter;

	@FXML
	private ChoiceBox<String> validationShapeFilter;

	@FXML
	private TextField validationObjectFilter;

	@FXML
	private TextArea validationInspectorTxt;

	@FXML
	private Button validationFocusButton;

	@FXML
	private TextArea sparqlEditorTxt;

	@FXML
	private TextArea sparqlResultsTxt;

	@FXML
	private TableView<SparqlRow> sparqlResultsTable;

	@FXML
	private Label sparqlResultsStatusLabel;

	@FXML
	private ChoiceBox<String> sparqlTemplateChoice;

	@FXML
	private Button copySparqlResultsButton;

	@FXML
	private Button exportSparqlResultsButton;

	@FXML
	private Button runSparqlQueryButton;

	@FXML
	private StackPane geometryViewport;

	@FXML
	private Slider geometryExplodeSlider;

	@FXML
	private Label geometryExplodeValueLabel;

	@FXML
	private Label selectedObjectHeadingLabel;

	@FXML
	private Label selectedObjectTypeLabel;

	@FXML
	private Label selectedObjectGuidLabel;

	@FXML
	private Label selectedObjectUriLabel;

	@FXML
	private TextArea selectedObjectPropertiesTxt;

	@FXML
	private AnchorPane floatingWorkspace;

	@FXML
	private TitledPane filtersCard;

	@FXML
	private TitledPane geometryCard;

	@FXML
	private CustomTextField labelBaseURI;

	@FXML
	private Label baseUriLabel;

	@FXML
	private Label propertyLevelLabel;

	@FXML
	private Label performanceConflictLabel;

	FileChooser fc_ifc;
	FileChooser fc_target;

	@FXML
	private ToggleSwitch geometry_elements;

	@FXML
	private ToggleSwitch geometry_interfaces;

	
	@FXML
	private ToggleSwitch hasBoundingBox_WKT;

	@FXML
	private ToggleSwitch hasElementWireframe;

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
	private ToggleSwitch propertiesAsPropertySets;

	@FXML
	private ToggleSwitch ifc_based_elements;
	
	
	@FXML
	private ToggleSwitch 	createTrig;
	
	@FXML
	private TitledPane options_panel;

	@FXML
	private ChoiceBox<ConversionPreset> presetChoice;

	@FXML
	private Label presetStatusLabel;

	private final Map<String, ConversionPreset> userPresets = new LinkedHashMap<>();
	private boolean updatingPresetChoice;
	
	
	@FXML
	private ChoiceBox<String> outputJSONorTTL;

	@FXML
	private ChoiceBox<String> basicOutputJSONorTTL;

	private ConversionSettings readInSettings;

	private record ConversionSettings(String ifcFileName, String rdfTargetName, String baseUri, int propsLevel,
			boolean hasBuildingElements, boolean hasMaterialsBmo, boolean hasSeparateBuildingElementsModel, boolean hasBuildingProperties,
			boolean hasSeparatePropertiesModel, boolean hasPropertiesBlankNodes, boolean hasGeolocation,
			boolean hasGeometry, boolean exportIfcOwl, boolean hasPerformanceBoost, boolean hasBoundingBoxWkt,
			boolean hasInterfaces, boolean hasElementWireframe, boolean hasUnits, boolean hasHierarchicalNaming,
			boolean hasSimpleProperties, boolean propertiesAsPropertySets, boolean hasIfcBasedElements,
			boolean createTrig, boolean exportAsJsonLd, boolean exportAsIcdd) {
	}

	private record ConversionRequest(ConversionSettings settings, Set<String> selectedTypes, Set<String> selectedPsets) {
	}

	record ModelSummary(String schema, String fileSize, int elementCount, int mappedElementCount,
			int propertySetTypeCount, int propertySetOccurrenceCount, int storeyCount, int spaceCount,
			String geometryStatus, String majorTypes, List<String> warnings) {
	}

	private ConversionRequest lastSuccessfulConversionRequest;
	private ConversionRequest pendingConversionRequest;
	private boolean generatedResultsStale;
	private boolean runAfterReadIn;
	private Future<?> running_validation;
	private long operationSequence;
	private volatile long activeOperationId = -1;
	private long workflowStartedNanos;
	private Timeline workflowElapsedTimeline;
	private SubScene geometryScene;
	private Group geometrySceneRoot;
	private Group geometryModelRoot;
	private PerspectiveCamera geometryCamera;
	private Label geometryEmptyStateLabel;
	private Button geometryFitButton;
	private final Rotate geometryRotateX = new Rotate(-25, Rotate.X_AXIS);
	private final Rotate geometryRotateY = new Rotate(-35, Rotate.Y_AXIS);
	private final Translate geometryCameraDistance = new Translate(0, 0, -760);
	private PreviewMesh currentPreviewMesh;
	private final Map<String, List<MeshView>> geometryViewsByResource = new LinkedHashMap<>();
	private final Map<String, Point3D> geometryExplosionVectors = new LinkedHashMap<>();
	private final Map<String, GeometryBounds> geometryBoundsByResource = new LinkedHashMap<>();
	private final Map<MeshView, PhongMaterial> geometryMaterials = new IdentityHashMap<>();
	private String selectedResourceUri;
	private String selectedPropertyPath;
	private boolean sparqlSelectionHighlighted;
	private String isolatedElementType;
	private boolean queryDataAvailable;
	private long outputRevision;
	private final ExplorationModelCache explorationModelCache = new ExplorationModelCache(IFCtoLBDController::readExplorationModel);
	private SelectResults currentSelectResults;
	private String sparqlResultMetrics = "";
	private long validationRevision;
	private boolean sparqlCardPositioned;
	private final List<LoadedShapes> loadedShapes = new ArrayList<>();
	private final ObservableList<ShapeValidationItem> shapeValidationItems = FXCollections.observableArrayList();
	private ValidationFindings.Finding selectedValidationFinding;
	private boolean refreshingValidationFilters;
	private double geometryMouseX;
	private double geometryMouseY;
	private double geometryZoom = 1.0;
	private boolean geometryPanning;
	private double floatingMouseX;
	private double floatingMouseY;
	private double floatingCardX;
	private double floatingCardY;
	private Button pressedFloatingWindowButton;
	private final Map<Button, Runnable> floatingWindowButtonActions = new IdentityHashMap<>();
	private final Map<Button, TitledPane> floatingWindowButtonCards = new IdentityHashMap<>();
	private final Map<TitledPane, Double> floatingCardNormalHeights = new IdentityHashMap<>();
	private final Map<TitledPane, Timeline> floatingCardAnimations = new IdentityHashMap<>();

	private static final int MAX_PREVIEW_POINTS = 220_000;
	private static final int MAX_PREVIEW_TRIANGLES = 60_000;
	private static final double FLOATING_CARD_HEADER_HEIGHT = 38.0;
	private static final double FLOATING_CARD_WINDOW_BUTTON_WIDTH = 24.0;
	private static final double FLOATING_CARD_WINDOW_BUTTON_HEIGHT = 22.0;
	private static final double FLOATING_CARD_INITIAL_X = 14.0;
	private static final double FLOATING_CARD_INITIAL_Y = 14.0;
	private static final double FLOATING_CARD_INITIAL_GAP = 12.0;
	private static final double GEOMETRY_CARD_WIDTH = 560.0;
	private static final double GEOMETRY_VIEWPORT_WIDTH = 540.0;
	private static final String GEOMETRY_OBJ_PROPERTY = "https://w3id.org/fog#asObj_v3.0-obj";
	private static final String GEOMETRY_MTL_PROPERTY = "https://lbd.org/#asMTL";
	private static final String GEOMETRY_MTL_KD_PROPERTY = "https://lbd.org/#asMTL_kd";
	private static final String OMG_HAS_GEOMETRY = "https://w3id.org/omg#hasGeometry";
	private static final String BOT_ELEMENT = "https://w3id.org/bot#Element";
	private static final Pattern GUID_PATTERN = Pattern.compile(
			"(?i)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
	private static final Pattern MTL_KD_LINE_PATTERN = Pattern.compile(
			"(?m)^\\s*Kd\\s+([0-9]*\\.?[0-9]+)\\s+([0-9]*\\.?[0-9]+)\\s+([0-9]*\\.?[0-9]+)\\s*$");
	private static final int DEFAULT_PREVIEW_COLOR = 0x9aa8b8;
	private static final int MAX_SPARQL_RESULT_ROWS = 1_000;
	private static final String DEFAULT_SHACL_RESOURCE = "SHACL_rulesetLevel1.ttl";
	private static final String DEFAULT_SPARQL_QUERY = """
			PREFIX bot: <https://w3id.org/bot#>

			SELECT DISTINCT ?element ?type WHERE {
			  ?element a bot:Element .
			  OPTIONAL {
			    ?element a ?specificType .
			    FILTER(?specificType != bot:Element)
			  }
			  BIND(COALESCE(?specificType, bot:Element) AS ?type)
			}
			ORDER BY ?type ?element
			""";
	private static final String SPACES_BY_STOREY_QUERY = """
			PREFIX bot: <https://w3id.org/bot#>

			SELECT ?storey ?space WHERE {
			  ?storey bot:hasSpace ?space .
			}
			ORDER BY ?storey ?space
			""";
	private static final String ELEMENTS_MISSING_PROPERTIES_QUERY = """
			PREFIX bot: <https://w3id.org/bot#>

			SELECT ?element WHERE {
			  ?element a bot:Element .
			  FILTER NOT EXISTS {
			    ?element ?property ?value .
			    FILTER(STRSTARTS(STR(?property), "http://lbd.arch.rwth-aachen.de/props#") ||
				           STRSTARTS(STR(?property), "https://identifier.buildingsmart.org/uri/buildingsmart/ifc/4.3/prop/") ||
			           ?property = <https://w3id.org/ifc2lbd/bsdd-meta#hasPropertySet>)
			  }
			}
			ORDER BY ?element
			""";

	static record SparqlCell(String text, String resourceUri) {
	}

	static record SparqlRow(Map<String, SparqlCell> cells) {
	}

	static record SelectResults(List<String> variables, List<SparqlRow> rows, boolean limited) {
	}

	private record SparqlOutput(SelectResults select, String text, long elapsedMillis) {
	}

	private record LoadedShapes(File file, String name, Shapes shapes, List<ShapeValidationItem> items,
			boolean builtIn) {
	}

	private record ValidationTreeEntry(String label, ValidationFindings.Finding finding) {
	}

	private record ShapeValidationResult(ShapeValidationItem item, Boolean conforms, String message,
			List<ValidationFindings.Finding> violations) {
	}

	private static final class ShapeValidationItem {
		private final String sourceName;
		private final String displayName;
		private final org.apache.jena.graph.Node shapeNode;
		private final String persistentKey;
		private final Set<org.apache.jena.graph.Node> constraintNodes;
		private Boolean conforms;
		private String message;
		private List<ValidationFindings.Finding> violations = List.of();

		private ShapeValidationItem(String sourceName, String displayName, org.apache.jena.graph.Node shapeNode,
				Set<org.apache.jena.graph.Node> constraintNodes) {
			this(sourceName, displayName, shapeNode, shapeNode.toString(), constraintNodes);
		}

		private ShapeValidationItem(String sourceName, String displayName, org.apache.jena.graph.Node shapeNode,
				String persistentKey,
				Set<org.apache.jena.graph.Node> constraintNodes) {
			this.sourceName = sourceName;
			this.displayName = displayName;
			this.shapeNode = shapeNode;
			this.persistentKey = persistentKey;
			this.constraintNodes = constraintNodes;
			this.message = "Not tested yet";
		}
	}

	private record ResourceDetails(String uri, String ifcType, Set<String> typeNames, String guid,
			List<String> properties) {
	}

	private record GeometryRenderKey(String resourceUri, int color) {
	}

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
		if (!isFiltersAvailable()) {
			this.conversionTxt.appendText("Read an IFC file before opening Filters.\n");
			return;
		}
		toggleFloatingCard(this.filtersCard);
	}

	@FXML
	private void testGeometryWorkflow() {
		if (!isGeometryPreviewAvailable()) {
			this.conversionTxt.appendText("Run a conversion before opening Geometry Preview.\n");
			return;
		}
		toggleFloatingCard(this.geometryCard);
	}

	@FXML
	private void validateWorkflow() {
		if (!isValidationAvailable()) {
			this.conversionTxt.appendText("Generate an RDF output before opening Validate.\n");
			return;
		}
		alignFloatingCardsIfNeeded();
		toggleFloatingCard(this.validateCard);
		validateLoadedShapesAsync();
	}

	@FXML
	private void loadShaclShapes() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Load SHACL shapes");
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Turtle files (*.ttl)", "*.ttl"),
				new FileChooser.ExtensionFilter("All Files", "*.*"));
		File selectedFile = fileChooser.showOpenDialog(this.root.getScene().getWindow());
		if (selectedFile != null) {
			loadShapesFileAsync(selectedFile, selectedFile.getName());
		}
	}

	@FXML
	private void restoreDefaultShaclShapes() {
		this.loadedShapes.clear();
		this.shapeValidationItems.clear();
		loadDefaultShapes();
		rememberAddedShapes();
		if (this.shaclShapesList != null) {
			this.shaclShapesList.refresh();
		}
		refreshValidationFindings();
		if (isValidationAvailable()) {
			validateLoadedShapesAsync();
		}
	}

	@FXML
	private void queryWorkflow() {
		if (!isSparqlQueryAvailable()) {
			this.conversionTxt.appendText("Generate an RDF output before opening SPARQL Query.\n");
			return;
		}
		alignFloatingCardsIfNeeded();
		toggleFloatingCard(this.sparqlQueryCard);
	}

	@FXML
	private void runSparqlQuery() {
		if (!isSparqlQueryAvailable()) {
			showSparqlMessage("Generate an RDF output before running a SPARQL query.");
			return;
		}
		String queryText = this.sparqlEditorTxt.getText();
		if (queryText == null || queryText.isBlank()) {
			showSparqlMessage("Enter a SPARQL query.");
			return;
		}
		this.runSparqlQueryButton.setDisable(true);
		showSparqlMessage("Running query...");
		long revision = this.outputRevision;
		File outputFile = new File(this.lastSuccessfulConversionRequest.settings().rdfTargetName());
		this.executor.submit(() -> {
			long started = System.nanoTime();
			try (ExplorationModelCache.Lease lease = this.explorationModelCache.acquire(outputFile, revision)) {
				SparqlOutput result = executeSparqlQueryData(lease.model(), queryText, started);
				Platform.runLater(() -> {
					if (revision == this.outputRevision) showSparqlOutput(result);
				});
			} catch (Exception e) {
				Platform.runLater(() -> {
					if (revision == this.outputRevision)
						showSparqlMessage("SPARQL query failed: " + e.getMessage());
				});
			} finally {
				Platform.runLater(() -> {
					if (revision == this.outputRevision)
						this.runSparqlQueryButton.setDisable(!isSparqlQueryAvailable());
				});
			}
		});
	}

	@FXML
	private void copySparqlResults() {
		if (this.currentSelectResults == null) return;
		ClipboardContent content = new ClipboardContent();
		content.putString(selectResultsAsCsv(this.currentSelectResults, this.sparqlResultsTable.getItems()));
		Clipboard.getSystemClipboard().setContent(content);
		this.sparqlResultsStatusLabel.setText(this.sparqlResultMetrics + " · Copied CSV");
	}

	@FXML
	private void exportSparqlResults() {
		if (this.currentSelectResults == null) return;
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Export SPARQL results");
		chooser.setInitialFileName("sparql-results.csv");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
		File destination = chooser.showSaveDialog(this.root.getScene().getWindow());
		if (destination == null) return;
		try {
			Files.writeString(destination.toPath(), selectResultsAsCsv(this.currentSelectResults,
					this.sparqlResultsTable.getItems()), StandardCharsets.UTF_8);
			this.sparqlResultsStatusLabel.setText(this.sparqlResultMetrics + " · Exported "
					+ destination.getName());
		} catch (IOException e) {
			this.sparqlResultsStatusLabel.setText("CSV export failed: " + e.getMessage());
		}
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
		updatePerformanceModeDependency();
	}


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
		ef1 = new FileChooser.ExtensionFilter("IFC documents (*.ifc, *.ifcxml, *.ifcjson)", "*.ifc", "*.ifcxml", "*.ifcjson", "*.xml", "*.json");
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
		setIfcFileLabel(file.getName());
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
			setTargetFileLabel(this.rdfTargetName);
		}
		if (this.ifcFileName != null && this.rdfTargetName != null) {
			this.selectTargetFileButton.setDisable(false);
			System.out.println("workdir put:" + file.getParentFile().getAbsolutePath());
			this.prefs.put("ifc_work_directory", file.getParentFile().getAbsolutePath());
			readInIFC();
		}
		setSelectIfcDefault(false);
		refreshReactiveSettings();
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
		FileChooser.ExtensionFilter icddFilter = new FileChooser.ExtensionFilter("ICDD packages (*.icdd)", "*.icdd");
		this.fc_target.getExtensionFilters().clear();
		this.fc_target.getExtensionFilters().addAll(ttlFilter, jsonLdFilter, icddFilter);
		this.fc_target.setSelectedExtensionFilter(
				isIcddOutputSelected() ? icddFilter : isJsonLdOutputSelected() ? jsonLdFilter : ttlFilter);

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
		setTargetFileLabel(file.getAbsolutePath());

		this.rdfTargetName = file.getAbsolutePath();
		refreshReactiveSettings();
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

	private boolean isIcddOutputSelected() {
		String outputFormat = this.outputJSONorTTL.getValue();
		return outputFormat != null && outputFormat.toUpperCase(Locale.ROOT).contains("ICDD");
	}

	private String selectedOutputExtension() {
		if (isIcddOutputSelected()) return ".icdd";
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
		setTargetFileLabel(this.rdfTargetName);
		setSparqlQueryAvailable(isSparqlQueryAvailable());
	}

	private ConversionSettings currentSettings() {
		String baseUri = this.labelBaseURI.getText().trim();
		if (baseUri.isEmpty()) {
			baseUri = IFCtoLBDConverter.DEFAULT_BASE_URI;
		}
		return new ConversionSettings(this.ifcFileName, this.rdfTargetName, baseUri,
				selectedPropertyLevel(), this.building_elements.isSelected(), this.materialsBmo.isSelected(),
				this.building_elements_separate_file.isSelected(), this.building_props.isSelected(),
				this.building_props_separate_file.isSelected(), this.building_props_blank_nodes.isSelected(),
				this.geolocation.isSelected(), this.geometry_elements.isSelected(), this.ifcOWL_elements.isSelected(),
				this.hasPerformanceBoost.isSelected(), this.hasBoundingBox_WKT.isSelected(),
				this.geometry_interfaces.isSelected(), this.hasElementWireframe.isSelected(), this.createUnits.isSelected(),
				this.hasHierarchicalNaming.isSelected(), this.hasSimpleProperties.isSelected(),
				this.propertiesAsPropertySets.isSelected(), this.ifc_based_elements.isSelected(),
				this.createTrig.isSelected(), isJsonLdOutputSelected(), isIcddOutputSelected());
	}

	private void setupPresets() {
		Preferences stored = this.prefs.node("conversionPresets");
		try {
			for (String key : stored.keys()) {
				try {
					ConversionPreset preset = ConversionPreset.read(new StringReader(stored.get(key, "")));
					if (ConversionPreset.builtIns().stream().noneMatch(builtin -> builtin.name().equalsIgnoreCase(preset.name()))) {
						this.userPresets.put(preset.name().toLowerCase(Locale.ROOT), preset);
					}
				} catch (Exception ignored) {
					// A damaged saved preset must not prevent the desktop from opening.
				}
			}
		} catch (BackingStoreException e) {
			this.presetStatusLabel.setText("Could not read saved presets: " + e.getMessage());
		}
		refreshPresetChoices(null);
		this.presetChoice.valueProperty().addListener((observable, previous, selected) -> {
			if (!this.updatingPresetChoice && selected != null) applyPreset(selected);
		});
	}

	private void refreshPresetChoices(ConversionPreset selection) {
		this.updatingPresetChoice = true;
		try {
			List<ConversionPreset> choices = new ArrayList<>(ConversionPreset.builtIns());
			choices.addAll(this.userPresets.values().stream()
					.sorted(Comparator.comparing(ConversionPreset::name, String.CASE_INSENSITIVE_ORDER)).toList());
			this.presetChoice.getItems().setAll(choices);
			this.presetChoice.setValue(selection);
		} finally {
			this.updatingPresetChoice = false;
		}
	}

	private ToggleSwitch presetToggle(String key) {
		return switch (key) {
		case "buildingElements" -> this.building_elements;
		case "separateElements" -> this.building_elements_separate_file;
		case "properties" -> this.building_props;
		case "separateProperties" -> this.building_props_separate_file;
		case "blankNodes" -> this.building_props_blank_nodes;
		case "geolocation" -> this.geolocation;
		case "materials" -> this.materialsBmo;
		case "geometry" -> this.geometry_elements;
		case "ifcOwl" -> this.ifcOWL_elements;
		case "performance" -> this.hasPerformanceBoost;
		case "boundingBoxWkt" -> this.hasBoundingBox_WKT;
		case "interfaces" -> this.geometry_interfaces;
		case "wireframe" -> this.hasElementWireframe;
		case "units" -> this.createUnits;
		case "hierarchicalNames" -> this.hasHierarchicalNaming;
		case "simpleProperties" -> this.hasSimpleProperties;
		case "propertySets" -> this.propertiesAsPropertySets;
		case "ifcBasedElements" -> this.ifc_based_elements;
		case "trig" -> this.createTrig;
		default -> throw new IllegalArgumentException("Unknown preset setting: " + key);
		};
	}

	private ConversionPreset capturePreset(String name) {
		Map<String, String> settings = new LinkedHashMap<>();
		for (String key : ConversionPreset.SWITCHES) {
			settings.put(key, Boolean.toString(presetToggle(key).isSelected()));
		}
		settings.put(ConversionPreset.LEVEL, Integer.toString(selectedPropertyLevel()));
		settings.put(ConversionPreset.FORMAT, this.outputJSONorTTL.getValue());
		settings.put(ConversionPreset.BASE_URI, this.labelBaseURI.getText().trim());
		return new ConversionPreset(name, settings, false);
	}

	private void applyPreset(ConversionPreset preset) {
		for (String key : ConversionPreset.SWITCHES) {
			presetToggle(key).setSelected(Boolean.parseBoolean(preset.settings().get(key)));
		}
		int level = Integer.parseInt(preset.settings().get(ConversionPreset.LEVEL));
		this.level1.setSelected(level == 1);
		this.level2.setSelected(level == 2);
		this.level3.setSelected(level == 3);
		if (preset.settings().containsKey(ConversionPreset.BASE_URI)) {
			this.labelBaseURI.setText(preset.settings().get(ConversionPreset.BASE_URI));
		}
		this.outputJSONorTTL.setValue(preset.settings().get(ConversionPreset.FORMAT));
		updatePerformanceModeDependency();
		refreshReactiveSettings();
		this.presetStatusLabel.setText("Applied " + preset.name() + ". Adjust advanced settings as needed.");
	}

	private String presetKey(String name) {
		return UUID.nameUUIDFromBytes(name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private void storePreset(ConversionPreset preset) {
		try {
			StringWriter writer = new StringWriter();
			preset.write(writer);
			this.prefs.node("conversionPresets").put(presetKey(preset.name()), writer.toString());
			this.userPresets.put(preset.name().toLowerCase(Locale.ROOT), preset);
			refreshPresetChoices(preset);
			applyPreset(preset);
			this.presetStatusLabel.setText("Saved " + preset.name() + ".");
		} catch (Exception e) {
			this.presetStatusLabel.setText("Could not save preset: " + e.getMessage());
		}
	}

	private void askPresetName(String suggested, java.util.function.Consumer<String> onName) {
		TextInputDialog dialog = new TextInputDialog(suggested);
		dialog.initOwner(this.myMenuBar.getScene().getWindow());
		dialog.setTitle("Conversion preset");
		dialog.setHeaderText("Name this preset");
		dialog.showAndWait().ifPresent(raw -> {
			String name = raw.trim();
			if (name.isEmpty() || name.length() > 80) {
				this.presetStatusLabel.setText("Use a preset name of 1–80 characters.");
			} else if (ConversionPreset.builtIns().stream().anyMatch(p -> p.name().equalsIgnoreCase(name))
					|| this.userPresets.containsKey(name.toLowerCase(Locale.ROOT))) {
				this.presetStatusLabel.setText("A preset named " + name + " already exists.");
			} else {
				onName.accept(name);
			}
		});
	}

	@FXML
	private void savePreset() {
		ConversionPreset selected = this.presetChoice.getValue();
		if (selected != null && !selected.builtIn()) {
			storePreset(capturePreset(selected.name()));
		} else {
			askPresetName("My preset", name -> storePreset(capturePreset(name)));
		}
	}

	@FXML
	private void duplicatePreset() {
		ConversionPreset selected = this.presetChoice.getValue();
		if (selected == null) {
			this.presetStatusLabel.setText("Choose a preset to duplicate.");
			return;
		}
		askPresetName(selected.name() + " copy", name -> storePreset(selected.renamed(name)));
	}

	@FXML
	private void importPreset() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Import conversion preset");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Preset files (*.properties)", "*.properties"));
		File file = chooser.showOpenDialog(this.myMenuBar.getScene().getWindow());
		if (file == null) return;
		try (java.io.Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			ConversionPreset preset = ConversionPreset.read(reader);
			if (ConversionPreset.builtIns().stream().anyMatch(p -> p.name().equalsIgnoreCase(preset.name()))
					|| this.userPresets.containsKey(preset.name().toLowerCase(Locale.ROOT))) {
				this.presetStatusLabel.setText("A preset named " + preset.name() + " already exists. Rename it before importing.");
				return;
			}
			storePreset(preset);
		} catch (Exception e) {
			this.presetStatusLabel.setText("Could not import preset: " + e.getMessage());
		}
	}

	@FXML
	private void exportPreset() {
		ConversionPreset preset = this.presetChoice.getValue();
		if (preset == null) {
			this.presetStatusLabel.setText("Choose a preset to export.");
			return;
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Export conversion preset");
		chooser.setInitialFileName(preset.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".properties");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Preset files (*.properties)", "*.properties"));
		File file = chooser.showSaveDialog(this.myMenuBar.getScene().getWindow());
		if (file == null) return;
		try (java.io.Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			preset.write(writer);
			this.presetStatusLabel.setText("Exported " + preset.name() + ".");
		} catch (Exception e) {
			this.presetStatusLabel.setText("Could not export preset: " + e.getMessage());
		}
	}

	@FXML
	private void deletePreset() {
		ConversionPreset preset = this.presetChoice.getValue();
		if (preset == null || preset.builtIn()) {
			this.presetStatusLabel.setText("Select a saved preset to delete.");
			return;
		}
		this.prefs.node("conversionPresets").remove(presetKey(preset.name()));
		this.userPresets.remove(preset.name().toLowerCase(Locale.ROOT));
		refreshPresetChoices(null);
		this.presetStatusLabel.setText("Deleted " + preset.name() + ". Current settings are unchanged.");
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
				|| previous.hasElementWireframe() != current.hasElementWireframe()
				|| previous.hasUnits() != current.hasUnits();
	}

	private boolean hasConversionRequestChanged(ConversionRequest previous, ConversionRequest current) {
		if (previous == null) {
			return true;
		}
		ConversionSettings previousSettings = previous.settings();
		ConversionSettings currentSettings = current.settings();
		return hasReadInSettingsChanged(previousSettings, currentSettings)
				|| !Objects.equals(previousSettings.rdfTargetName(), currentSettings.rdfTargetName())
				|| previousSettings.hasMaterialsBmo() != currentSettings.hasMaterialsBmo()
				|| previousSettings.hasSeparateBuildingElementsModel() != currentSettings.hasSeparateBuildingElementsModel()
				|| previousSettings.hasSeparatePropertiesModel() != currentSettings.hasSeparatePropertiesModel()
				|| previousSettings.hasHierarchicalNaming() != currentSettings.hasHierarchicalNaming()
				|| previousSettings.hasSimpleProperties() != currentSettings.hasSimpleProperties()
				|| previousSettings.propertiesAsPropertySets() != currentSettings.propertiesAsPropertySets()
				|| previousSettings.hasIfcBasedElements() != currentSettings.hasIfcBasedElements()
				|| previousSettings.createTrig() != currentSettings.createTrig()
				|| previousSettings.exportAsJsonLd() != currentSettings.exportAsJsonLd()
				|| previousSettings.exportAsIcdd() != currentSettings.exportAsIcdd()
				|| !Objects.equals(previous.selectedTypes(), current.selectedTypes())
				|| !Objects.equals(previous.selectedPsets(), current.selectedPsets());
	}

	private Set<String> selectedElementTypes() {
		Set<String> selectedTypes = new HashSet<>();
		if (this.element_types_checkbox.getCheckModel() == null) {
			return selectedTypes;
		}
		for (TreeItem<String> item : this.element_types_checkbox.getCheckModel().getCheckedItems()) {
			String value = item.getValue();
			int suffix = value.lastIndexOf(" (");
			selectedTypes.add(suffix > 0 && value.endsWith(")") ? value.substring(0, suffix) : value);
		}
		return selectedTypes;
	}

	private Set<String> selectedPropertySets() {
		Set<String> selectedPsets = new HashSet<>();
		if (this.propertysets_checkbox.getCheckModel() == null) {
			return selectedPsets;
		}
		for (TreeItem<String> item : this.propertysets_checkbox.getCheckModel().getCheckedItems()) {
			selectedPsets.add(item.getValue());
		}
		return selectedPsets;
	}

	private static String shellQuote(String value) {
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			return "\"" + value.replace("\"", "\\\"") + "\"";
		}
		return "'" + value.replace("'", "'\"'\"'") + "'";
	}

	private static void addBooleanOption(List<String> arguments, String option, boolean enabled) {
		if (enabled) {
			arguments.add(option);
		}
	}

	private String createCommandLine(ConversionSettings settings) {
		if (settings.exportAsIcdd()) {
			return "# ICDD package export is available in the IFCtoLBD desktop application.";
		}
		List<String> arguments = new ArrayList<>();
		arguments.add("IFCtoLBDConverter_CLI");
		arguments.add(shellQuote(settings.ifcFileName()));
		arguments.add("--target_file");
		arguments.add(shellQuote(settings.rdfTargetName()));
		arguments.add("--url");
		arguments.add(shellQuote(settings.baseUri()));
		arguments.add("--level");
		arguments.add(Integer.toString(settings.propsLevel()));
		addBooleanOption(arguments, "--hasBuildingElements", settings.hasBuildingElements());
		addBooleanOption(arguments, "--hasSeparateBuildingElementsModel", settings.hasSeparateBuildingElementsModel());
		addBooleanOption(arguments, "--hasBuildingElementProperties", settings.hasBuildingProperties());
		addBooleanOption(arguments, "--hasSeparatePropertiesModel", settings.hasSeparatePropertiesModel());
		addBooleanOption(arguments, "--hasBlankNodes", settings.hasPropertiesBlankNodes());
		addBooleanOption(arguments, "--hasGeolocation", settings.hasGeolocation());
		addBooleanOption(arguments, "--hasGeometry", settings.hasGeometry());
		addBooleanOption(arguments, "--ifcOWL", settings.exportIfcOwl());
		addBooleanOption(arguments, "--hasPerformanceBoost", settings.hasPerformanceBoost());
		addBooleanOption(arguments, "--hasWKT", settings.hasBoundingBoxWkt());
		addBooleanOption(arguments, "--hasInterfaces", settings.hasInterfaces());
		addBooleanOption(arguments, "--hasWireframe", settings.hasElementWireframe());
		addBooleanOption(arguments, "--hasUnits", settings.hasUnits());
		addBooleanOption(arguments, "--hasHierarchicalNaming", settings.hasHierarchicalNaming());
		addBooleanOption(arguments, "--hasSimpleProperties", settings.hasSimpleProperties());
		addBooleanOption(arguments, "-asPropertySets", settings.propertiesAsPropertySets());
		addBooleanOption(arguments, "--hasIfc_based_elements", settings.hasIfcBasedElements());
		addBooleanOption(arguments, "--hasTriG", settings.createTrig());
		addBooleanOption(arguments, "--JSON", settings.exportAsJsonLd());
		for (String type : selectedElementTypes().stream().sorted().toList()) {
			arguments.add("--selectedType");
			arguments.add(shellQuote(type));
		}
		for (String pset : selectedPropertySets().stream().sorted().toList()) {
			arguments.add("--selectedPropertySet");
			arguments.add(shellQuote(pset));
		}
		return String.join(" ", arguments);
	}

	static List<String> proposedOutputFiles(ConversionSettings settings) {
		if (settings == null) {
			return List.of();
		}
		return proposedOutputFiles(settings.rdfTargetName(), settings.exportAsJsonLd(), settings.exportAsIcdd(),
				settings.hasBuildingElements(), settings.hasSeparateBuildingElementsModel(),
				settings.hasBuildingProperties(), settings.hasSeparatePropertiesModel(), settings.createTrig());
	}

	static List<String> proposedOutputFiles(String target, boolean exportAsJsonLd, boolean exportAsIcdd,
			boolean hasBuildingElements, boolean separateBuildingElements, boolean hasBuildingProperties,
			boolean separateProperties, boolean createTrig) {
		if (target == null || target.isBlank()) {
			return List.of();
		}
		List<String> files = new ArrayList<>();
		files.add(target);
		if (exportAsIcdd) {
			return List.copyOf(files);
		}
		int separatorIndex = Math.max(target.lastIndexOf(File.separatorChar), target.lastIndexOf('/'));
		int dotIndex = target.lastIndexOf('.');
		String stem = dotIndex > separatorIndex ? target.substring(0, dotIndex) : target;
		String extension = exportAsJsonLd ? ".json" : ".ttl";
		if (hasBuildingElements && separateBuildingElements) {
			files.add(stem + "_building_elements" + extension);
		}
		if (hasBuildingProperties && separateProperties) {
			files.add(stem + "_element_properties" + extension);
		}
		if (createTrig) {
			files.add(stem + ".trig");
		}
		return List.copyOf(files);
	}

	static String proposedOutputText(List<String> outputFiles) {
		if (outputFiles.isEmpty()) {
			return "Select an IFC file and output path.";
		}
		String paths = outputFiles.stream().map(path -> new File(path).getAbsolutePath())
				.collect(java.util.stream.Collectors.joining("\n"));
		return outputFiles.size() == 1 ? paths : outputFiles.size() + " output files:\n" + paths;
	}

	private void updateLivePreviews() {
		if (this.proposedFilenamesLabel == null || this.commandLinePreviewTxt == null) {
			return;
		}
		ConversionSettings settings = currentSettings();
		List<String> outputFiles = proposedOutputFiles(settings);
		this.proposedFilenamesLabel.setText(proposedOutputText(outputFiles));
		if (settings.ifcFileName() == null || settings.rdfTargetName() == null) {
			this.commandLinePreviewTxt.setText("Select an IFC file and output path to preview the command.");
		} else {
			this.commandLinePreviewTxt.setText(createCommandLine(settings));
		}
	}

	@FXML
	private void copyCommandLine() {
		ConversionSettings settings = currentSettings();
		if (settings.ifcFileName() == null || settings.rdfTargetName() == null) {
			this.conversionTxt.appendText("Select IFC and target files before copying the command line.\n");
			return;
		}
		String commandLine = createCommandLine(settings);
		ClipboardContent content = new ClipboardContent();
		content.putString(commandLine);
		Clipboard.getSystemClipboard().setContent(content);
		this.conversionTxt.appendText("Command line copied to the clipboard.\n");
	}

	private void setupCommandLinePreview() {
		this.copyCommandLineButton.setTooltip(new Tooltip("Copy the command-line preview to the clipboard."));
		var advancedSelected = this.workflowTabs.getSelectionModel().selectedItemProperty()
				.isEqualTo(this.workflowTabs.getTabs().get(1));
		this.proposedOutputSection.visibleProperty().bind(advancedSelected.not());
		this.proposedOutputSection.managedProperty().bind(this.proposedOutputSection.visibleProperty());
		this.commandLinePreviewSection.visibleProperty().bind(advancedSelected);
		this.commandLinePreviewSection.managedProperty().bind(this.commandLinePreviewSection.visibleProperty());
	}

	private void setupReactiveSettings() {
		List<ToggleSwitch> settingsToggles = List.of(this.building_elements,
				this.building_elements_separate_file, this.geolocation, this.building_props,
				this.materialsBmo,
				this.building_props_blank_nodes, this.building_props_separate_file, this.geometry_elements,
				this.geometry_interfaces, this.hasBoundingBox_WKT, this.hasElementWireframe, this.ifcOWL_elements,
				this.hasPerformanceBoost, this.createUnits, this.hasHierarchicalNaming, this.hasSimpleProperties,
				this.propertiesAsPropertySets, this.ifc_based_elements, this.createTrig);
		for (ToggleSwitch toggle : settingsToggles) {
			toggle.selectedProperty().addListener((observable, oldValue, newValue) -> refreshReactiveSettings());
		}
		this.ifcOWL_elements.selectedProperty().addListener((observable, oldValue, newValue) ->
				updatePerformanceModeDependency());
		this.materialsBmo.selectedProperty().addListener((observable, oldValue, newValue) ->
				updatePerformanceModeDependency());
		this.level1.getToggleGroup().selectedToggleProperty().addListener((observable, oldValue, newValue) ->
				refreshReactiveSettings());
		this.labelBaseURI.textProperty().addListener((observable, oldValue, newValue) -> refreshReactiveSettings());
		this.element_types_checkbox.checkModelProperty().addListener((observable, oldModel, newModel) -> {
			if (newModel != null) {
				newModel.getCheckedItems().addListener(
						(ListChangeListener<TreeItem<String>>) change -> refreshReactiveSettings());
			}
		});
		this.propertysets_checkbox.checkModelProperty().addListener((observable, oldModel, newModel) -> {
			if (newModel != null) {
				newModel.getCheckedItems().addListener(
						(ListChangeListener<TreeItem<String>>) change -> refreshReactiveSettings());
			}
		});
		this.element_types_checkbox.getSelectionModel().selectedItemProperty().addListener(
				(observable, oldValue, newValue) -> isolateSelectedElementType(newValue));
		updatePerformanceModeDependency();
		refreshReactiveSettings();
	}

	private void refreshReactiveSettings() {
		if (this.building_props == null) {
			return;
		}
		updateMaterialDependencies();
		updatePropertyDependencies();
		updateLivePreviews();
		updateReadRequiredMarkers();
		updateGeneratedResultsState();
	}

	private void updateMaterialDependencies() {
		boolean selected = this.materialsBmo.isSelected();
		if (selected) {
			this.building_elements.setSelected(true);
			this.building_props.setSelected(true);
		}
		this.building_elements.setDisable(selected);
		this.building_props.setDisable(selected);
	}

	private void updatePropertyDependencies() {
		boolean disabled = !this.building_props.isSelected();
		this.level1.setDisable(disabled);
		this.level2.setDisable(disabled);
		this.level3.setDisable(disabled);
		this.propertiesAsPropertySets.setDisable(disabled);
		this.building_props_separate_file.setDisable(disabled);
		this.building_props_blank_nodes.setDisable(disabled);
		this.createUnits.setDisable(disabled);
		this.hasSimpleProperties.setDisable(disabled);
		this.propertysets_checkbox.setDisable(disabled);
	}

	private void updatePerformanceModeDependency() {
		if (this.ifcOWL_elements == null || this.hasPerformanceBoost == null) {
			return;
		}
		boolean ifcOwlSelected = this.ifcOWL_elements.isSelected();
		boolean materialsSelected = this.materialsBmo != null && this.materialsBmo.isSelected();
		if ((ifcOwlSelected || materialsSelected) && this.hasPerformanceBoost.isSelected()) {
			this.hasPerformanceBoost.setSelected(false);
		}
		this.hasPerformanceBoost.setDisable(ifcOwlSelected || materialsSelected);
		String explanation = "Performance mode uses a reduced streaming read to save memory. "
				+ "ifcOWL and BMO material export need the complete source graph, so selecting either turns Performance mode off.";
		this.hasPerformanceBoost.setTooltip(new Tooltip(explanation));
		this.ifcOWL_elements.setTooltip(new Tooltip(explanation));
		if (this.materialsBmo != null) this.materialsBmo.setTooltip(new Tooltip(explanation));
		if (this.performanceConflictLabel != null) {
			this.performanceConflictLabel.setText(explanation);
		}
	}

	private void updateReadRequiredMarkers() {
		ConversionSettings previous = this.readInSettings;
		ConversionSettings current = currentSettings();
		boolean modelLoaded = isFiltersAvailable() && previous != null;
		setRereadMarker(this.baseUriLabel, "Base URI", modelLoaded
				&& !Objects.equals(previous.baseUri(), current.baseUri()));
		setRereadMarker(this.propertyLevelLabel, "Level", modelLoaded
				&& previous.propsLevel() != current.propsLevel());
		setRereadMarker(this.building_elements, "Building elements", modelLoaded
				&& previous.hasBuildingElements() != current.hasBuildingElements());
		setRereadMarker(this.building_props, "Properties", modelLoaded
				&& previous.hasBuildingProperties() != current.hasBuildingProperties());
		setRereadMarker(this.building_props_blank_nodes, "Blank nodes", modelLoaded
				&& previous.hasPropertiesBlankNodes() != current.hasPropertiesBlankNodes());
		setRereadMarker(this.geolocation, "Geolocation", modelLoaded
				&& previous.hasGeolocation() != current.hasGeolocation());
		setRereadMarker(this.geometry_elements, "Geometry", modelLoaded
				&& previous.hasGeometry() != current.hasGeometry());
		setRereadMarker(this.ifcOWL_elements, "ifcOWL links", modelLoaded
				&& previous.exportIfcOwl() != current.exportIfcOwl());
		setRereadMarker(this.hasPerformanceBoost, "Performance mode", modelLoaded
				&& previous.hasPerformanceBoost() != current.hasPerformanceBoost());
		setRereadMarker(this.hasBoundingBox_WKT, "Bounding boxes", modelLoaded
				&& previous.hasBoundingBoxWkt() != current.hasBoundingBoxWkt());
		setRereadMarker(this.geometry_interfaces, "Interfaces", modelLoaded
				&& previous.hasInterfaces() != current.hasInterfaces());
		setRereadMarker(this.hasElementWireframe, "Element wireframe", modelLoaded
				&& previous.hasElementWireframe() != current.hasElementWireframe());
		setRereadMarker(this.createUnits, "Units", modelLoaded
				&& previous.hasUnits() != current.hasUnits());
		boolean rereadRequired = modelLoaded && hasReadInSettingsChanged(previous, current);
		String runTooltip = rereadRequired
				? "Run will reread the IFC model with the changed settings, then convert automatically."
				: "Start the conversion process.";
		this.convert2RDFButton.setTooltip(new Tooltip(runTooltip));
		this.basicConvert2RDFButton.setTooltip(new Tooltip(runTooltip));
	}

	private static void setRereadMarker(Labeled control, String label, boolean rereadRequired) {
		control.setWrapText(true);
		control.setText(rereadRequired ? label + "  · Requires model reread" : label);
		if (rereadRequired) {
			if (!control.getStyleClass().contains("requires-reread")) {
				control.getStyleClass().add("requires-reread");
			}
		} else {
			control.getStyleClass().remove("requires-reread");
		}
	}

	private void updateGeneratedResultsState() {
		if (this.lastSuccessfulConversionRequest == null) {
			setGeneratedResultsStale(false);
			return;
		}
		ConversionRequest current = new ConversionRequest(currentSettings(), selectedElementTypes(), selectedPropertySets());
		setGeneratedResultsStale(hasConversionRequestChanged(this.lastSuccessfulConversionRequest, current));
	}

	private void setGeneratedResultsStale(boolean stale) {
		if (this.generatedResultsStatusLabel == null) {
			this.generatedResultsStale = stale;
			return;
		}
		if (this.generatedResultsStale == stale && this.generatedResultsStatusLabel != null
				&& this.generatedResultsStatusLabel.isVisible() == stale) {
			return;
		}
		this.generatedResultsStale = stale;
		this.generatedResultsStatusLabel.setManaged(stale);
		this.generatedResultsStatusLabel.setVisible(stale);
		if (stale) {
			this.generatedResultsStatusLabel.setText("Generated results are stale — run again to apply the current settings and filters.");
			setWorkflowDataAvailable(isFiltersAvailable(), this.currentPreviewMesh != null, false);
			showSparqlMessage("Generated results are stale. Run the conversion again before querying.");
			this.validateStatusLabel.setText("Generated results are stale. Run the conversion again before validation.");
			appendGeometryPreviewMessage("Generated results are stale; the preview still shows the last conversion.");
		} else if (this.lastSuccessfulConversionRequest != null) {
			ConversionSettings settings = this.lastSuccessfulConversionRequest.settings();
			boolean queryAvailable = !settings.exportAsIcdd() && settings.rdfTargetName() != null
					&& new File(settings.rdfTargetName()).isFile();
			setWorkflowDataAvailable(isFiltersAvailable(), !settings.exportAsIcdd(), queryAvailable);
			if (!settings.exportAsIcdd()) {
				scheduleGeometryPreview(settings);
			}
			if (queryAvailable) {
				showSparqlMessage("Ready. Run a SPARQL query against "
						+ new File(settings.rdfTargetName()).getName() + ".");
				validateLoadedShapesAsync();
			}
		}
	}

	private void persistSettings(ConversionSettings settings) {
		this.prefs.putBoolean("lbd_building_elements", settings.hasBuildingElements());
		this.prefs.putBoolean("lbd_materials_bmo", settings.hasMaterialsBmo());
		this.prefs.putBoolean("lbd_building_elements_separate_file", settings.hasSeparateBuildingElementsModel());
		this.prefs.putBoolean("lbd_building_props", settings.hasBuildingProperties());
		this.prefs.putBoolean("lbd_building_props_blank_nodes", settings.hasPropertiesBlankNodes());
		this.prefs.putBoolean("lbd_building_props_separate_file", settings.hasSeparatePropertiesModel());
		this.prefs.put("lbd_props_base_url", settings.baseUri());
		this.prefs.putBoolean("lbd_boundinbox_elements", settings.hasGeometry());
		this.prefs.putBoolean("lbd_boundinbox_interfaces", settings.hasInterfaces());
		this.prefs.putBoolean("lbd_boundinbox_wkt", settings.hasBoundingBoxWkt());
		this.prefs.putBoolean("lbd_element_wireframe", settings.hasElementWireframe());
		this.prefs.putBoolean("lbd_ifcOWL_elements", settings.exportIfcOwl());
		this.prefs.putBoolean("lbd_performance", settings.hasPerformanceBoost());
		this.prefs.putBoolean("lbd_createUnits", settings.hasUnits());
		this.prefs.putBoolean("lbd_geolocation", settings.hasGeolocation());
		this.prefs.putBoolean("lbd_hasHierarchicalNaming", settings.hasHierarchicalNaming());
		this.prefs.putBoolean("lbd_hasSimpleProperties", settings.hasSimpleProperties());
		this.prefs.putBoolean("lbd_propertiesAsPropertySets", settings.propertiesAsPropertySets());
		this.prefs.putBoolean("ifc_based_elements", settings.hasIfcBasedElements());
		this.prefs.putBoolean("createTrig", settings.createTrig());
		this.prefs.putBoolean("export_as_jsonld", settings.exportAsJsonLd());
		this.prefs.put("desktop_output_format",
				settings.exportAsIcdd() ? "ICDD" : settings.exportAsJsonLd() ? "JSON-LD" : "Turtle TTL");
		this.prefs.putInt("lbd_props_level", settings.propsLevel());
	}

	private void readInIFC() {
		this.runAfterReadIn = false;
		ConversionSettings settings = currentSettings();
		readInIFCExecute(settings);
	}

	private void readInIFCExecute(ConversionSettings settings) {
		if ((this.running_read_in != null && !this.running_read_in.isDone())
				|| (this.running_conversion != null && !this.running_conversion.isDone())) {
			this.conversionTxt.appendText("The previous read-in or conversion is still running.\n");
			return;
		}
		persistSettings(settings);
		this.readInSettings = settings;
		setRunReady(false);
		resetConversionOutput();
		this.element_types_checkbox.setRoot(null);
		this.propertysets_checkbox.setRoot(null);
		this.conversionTxt.setText("");
		clearGeometryPreview("Convert with geometry enabled to preview the model.");
		long operationId = beginWorkflow("Starting IFC read-in", ProgressBar.INDETERMINATE_PROGRESS);
		try {
			this.running_read_in = this.executor
					.submit(new ReadinInThread(operationId, settings.ifcFileName(), settings.baseUri(), settings.rdfTargetName(),
							settings.propsLevel(), settings.hasBuildingElements(),
							settings.hasSeparateBuildingElementsModel(), settings.hasBuildingProperties(),
							settings.hasSeparatePropertiesModel(), settings.hasPropertiesBlankNodes(),
							settings.hasGeolocation(), settings.hasGeometry() || settings.hasElementWireframe(), settings.exportIfcOwl(),
							settings.hasUnits(), settings.hasPerformanceBoost(), settings.hasBoundingBoxWkt(),
							settings.hasInterfaces()));
		} catch (Exception e) {
			this.options_panel.setDisable(false);
			finishWorkflow(operationId, "Read-in failed", false);
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}
	}

	private void setupGeometryPreview() {
		this.geometryCard.setMinWidth(GEOMETRY_CARD_WIDTH);
		this.geometryCard.setPrefWidth(GEOMETRY_CARD_WIDTH);
		this.geometryCard.setMaxWidth(GEOMETRY_CARD_WIDTH);
		this.geometryViewport.setMinWidth(GEOMETRY_VIEWPORT_WIDTH);
		this.geometryViewport.setPrefWidth(GEOMETRY_VIEWPORT_WIDTH);
		this.geometryViewport.setMaxWidth(Double.MAX_VALUE);

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

		this.geometryEmptyStateLabel = new Label();
		this.geometryEmptyStateLabel.getStyleClass().add("geometry-empty-state");
		this.geometryEmptyStateLabel.setWrapText(true);
		this.geometryEmptyStateLabel.setMaxWidth(360);
		this.geometryEmptyStateLabel.setMouseTransparent(true);

		this.geometryFitButton = new Button("Fit");
		this.geometryFitButton.getStyleClass().add("geometry-tool-button");
		this.geometryFitButton.setFocusTraversable(false);
		this.geometryFitButton.setOnAction(event -> {
			fitGeometryPreview();
			event.consume();
		});
		StackPane.setAlignment(this.geometryFitButton, Pos.TOP_RIGHT);

		this.geometryViewport.getChildren().setAll(this.geometryScene, this.geometryEmptyStateLabel,
				this.geometryFitButton);
		this.geometryExplodeSlider.setDisable(true);
		this.geometryExplodeSlider.setTooltip(new Tooltip("Move each object outward from the model center. Set to 0% to restore the model."));
		this.geometryExplodeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
			this.geometryExplodeValueLabel.setText(Math.round(newValue.doubleValue()) + "%");
			applyGeometryExplosion();
		});
		clearGeometryPreview("Select geometry and convert to preview the model.");
	}

	private void setupFloatingCards() {
		setupFloatingCard(this.options_panel, "Settings");
		setupFloatingCard(this.filtersCard, "Filters");
		setupFloatingCard(this.geometryCard, "Geometry Preview");
		setupFloatingCard(this.sparqlQueryCard, "SPARQL Query");
		setupFloatingCard(this.validateCard, "Validate");
		matchFloatingCardWidthsToSettings();
		setupFloatingWindowButtonRouting();
		this.floatingWorkspace.widthProperty().addListener((observable, oldValue, newValue) -> clampFloatingCardsToWorkspace());
		this.floatingWorkspace.heightProperty().addListener((observable, oldValue, newValue) -> clampFloatingCardsToWorkspace());
		Platform.runLater(() -> {
			alignFloatingCardsIfNeeded();
			clampFloatingCardsToWorkspace();
		});
	}

	private void matchFloatingCardWidthsToSettings() {
		if (this.options_panel == null) {
			return;
		}
		for (TitledPane card : new TitledPane[] {
				this.filtersCard, this.geometryCard, this.sparqlQueryCard, this.validateCard }) {
			if (card == null) {
				continue;
			}
			card.minWidthProperty().bind(this.options_panel.widthProperty());
			card.prefWidthProperty().bind(this.options_panel.widthProperty());
			card.maxWidthProperty().bind(this.options_panel.widthProperty());
		}
	}

	private void setupSparqlQueryWindow() {
		if (this.sparqlEditorTxt != null && this.sparqlEditorTxt.getText().isBlank()) {
			this.sparqlEditorTxt.setText(DEFAULT_SPARQL_QUERY);
		}
		this.sparqlTemplateChoice.getItems().setAll("Custom query", "Spaces by storey",
				"Elements missing properties", "Element inventory");
		this.sparqlTemplateChoice.setValue("Element inventory");
		this.sparqlTemplateChoice.valueProperty().addListener((observable, oldValue, template) -> {
			String query = queryForTemplate(template);
			if (query != null) this.sparqlEditorTxt.setText(query);
		});
		this.sparqlEditorTxt.textProperty().addListener((observable, oldValue, newValue) -> {
			if (this.sparqlTemplateChoice.getValue() != null
					&& !"Custom query".equals(this.sparqlTemplateChoice.getValue())
					&& !Objects.equals(newValue, queryForTemplate(this.sparqlTemplateChoice.getValue()))) {
				this.sparqlTemplateChoice.setValue("Custom query");
			}
		});
		this.sparqlResultsTable.setPlaceholder(new Label("Run a SELECT query to see results."));
		this.sparqlResultsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, row) -> {
			if (row == null) return;
			for (SparqlCell cell : row.cells().values()) {
				if (cell != null && cell.resourceUri() != null) {
					highlightSparqlResource(cell.resourceUri());
					break;
				}
			}
		});
		this.copySparqlResultsButton.setDisable(true);
		this.exportSparqlResultsButton.setDisable(true);
		setSparqlQueryAvailable(false);
	}

	static String queryForTemplate(String template) {
		if (template == null) return null;
		return switch (template) {
		case "Spaces by storey" -> SPACES_BY_STOREY_QUERY;
		case "Elements missing properties" -> ELEMENTS_MISSING_PROPERTIES_QUERY;
		case "Element inventory" -> DEFAULT_SPARQL_QUERY;
		default -> null;
		};
	}

	private void setupValidateWindow() {
		if (this.shaclShapesList != null) {
			this.shaclShapesList.setItems(this.shapeValidationItems);
			this.shaclShapesList.setCellFactory(list -> new ListCell<>() {
				private final Label shapeLabel = new Label();
				private final Button removeButton = new Button("x");
				private final HBox row = new HBox(8.0, this.shapeLabel, this.removeButton);

				{
					this.row.setAlignment(Pos.CENTER_LEFT);
					this.shapeLabel.getStyleClass().add("shape-result-label");
					this.shapeLabel.setMaxWidth(Double.MAX_VALUE);
					this.shapeLabel.setWrapText(true);
					HBox.setHgrow(this.shapeLabel, Priority.ALWAYS);
					this.removeButton.getStyleClass().add("shape-remove-button");
					this.removeButton.setTooltip(new Tooltip("Remove rule"));
					this.removeButton.setFocusTraversable(false);
				}

				@Override
				protected void updateItem(ShapeValidationItem item, boolean empty) {
					super.updateItem(item, empty);
					getStyleClass().removeAll("shape-valid", "shape-invalid", "shape-warning", "shape-info", "shape-pending");
					if (empty || item == null) {
						setText(null);
						setGraphic(null);
						setTooltip(null);
						return;
					}
					setText(null);
					ValidationFindings.Counts counts = ValidationFindings.count(item.violations);
					this.shapeLabel.setText(item.displayName + "\n" + item.sourceName + " · " + counts.summary());
					this.removeButton.setOnAction(event -> removeShapeItem(item));
					setGraphic(this.row);
					setTooltip(new Tooltip(item.message));
					if (counts.violations() > 0) {
						getStyleClass().add("shape-invalid");
					} else if (counts.warnings() > 0) {
						getStyleClass().add("shape-warning");
					} else if (counts.infos() > 0) {
						getStyleClass().add("shape-info");
					} else if (Boolean.TRUE.equals(item.conforms)) {
						getStyleClass().add("shape-valid");
					} else {
						getStyleClass().add("shape-pending");
					}
				}
			});
			this.shaclShapesList.getSelectionModel().selectedItemProperty().addListener(
					(observable, oldValue, item) -> focusValidationItem(item));
		}
		this.validationSeverityFilter.getItems().setAll("All severities", "Violation", "Warning", "Info");
		this.validationSeverityFilter.setValue("All severities");
		this.validationSeverityFilter.valueProperty().addListener((observable, oldValue, newValue) -> refreshValidationFindings());
		this.validationShapeFilter.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (!this.refreshingValidationFilters) refreshValidationFindings();
		});
		this.validationObjectFilter.textProperty().addListener((observable, oldValue, newValue) -> refreshValidationFindings());
		this.validationFindingsTree.setShowRoot(false);
		this.validationFindingsTree.setCellFactory(tree -> new TreeCell<>() {
			@Override
			protected void updateItem(ValidationTreeEntry entry, boolean empty) {
				super.updateItem(entry, empty);
				getStyleClass().removeAll("finding-violation", "finding-warning", "finding-info");
				setText(empty || entry == null ? null : entry.label());
				if (!empty && entry != null && entry.finding() != null) {
					getStyleClass().add("finding-" + entry.finding().level().name().toLowerCase(Locale.ROOT));
				}
			}
		});
		this.validationFindingsTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, item) -> {
			if (item != null && item.getValue() != null && item.getValue().finding() != null) {
				inspectValidationFinding(item.getValue().finding());
			}
		});
		refreshValidationFindings();
		loadDefaultShapes();
		restoreRememberedShapes();
		setValidationAvailable(false);
	}

	private void focusValidationItem(ShapeValidationItem item) {
		if (item == null || item.violations.isEmpty()) return;
		inspectValidationFinding(item.violations.getFirst());
	}

	private void inspectValidationFinding(ValidationFindings.Finding finding) {
		this.selectedValidationFinding = finding;
		this.validationInspectorTxt.setText(ValidationFindings.inspection(finding));
		this.validationFocusButton.setDisable(finding.objectUri() == null || finding.objectUri().isBlank());
		focusResource(finding.objectUri(), finding.propertyPath(), "SHACL violation", false);
	}

	@FXML
	private void focusSelectedValidationInGeometry() {
		if (this.selectedValidationFinding == null) return;
		focusResource(this.selectedValidationFinding.objectUri(), this.selectedValidationFinding.propertyPath(),
				"SHACL violation", true);
	}

	private void refreshValidationFindings() {
		if (this.validationFindingsTree == null) return;
		List<ValidationFindings.Finding> all = this.shapeValidationItems.stream()
				.flatMap(item -> item.violations.stream()).toList();
		String selectedShape = this.validationShapeFilter.getValue();
		List<String> shapeChoices = new ArrayList<>();
		shapeChoices.add("All shapes");
		this.shapeValidationItems.stream().map(item -> item.displayName + " (" + item.sourceName + ")")
				.distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(shapeChoices::add);
		this.refreshingValidationFilters = true;
		try {
			this.validationShapeFilter.getItems().setAll(shapeChoices);
			this.validationShapeFilter.setValue(shapeChoices.contains(selectedShape) ? selectedShape : "All shapes");
		} finally {
			this.refreshingValidationFilters = false;
		}
		ValidationFindings.Level level = switch (this.validationSeverityFilter.getValue() == null
				? "" : this.validationSeverityFilter.getValue()) {
		case "Violation" -> ValidationFindings.Level.VIOLATION;
		case "Warning" -> ValidationFindings.Level.WARNING;
		case "Info" -> ValidationFindings.Level.INFO;
		default -> null;
		};
		String shapeKey = "All shapes".equals(this.validationShapeFilter.getValue())
				? null : this.validationShapeFilter.getValue();
		List<ValidationFindings.Finding> shown = ValidationFindings.filter(all, level, shapeKey,
				this.validationObjectFilter.getText());
		ValidationFindings.Counts counts = ValidationFindings.count(all);
		this.validationCountsLabel.setText(counts.summary() + (shown.size() == all.size() ? "" : " · " + shown.size() + " shown"));

		TreeItem<ValidationTreeEntry> root = new TreeItem<>(new ValidationTreeEntry("Findings", null));
		for (ValidationFindings.Level severity : ValidationFindings.Level.values()) {
			List<ValidationFindings.Finding> bySeverity = shown.stream()
					.filter(finding -> finding.level() == severity).toList();
			if (bySeverity.isEmpty()) continue;
			TreeItem<ValidationTreeEntry> severityNode = new TreeItem<>(new ValidationTreeEntry(
					severity.label() + " (" + bySeverity.size() + ")", null));
			Map<String, List<ValidationFindings.Finding>> byShape = new LinkedHashMap<>();
			bySeverity.stream().sorted(Comparator.comparing(ValidationFindings.Finding::shapeKey))
					.forEach(finding -> byShape.computeIfAbsent(finding.shapeKey(), ignored -> new ArrayList<>()).add(finding));
			for (Map.Entry<String, List<ValidationFindings.Finding>> shape : byShape.entrySet()) {
				TreeItem<ValidationTreeEntry> shapeNode = new TreeItem<>(new ValidationTreeEntry(
						shape.getKey() + " (" + shape.getValue().size() + ")", null));
				Map<String, List<ValidationFindings.Finding>> byObject = new LinkedHashMap<>();
				shape.getValue().stream().sorted(Comparator.comparing(ValidationFindings.Finding::objectKey))
						.forEach(finding -> byObject.computeIfAbsent(finding.objectKey(), ignored -> new ArrayList<>())
								.add(finding));
				for (Map.Entry<String, List<ValidationFindings.Finding>> object : byObject.entrySet()) {
					TreeItem<ValidationTreeEntry> objectNode = new TreeItem<>(new ValidationTreeEntry(
							compactResourceName(object.getKey()) + " (" + object.getValue().size() + ")", null));
					objectNode.setExpanded(false);
					for (ValidationFindings.Finding finding : object.getValue()) {
						String path = finding.propertyPath() == null ? "Object" : compactResourceName(finding.propertyPath());
						String message = finding.message() == null || finding.message().isBlank() ? "Failed constraint"
								: finding.message();
						objectNode.getChildren().add(new TreeItem<>(new ValidationTreeEntry(
								path + " · " + (message.length() > 90 ? message.substring(0, 87) + "..." : message), finding)));
					}
					shapeNode.getChildren().add(objectNode);
				}
				shapeNode.setExpanded(true);
				severityNode.getChildren().add(shapeNode);
			}
			severityNode.setExpanded(true);
			root.getChildren().add(severityNode);
		}
		this.validationFindingsTree.setRoot(root);
		this.selectedValidationFinding = null;
		this.validationInspectorTxt.clear();
		this.validationFocusButton.setDisable(true);
	}

	private void isolateSelectedElementType(TreeItem<String> item) {
		if (item == null || item == this.element_types_checkbox.getRoot()) {
			this.isolatedElementType = null;
		} else {
			String value = item.getValue();
			int suffix = value == null ? -1 : value.lastIndexOf(" (");
			this.isolatedElementType = suffix > 0 && value.endsWith(")") ? value.substring(0, suffix) : value;
		}
		this.selectedResourceUri = null;
		this.selectedPropertyPath = null;
		this.sparqlSelectionHighlighted = false;
		applyGeometrySelection();
		if (this.isolatedElementType != null && this.selectedObjectHeadingLabel != null) {
			this.selectedObjectHeadingLabel.setText("Isolated element type: " + this.isolatedElementType);
			this.selectedObjectTypeLabel.setText("Select an object to inspect its details.");
			this.selectedObjectGuidLabel.setText("GUID: —");
			this.selectedObjectUriLabel.setText("RDF URI: —");
			this.selectedObjectPropertiesTxt.setText("");
		}
	}

	private void focusResource(String uri, String propertyPath, String source) {
		focusResource(uri, propertyPath, source, true);
	}

	private void focusResource(String uri, String propertyPath, String source, boolean showGeometry) {
		if (uri == null || uri.isBlank()) return;
		this.selectedResourceUri = normalizeResourceUri(uri);
		this.selectedPropertyPath = propertyPath;
		this.sparqlSelectionHighlighted = false;
		showSelectedResourceDetails(source);
		applyGeometrySelection();
		if (showGeometry) {
			focusSelectedGeometry();
			if (this.geometryCard != null) bringFloatingCardToFront(this.geometryCard);
		}
	}

	private void highlightSparqlResource(String uri) {
		if (uri == null || uri.isBlank()) return;
		this.selectedResourceUri = normalizeResourceUri(uri);
		this.selectedPropertyPath = null;
		this.sparqlSelectionHighlighted = true;
		showSelectedResourceDetails("SPARQL result");
		applyGeometrySelection();
	}

	private String normalizeResourceUri(String uri) {
		if (this.currentPreviewMesh == null || uri == null) return uri;
		return this.currentPreviewMesh.resourceAliases().getOrDefault(uri, uri);
	}

	private void showSelectedResourceDetails(String source) {
		if (this.selectedObjectHeadingLabel == null) return;
		ResourceDetails details = this.currentPreviewMesh == null ? null
				: this.currentPreviewMesh.resources().get(this.selectedResourceUri);
		this.selectedObjectHeadingLabel.setText((source == null ? "Selected object" : source) + ": "
				+ compactResourceName(this.selectedResourceUri));
		this.selectedObjectTypeLabel.setText("IFC type: " + (details == null ? "Unknown" : details.ifcType()));
		this.selectedObjectGuidLabel.setText("GUID: " + (details == null || details.guid() == null ? "—" : details.guid()));
		this.selectedObjectUriLabel.setText("RDF URI: " + this.selectedResourceUri);
		List<String> lines = new ArrayList<>();
		if (this.selectedPropertyPath != null && !this.selectedPropertyPath.isBlank()) {
			lines.add("Focused property: " + this.selectedPropertyPath);
		}
		if (details != null) lines.addAll(details.properties());
		this.selectedObjectPropertiesTxt.setText(lines.isEmpty() ? "No additional RDF properties found."
				: String.join("\n", lines));
	}

	private void applyGeometrySelection() {
		for (Map.Entry<String, List<MeshView>> entry : this.geometryViewsByResource.entrySet()) {
			ResourceDetails details = this.currentPreviewMesh == null ? null
					: this.currentPreviewMesh.resources().get(entry.getKey());
			boolean typeMatches = this.isolatedElementType == null
					|| details != null && details.typeNames().contains(this.isolatedElementType);
			boolean selected = Objects.equals(entry.getKey(), this.selectedResourceUri);
			for (MeshView meshView : entry.getValue()) {
				meshView.setVisible(typeMatches || selected);
				meshView.setOpacity(this.sparqlSelectionHighlighted || this.selectedResourceUri == null || selected ? 1.0 : 0.16);
				PhongMaterial original = this.geometryMaterials.get(meshView);
				meshView.setMaterial(selected ? createPreviewMaterial(this.sparqlSelectionHighlighted ? 0xff2020 : 0xf59e0b) : original);
			}
		}
	}

	private void focusSelectedGeometry() {
		List<MeshView> selectedViews = this.geometryViewsByResource.get(this.selectedResourceUri);
		if (selectedViews == null || selectedViews.isEmpty() || this.geometryModelRoot == null) return;
		GeometryBounds bounds = this.geometryBoundsByResource.get(this.selectedResourceUri);
		if (bounds == null) return;
		this.geometryModelRoot.setTranslateX(-bounds.center().getX() - selectedViews.getFirst().getTranslateX());
		this.geometryModelRoot.setTranslateY(-bounds.center().getY() - selectedViews.getFirst().getTranslateY());
	}

	private static String compactResourceName(String uri) {
		if (uri == null || uri.isBlank()) return "Unknown resource";
		int split = Math.max(uri.lastIndexOf('#'), Math.max(uri.lastIndexOf('/'), uri.lastIndexOf(':')));
		return split >= 0 && split + 1 < uri.length() ? uri.substring(split + 1) : uri;
	}

	private boolean isFiltersAvailable() {
		return this.running_read_in != null && this.running_read_in.isDone() && this.element_types_checkbox != null
				&& this.element_types_checkbox.getRoot() != null;
	}

	private void setFiltersAvailable(boolean available) {
		if (this.filtersWorkflowButton != null) {
			this.filtersWorkflowButton.setDisable(!available);
			setUnavailableStyle(this.filtersWorkflowButton, !available);
		}
		if (this.filtersCard != null) {
			setUnavailableStyle(this.filtersCard, !available);
		}
	}

	private boolean isGeometryPreviewAvailable() {
		return this.lastSuccessfulConversionRequest != null;
	}

	private void setGeometryPreviewAvailable(boolean available) {
		if (this.geometryWorkflowButton != null) {
			this.geometryWorkflowButton.setDisable(!available);
			setUnavailableStyle(this.geometryWorkflowButton, !available);
		}
		if (this.geometryCard != null) {
			setUnavailableStyle(this.geometryCard, !available);
		}
	}

	private boolean isSparqlQueryAvailable() {
		if (!this.queryDataAvailable || this.lastSuccessfulConversionRequest == null) {
			return false;
		}
		ConversionSettings settings = this.lastSuccessfulConversionRequest.settings();
		return settings != null && !settings.exportAsIcdd()
				&& settings.rdfTargetName() != null
				&& new File(settings.rdfTargetName()).isFile();
	}

	private boolean isValidationAvailable() {
		return isSparqlQueryAvailable();
	}

	private void setSparqlQueryAvailable(boolean available) {
		this.queryDataAvailable = available;
		if (this.queryWorkflowButton != null) {
			this.queryWorkflowButton.setDisable(!available);
			setUnavailableStyle(this.queryWorkflowButton, !available);
		}
		if (this.runSparqlQueryButton != null) {
			this.runSparqlQueryButton.setDisable(!available);
		}
		if (this.sparqlQueryCard != null) {
			setUnavailableStyle(this.sparqlQueryCard, !available);
		}
		if (!available) {
			if (this.sparqlResultsTxt != null) {
				showSparqlMessage("Generate an RDF output to run SPARQL queries.");
			}
		}
	}

	private void setValidationAvailable(boolean available) {
		if (this.validateWorkflowButton != null) {
			this.validateWorkflowButton.setDisable(!available);
			setUnavailableStyle(this.validateWorkflowButton, !available);
		}
		if (this.loadShaclButton != null) {
			this.loadShaclButton.setDisable(false);
		}
		if (this.validateCard != null) {
			setUnavailableStyle(this.validateCard, !available);
		}
		if (!available) {
			this.outputRevision++;
			this.explorationModelCache.invalidate();
			this.validationRevision++;
			for (ShapeValidationItem item : this.shapeValidationItems) {
				item.conforms = null;
				item.message = "Generate an RDF output to validate shapes.";
				item.violations = List.of();
			}
			if (this.shaclShapesList != null) {
				this.shaclShapesList.refresh();
			}
			refreshValidationFindings();
			if (this.validateStatusLabel != null) {
				this.validateStatusLabel.setText("Generate an RDF output to validate loaded SHACL shapes.");
			}
		}
	}

	private void setWorkflowDataAvailable(boolean filtersAvailable, boolean geometryAvailable, boolean sparqlAvailable) {
		setFiltersAvailable(filtersAvailable);
		setGeometryPreviewAvailable(geometryAvailable);
		setSparqlQueryAvailable(sparqlAvailable);
		setValidationAvailable(sparqlAvailable);
	}

	private void resetConversionOutput() {
		this.lastSuccessfulConversionRequest = null;
		this.pendingConversionRequest = null;
		setGeneratedResultsStale(false);
		setWorkflowDataAvailable(false, false, false);
		resetModelSummary();
	}

	private void resetModelSummary() {
		if (this.modelSummaryCard == null) {
			return;
		}
		this.collapsedModelSummaryTitle = "Model Summary";
		this.modelSummaryCard.setText(this.collapsedModelSummaryTitle);
		this.modelSummaryCard.setExpanded(false);
		this.modelSummaryCard.setVisible(false);
		this.modelSummaryCard.setManaged(false);
	}

	private ModelSummary createModelSummary(IFCtoLBDConverter converter, Map<String, Integer> mappedTypeCounts,
			Set<String> propertySetNames) {
		IfcModelStatistics statistics = converter.getIfcModelStatistics();
		ConversionSettings settings = this.readInSettings;
		File inputFile = new File(settings.ifcFileName());
		return createModelSummary(converter.getLoadedExpressSchema(), inputFile.length(), mappedTypeCounts,
				statistics, propertySetNames.size(), converter.getPropertysets().size(),
				settings.hasGeometry() || settings.hasElementWireframe(),
				settings.hasBuildingProperties());
	}

	static ModelSummary createModelSummary(String schema, long fileSize, Map<String, Integer> mappedTypeCounts,
			IfcModelStatistics statistics, int propertySetTypeCount, int propertySetOccurrenceCount,
			boolean geometryRequested, boolean propertiesRequested) {
		int mappedElementCount = mappedTypeCounts.values().stream().mapToInt(Integer::intValue).sum();
		int storeyCount = statistics.rootTypeCounts().getOrDefault("IfcBuildingStorey", 0);
		int spaceCount = statistics.rootTypeCounts().getOrDefault("IfcSpace", 0);
		int unmappedElementCount = Math.max(0, statistics.elementCount() - mappedElementCount);
		String geometryStatus = !geometryRequested ? "Not loaded (disabled)"
				: statistics.geometryAvailable() ? "Available" : "Requested, unavailable";
		String majorTypes = mappedTypeCounts.entrySet().stream()
				.filter(entry -> entry.getValue() != null && entry.getValue() > 0)
				.sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
						.thenComparing(Map.Entry.comparingByKey()))
				.limit(5)
				.map(entry -> entry.getKey() + " " + entry.getValue())
				.reduce((left, right) -> left + "  ·  " + right)
				.orElse("None detected");

		List<String> warnings = new ArrayList<>();
		if (schema == null || schema.isBlank()) {
			warnings.add("The IFC schema could not be identified.");
		}
		if (statistics.elementCount() == 0) {
			warnings.add("No IFC elements were detected.");
		}
		if (storeyCount == 0) {
			warnings.add("No building storeys were detected.");
		}
		if (spaceCount == 0) {
			warnings.add("No spaces were detected.");
		}
		if (unmappedElementCount > 0) {
			warnings.add(unmappedElementCount + " IFC element" + (unmappedElementCount == 1 ? " has" : "s have")
					+ " no mapped LBD product type.");
		}
		if (propertiesRequested && propertySetTypeCount == 0) {
			warnings.add("No property sets were detected.");
		}
		if (geometryRequested && !statistics.geometryAvailable()) {
			warnings.add("Geometry was requested but no geometry data was available.");
		}

		return new ModelSummary(schema == null || schema.isBlank() ? "Unknown" : schema, formatFileSize(fileSize),
				statistics.elementCount(), mappedElementCount, propertySetTypeCount, propertySetOccurrenceCount,
				storeyCount, spaceCount, geometryStatus, majorTypes, List.copyOf(warnings));
	}

	private void showModelSummary(ModelSummary summary) {
		this.summarySchemaLabel.setText(summary.schema());
		this.summaryFileSizeLabel.setText(summary.fileSize());
		this.summaryElementsLabel.setText(summary.elementCount() == summary.mappedElementCount()
				? Integer.toString(summary.elementCount())
				: summary.elementCount() + " (" + summary.mappedElementCount() + " mapped)");
		this.summaryPropertySetsLabel.setText(summary.propertySetTypeCount() + " types · "
				+ summary.propertySetOccurrenceCount() + " uses");
		this.summaryStoreysLabel.setText(Integer.toString(summary.storeyCount()));
		this.summarySpacesLabel.setText(Integer.toString(summary.spaceCount()));
		this.summaryGeometryLabel.setText(summary.geometryStatus());
		this.summaryMajorTypesLabel.setText(summary.majorTypes());
		this.summaryWarningsLabel.getStyleClass().remove("summary-ok");
		if (summary.warnings().isEmpty()) {
			this.summaryWarningsLabel.setText("No model warnings detected.");
			this.summaryWarningsLabel.getStyleClass().add("summary-ok");
		} else {
			this.summaryWarningsLabel.setText(summary.warnings().stream()
					.map(warning -> "• " + warning).reduce((left, right) -> left + "\n" + right).orElse(""));
		}
		this.collapsedModelSummaryTitle = collapsedModelSummaryTitle(summary);
		this.modelSummaryCard.setManaged(true);
		this.modelSummaryCard.setVisible(true);
		this.modelSummaryCard.setExpanded(false);
		updateModelSummaryTitle(false);
	}

	static String collapsedModelSummaryTitle(ModelSummary summary) {
		return String.format(Locale.ROOT, "Model Summary  Storeys %d  Spaces %d",
				summary.storeyCount(), summary.spaceCount());
	}

	private void updateModelSummaryTitle(boolean expanded) {
		this.modelSummaryCard.setText(expanded ? "Model Summary" : this.collapsedModelSummaryTitle);
	}

	static String formatFileSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		String[] units = { "KiB", "MiB", "GiB", "TiB" };
		double size = bytes;
		int unit = -1;
		do {
			size /= 1024.0;
			unit++;
		} while (size >= 1024.0 && unit < units.length - 1);
		return String.format(Locale.ROOT, size >= 10.0 ? "%.0f %s" : "%.1f %s", size, units[unit]);
	}

	private long beginWorkflow(String stage, double progress) {
		long operationId = ++this.operationSequence;
		this.activeOperationId = operationId;
		this.workflowStartedNanos = System.nanoTime();
		this.workflowStatusLabel.setText(stage);
		this.workflowProgressBar.setProgress(progress);
		this.workflowElapsedLabel.setText("00:00");
		this.cancelWorkflowButton.setDisable(false);
		setWorkflowStatusVisible(true);
		if (this.workflowElapsedTimeline != null) {
			this.workflowElapsedTimeline.stop();
		}
		this.workflowElapsedTimeline = new Timeline(
				new KeyFrame(Duration.seconds(1), event -> updateWorkflowElapsed()));
		this.workflowElapsedTimeline.setCycleCount(Timeline.INDEFINITE);
		this.workflowElapsedTimeline.play();
		return operationId;
	}

	private boolean isActiveOperation(long operationId) {
		return operationId >= 0 && operationId == this.activeOperationId;
	}

	private void updateWorkflowStage(long operationId, String stage, double progress) {
		if (!isActiveOperation(operationId)) {
			return;
		}
		this.workflowStatusLabel.setText(stage);
		this.workflowProgressBar.setProgress(progress);
		updateWorkflowElapsed();
	}

	private void finishWorkflow(long operationId, String status, boolean successful) {
		if (!isActiveOperation(operationId)) {
			return;
		}
		updateWorkflowElapsed();
		if (this.workflowElapsedTimeline != null) {
			this.workflowElapsedTimeline.stop();
		}
		this.workflowStatusLabel.setText(status);
		this.workflowProgressBar.setProgress(successful ? 1.0 : 0.0);
		this.cancelWorkflowButton.setDisable(true);
		this.activeOperationId = -1;
		setWorkflowStatusVisible(false);
	}

	private void setWorkflowStatusVisible(boolean visible) {
		this.workflowStatusCard.setManaged(visible);
		this.workflowStatusCard.setVisible(visible);
	}

	private void updateWorkflowElapsed() {
		long elapsedSeconds = Math.max(0L, (System.nanoTime() - this.workflowStartedNanos) / 1_000_000_000L);
		this.workflowElapsedLabel.setText(String.format(Locale.ROOT, "%02d:%02d", elapsedSeconds / 60,
				elapsedSeconds % 60));
	}

	@FXML
	private void cancelWorkflow() {
		long operationId = this.activeOperationId;
		if (!isActiveOperation(operationId)) {
			return;
		}
		if (this.running_read_in != null && !this.running_read_in.isDone()) {
			this.running_read_in.cancel(true);
		}
		if (this.running_conversion != null && !this.running_conversion.isDone()) {
			this.running_conversion.cancel(true);
		}
		if (this.running_validation != null && !this.running_validation.isDone()) {
			this.running_validation.cancel(true);
		}
		finishWorkflow(operationId, "Cancelled — read IFC again to continue", false);
		this.running_read_in = null;
		this.running_conversion = null;
		this.running_validation = null;
		this.runAfterReadIn = false;
		this.element_types_checkbox.setRoot(null);
		this.propertysets_checkbox.setRoot(null);
		resetConversionOutput();
		setRunReady(false);
		this.options_panel.setDisable(false);
		clearGeometryPreview("Conversion cancelled.");
		this.conversionTxt.appendText("Operation cancelled. Read the IFC model again before converting.\n");
	}

	private static void setUnavailableStyle(Node node, boolean unavailable) {
		if (node == null) {
			return;
		}
		if (unavailable) {
			if (!node.getStyleClass().contains("data-unavailable")) {
				node.getStyleClass().add("data-unavailable");
			}
		} else {
			node.getStyleClass().remove("data-unavailable");
		}
	}

	private void alignFloatingCardsIfNeeded() {
		if (this.sparqlCardPositioned || this.floatingWorkspace == null) {
			return;
		}
		double y = FLOATING_CARD_INITIAL_Y;
		y = positionFloatingCardInLine(this.options_panel, y);
		y = positionFloatingCardInLine(this.filtersCard, y);
		y = positionFloatingCardInLine(this.geometryCard, y);
		y = positionFloatingCardInLine(this.sparqlQueryCard, y);
		positionFloatingCardInLine(this.validateCard, y);
		this.sparqlCardPositioned = true;
	}

	private double positionFloatingCardInLine(TitledPane card, double y) {
		if (card == null) {
			return y;
		}
		card.setLayoutX(FLOATING_CARD_INITIAL_X);
		card.setLayoutY(y);
		return y + FLOATING_CARD_HEADER_HEIGHT + FLOATING_CARD_INITIAL_GAP;
	}

	private void setRunReady(boolean ready) {
		setRunButtonReady(this.convert2RDFButton, ready);
		setRunButtonReady(this.basicConvert2RDFButton, ready);
		if (ready) {
			setSelectIfcDefault(false);
		}
	}

	private static void setRunButtonReady(Button button, boolean ready) {
		if (button == null) {
			return;
		}
		button.setDisable(!ready);
		button.setDefaultButton(ready);
		button.getStyleClass().removeAll("workflow-button", "workflow-run-button");
		button.getStyleClass().add(ready ? "workflow-run-button" : "workflow-button");
	}

	private void setSelectIfcDefault(boolean isDefault) {
		if (this.selectIFCFileButton != null) {
			this.selectIFCFileButton.setDefaultButton(isDefault);
		}
		if (this.basicSelectIFCFileButton != null) {
			this.basicSelectIFCFileButton.setDefaultButton(isDefault);
		}
	}

	private void setIfcFileLabel(String text) {
		if (this.labelIFCFile != null) {
			this.labelIFCFile.setText(text);
		}
		if (this.basicLabelIFCFile != null) {
			this.basicLabelIFCFile.setText(text);
		}
	}

	private void setTargetFileLabel(String text) {
		if (this.labelTargetFile != null) {
			this.labelTargetFile.setText(text);
		}
	}

	/** Keeps the editable advanced-workflow output field as the conversion target. */
	private void setupTargetFileEditor() {
		this.labelTargetFile.textProperty().addListener((observable, oldValue, newValue) -> {
			String target = newValue == null ? "" : newValue.trim();
			if (target.isEmpty() || "Target path will be generated after input selection".equals(target)) {
				this.rdfTargetName = null;
				refreshReactiveSettings();
				return;
			}
			this.rdfTargetName = target;
			File parent = new File(target).getAbsoluteFile().getParentFile();
			if (parent != null && parent.isDirectory()) {
				this.prefs.put("ifc_target_directory", parent.getAbsolutePath());
			}
			refreshReactiveSettings();
		});
	}

	private void setupOutputFormatChoices() {
		String fallback = this.prefs.getBoolean("export_as_jsonld", false) ? "JSON-LD" : "Turtle TTL";
		String initialValue = this.prefs.get("desktop_output_format", fallback);
		if (!List.of("Turtle TTL", "JSON-LD", "ICDD package").contains(initialValue)) {
			initialValue = "ICDD".equals(initialValue) ? "ICDD package" : fallback;
		}
		this.outputJSONorTTL.getItems().setAll("Turtle TTL", "JSON-LD", "ICDD package");
		this.basicOutputJSONorTTL.getItems().setAll("Turtle TTL", "JSON-LD", "ICDD package");
		this.outputJSONorTTL.setValue(initialValue);
		this.basicOutputJSONorTTL.setValue(initialValue);
		this.outputJSONorTTL.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null && !Objects.equals(this.basicOutputJSONorTTL.getValue(), newValue)) {
				this.basicOutputJSONorTTL.setValue(newValue);
			}
			updateTargetFileExtension();
			refreshReactiveSettings();
		});
		this.basicOutputJSONorTTL.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue != null && !Objects.equals(this.outputJSONorTTL.getValue(), newValue)) {
				this.outputJSONorTTL.setValue(newValue);
			}
			updateTargetFileExtension();
			refreshReactiveSettings();
		});
	}

	private static void setDropHandlers(Node node, EventHandler<DragEvent> dragOverHandler,
			EventHandler<DragEvent> dragDroppedHandler) {
		if (node == null) {
			return;
		}
		node.setOnDragOver(dragOverHandler);
		node.setOnDragDropped(dragDroppedHandler);
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
		configureFloatingCardWindowButton(minimizeButton);
		setFloatingCardWindowButtonAction(card, minimizeButton, () -> {
			card.toFront();
			minimizeFloatingCard(card);
		});

		Button maximizeButton = new Button("□");
		configureFloatingCardWindowButton(maximizeButton);
		setFloatingCardWindowButtonAction(card, maximizeButton, () -> bringFloatingCardToFront(card));

		HBox header = new HBox(6, titleLabel, spacer, minimizeButton, maximizeButton);
		header.getStyleClass().add("floating-card-title-bar");
		header.setAlignment(Pos.CENTER_LEFT);
		header.setFillHeight(false);
		header.setPickOnBounds(true);
		header.minWidthProperty().bind(card.widthProperty().subtract(34));
		header.prefWidthProperty().bind(card.widthProperty().subtract(34));
		header.maxWidthProperty().bind(card.widthProperty().subtract(34));
		return header;
	}

	private static void configureFloatingCardWindowButton(Button button) {
		button.getStyleClass().add("floating-card-window-button");
		button.setFocusTraversable(false);
		button.setPickOnBounds(false);
		button.setMinSize(FLOATING_CARD_WINDOW_BUTTON_WIDTH, FLOATING_CARD_WINDOW_BUTTON_HEIGHT);
		button.setPrefSize(FLOATING_CARD_WINDOW_BUTTON_WIDTH, FLOATING_CARD_WINDOW_BUTTON_HEIGHT);
		button.setMaxSize(FLOATING_CARD_WINDOW_BUTTON_WIDTH, FLOATING_CARD_WINDOW_BUTTON_HEIGHT);
	}

	private void setupFloatingWindowButtonRouting() {
		if (this.floatingWorkspace == null) {
			return;
		}
		this.floatingWorkspace.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			if (event.getButton() != MouseButton.PRIMARY) {
				return;
			}
			Button targetButton = buttonEventTarget(event.getTarget());
			if (targetButton != null && !this.floatingWindowButtonActions.containsKey(targetButton)) {
				// Content controls such as Geometry Fit own their events. Coordinate-based
				// window-button routing must not activate a control on an overlapping card.
				return;
			}
			Button button = findFloatingWindowButtonAt(event);
			if (button != null) {
				this.pressedFloatingWindowButton = button;
				event.consume();
			}
		});
		this.floatingWorkspace.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
			Button targetButton = buttonEventTarget(event.getTarget());
			if (targetButton != null && !this.floatingWindowButtonActions.containsKey(targetButton)) {
				this.pressedFloatingWindowButton = null;
				return;
			}
			if (this.pressedFloatingWindowButton == null) {
				return;
			}
			Button pressedButton = this.pressedFloatingWindowButton;
			this.pressedFloatingWindowButton = null;
			if (event.getButton() == MouseButton.PRIMARY && pressedButton == findFloatingWindowButtonAt(event)) {
				Runnable action = this.floatingWindowButtonActions.get(pressedButton);
				if (action != null) {
					action.run();
				}
			}
			event.consume();
		});
	}

	private Button findFloatingWindowButtonAt(MouseEvent event) {
		Button topButton = null;
		int topCardIndex = -1;
		for (Button button : this.floatingWindowButtonActions.keySet()) {
			TitledPane card = this.floatingWindowButtonCards.get(button);
			if (card == null || !card.isVisible() || button.getScene() == null
					|| !isInsideWindowButtonVisualBounds(button, event)) {
				continue;
			}
			int cardIndex = this.floatingWorkspace.getChildren().indexOf(card);
			if (cardIndex >= topCardIndex) {
				topCardIndex = cardIndex;
				topButton = button;
			}
		}
		return topButton;
	}

	private void setFloatingCardWindowButtonAction(TitledPane card, Button button, Runnable action) {
		this.floatingWindowButtonActions.put(button, action);
		this.floatingWindowButtonCards.put(button, card);
		button.setOnAction(null);
		button.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			if (event.getButton() == MouseButton.PRIMARY && isInsideWindowButtonVisualBounds(button, event)) {
				this.pressedFloatingWindowButton = button;
				event.consume();
			}
		});
		button.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
			if (this.pressedFloatingWindowButton == button) {
				this.pressedFloatingWindowButton = null;
				if (event.getButton() == MouseButton.PRIMARY && isInsideWindowButtonVisualBounds(button, event)) {
					action.run();
				}
				event.consume();
			}
		});
		button.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
			if (this.pressedFloatingWindowButton == button) {
				this.pressedFloatingWindowButton = null;
			}
		});
	}

	private static boolean isInsideWindowButtonVisualBounds(Button button, MouseEvent event) {
		Point2D localPoint = button.screenToLocal(event.getScreenX(), event.getScreenY());
		if (localPoint == null) {
			return false;
		}
		double visualMinX = Math.max(0, (button.getWidth() - FLOATING_CARD_WINDOW_BUTTON_WIDTH) / 2.0);
		double visualMinY = Math.max(0, (button.getHeight() - FLOATING_CARD_WINDOW_BUTTON_HEIGHT) / 2.0);
		double visualMaxX = visualMinX + FLOATING_CARD_WINDOW_BUTTON_WIDTH;
		double visualMaxY = visualMinY + FLOATING_CARD_WINDOW_BUTTON_HEIGHT;
		return localPoint.getX() >= visualMinX && localPoint.getX() <= visualMaxX && localPoint.getY() >= visualMinY
				&& localPoint.getY() <= visualMaxY;
	}

	private void addFloatingCardBehavior(TitledPane card) {
		if (card == null) {
			return;
		}
		card.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
			if (!isInsideVisibleFloatingCardArea(card, event)) {
				return;
			}
			if (event.getButton() == MouseButton.PRIMARY && !isButtonEventTarget(event.getTarget())
					&& !isGeometrySceneEventTarget(event.getTarget())) {
				if (isCardHeaderEvent(card, event)) {
					card.toFront();
				} else {
					bringFloatingCardToFront(card);
				}
			}
			if (!isCardHeaderEvent(card, event)) {
				return;
			}
			this.floatingMouseX = event.getSceneX();
			this.floatingMouseY = event.getSceneY();
			this.floatingCardX = card.getLayoutX();
			this.floatingCardY = card.getLayoutY();
			event.consume();
		});
		card.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
			if (!isInsideVisibleFloatingCardArea(card, event) || !isCardHeaderEvent(card, event)) {
				return;
			}
			double nextX = this.floatingCardX + event.getSceneX() - this.floatingMouseX;
			double nextY = this.floatingCardY + event.getSceneY() - this.floatingMouseY;
			card.setLayoutX(clamp(nextX, 0, Math.max(0, this.floatingWorkspace.getWidth() - card.getWidth())));
			card.setLayoutY(clamp(nextY, 0, Math.max(0, this.floatingWorkspace.getHeight() - FLOATING_CARD_HEADER_HEIGHT)));
			event.consume();
		});
		card.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
			if (isInsideVisibleFloatingCardArea(card, event) && isCardHeaderEvent(card, event)) {
				event.consume();
			}
		});
		card.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
			if (isInsideVisibleFloatingCardArea(card, event) && isCardHeaderEvent(card, event)) {
				event.consume();
			}
		});
	}

	private void clampFloatingCardsToWorkspace() {
		clampFloatingCardToWorkspace(this.options_panel);
		clampFloatingCardToWorkspace(this.filtersCard);
		clampFloatingCardToWorkspace(this.geometryCard);
		clampFloatingCardToWorkspace(this.sparqlQueryCard);
		clampFloatingCardToWorkspace(this.validateCard);
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

	private boolean isCardHeaderEvent(TitledPane card, MouseEvent event) {
		return event.getButton() == MouseButton.PRIMARY && event.getY() <= FLOATING_CARD_HEADER_HEIGHT
				&& isInsideVisibleFloatingCardArea(card, event) && !isButtonEventTarget(event.getTarget());
	}

	private boolean isInsideVisibleFloatingCardArea(TitledPane card, MouseEvent event) {
		Point2D localPoint = card.screenToLocal(event.getScreenX(), event.getScreenY());
		if (localPoint == null) {
			return false;
		}
		double visibleWidth = card.getWidth() > 0 ? card.getWidth() : card.getPrefWidth();
		double visibleHeight = isFloatingCardMinimized(card) ? FLOATING_CARD_HEADER_HEIGHT
				: Math.max(FLOATING_CARD_HEADER_HEIGHT, card.getHeight() > 0 ? card.getHeight() : card.getPrefHeight());
		return localPoint.getX() >= 0 && localPoint.getX() <= visibleWidth && localPoint.getY() >= 0
				&& localPoint.getY() <= visibleHeight;
	}

	private boolean isButtonEventTarget(Object target) {
		return buttonEventTarget(target) != null;
	}

	private static Button buttonEventTarget(Object target) {
		if (!(target instanceof Node node)) {
			return target instanceof Button button ? button : null;
		}
		while (node != null) {
			if (node instanceof Button button) {
				return button;
			}
			node = node.getParent();
		}
		return null;
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
		if (card.getStyleClass().contains("data-unavailable")) {
			card.toFront();
			return;
		}
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
		if (isFloatingCardMinimized(card)) {
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
			card.setPickOnBounds(false);
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
			card.setPickOnBounds(false);
			renderGeometryScene();
		});
	}

	private boolean isFloatingCardMinimized(TitledPane card) {
		return !card.isExpanded() && card.getPrefHeight() <= FLOATING_CARD_HEADER_HEIGHT + 1;
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
			card.setPickOnBounds(true);
			card.setMinHeight(Region.USE_COMPUTED_SIZE);
			card.setMaxHeight(Region.USE_COMPUTED_SIZE);
			card.setPrefHeight(targetHeight);
			card.setExpanded(true);
			renderGeometryScene();
			return;
		}

		card.setExpanded(true);
		card.setPickOnBounds(true);
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

	private void loadDefaultShapes() {
		try {
			File defaultShapes = resourceFile(DEFAULT_SHACL_RESOURCE);
			LoadedShapes loaded = loadShapesFile(defaultShapes, DEFAULT_SHACL_RESOURCE);
			this.loadedShapes.add(new LoadedShapes(loaded.file(), loaded.name(), loaded.shapes(), loaded.items(), true));
			this.shapeValidationItems.addAll(loaded.items());
			refreshValidationFindings();
			if (this.validateStatusLabel != null) {
				this.validateStatusLabel.setText("Loaded " + DEFAULT_SHACL_RESOURCE + ".");
			}
		} catch (Exception e) {
			if (this.validateStatusLabel != null) {
				this.validateStatusLabel.setText("Default SHACL load failed: " + e.getMessage());
			}
		}
	}

	private void rememberAddedShapes() {
		List<RememberedShapes.Entry> entries = new ArrayList<>();
		for (LoadedShapes loaded : this.loadedShapes) {
			if (loaded.builtIn()) continue;
			for (ShapeValidationItem item : loaded.items()) {
				entries.add(new RememberedShapes.Entry(loaded.file().getAbsolutePath(), item.persistentKey));
			}
		}
		RememberedShapes.write(this.prefs, entries);
	}

	private void restoreRememberedShapes() {
		Map<String, Set<String>> selectedByFile = new LinkedHashMap<>();
		for (RememberedShapes.Entry entry : RememberedShapes.read(this.prefs)) {
			selectedByFile.computeIfAbsent(entry.filePath(), ignored -> new HashSet<>()).add(entry.shapeNode());
		}
		int restored = 0;
		List<String> unavailable = new ArrayList<>();
		for (Map.Entry<String, Set<String>> entry : selectedByFile.entrySet()) {
			File file = new File(entry.getKey());
			try {
				LoadedShapes loaded = loadShapesFile(file, file.getName());
				loaded.items().removeIf(item -> !entry.getValue().contains(item.persistentKey));
				if (loaded.items().isEmpty()) continue;
				this.loadedShapes.add(loaded);
				this.shapeValidationItems.addAll(loaded.items());
				restored += loaded.items().size();
			} catch (Exception e) {
				unavailable.add(file.getName());
			}
		}
		if (!unavailable.isEmpty()) {
			this.validateStatusLabel.setText("Could not reopen SHACL files: " + String.join(", ", unavailable));
		} else if (restored > 0) {
			this.validateStatusLabel.setText("Reopened " + restored + " saved SHACL shapes.");
		}
		refreshValidationFindings();
	}

	private void loadShapesFileAsync(File shapeFile, String sourceName) {
		if (this.loadShaclButton != null) {
			this.loadShaclButton.setDisable(true);
		}
		if (this.validateStatusLabel != null) {
			this.validateStatusLabel.setText("Loading " + sourceName + "...");
		}
		this.executor.submit(() -> {
			try {
				LoadedShapes loaded = loadShapesFile(shapeFile, sourceName);
				Platform.runLater(() -> {
					this.loadedShapes.add(loaded);
					this.shapeValidationItems.addAll(loaded.items());
					rememberAddedShapes();
					this.validateStatusLabel.setText("Loaded " + loaded.items().size() + " shapes from " + sourceName
							+ ".");
					this.shaclShapesList.refresh();
					refreshValidationFindings();
					if (isValidationAvailable()) {
						validateLoadedShapesAsync();
					}
				});
			} catch (Exception e) {
				Platform.runLater(() -> this.validateStatusLabel.setText("SHACL load failed: " + e.getMessage()));
			} finally {
				Platform.runLater(() -> this.loadShaclButton.setDisable(false));
			}
		});
	}

	private void removeShapeItem(ShapeValidationItem item) {
		if (item == null) {
			return;
		}
		for (LoadedShapes loaded : this.loadedShapes) {
			loaded.items().remove(item);
		}
		this.loadedShapes.removeIf(loaded -> loaded.items().isEmpty());
		this.shapeValidationItems.remove(item);
		rememberAddedShapes();
		if (this.shaclShapesList != null) {
			this.shaclShapesList.refresh();
		}
		refreshValidationFindings();
		if (this.validateStatusLabel != null) {
			this.validateStatusLabel.setText("Removed " + item.displayName + ".");
		}
		if (isValidationAvailable() && !this.loadedShapes.isEmpty()) {
			validateLoadedShapesAsync();
		}
	}

	private LoadedShapes loadShapesFile(File shapeFile, String sourceName) {
		Graph shapesGraph = RDFDataMgr.loadGraph(shapeFile.getAbsolutePath());
		Shapes shapes = Shapes.parse(shapesGraph);
		List<ShapeValidationItem> items = new ArrayList<>();
		for (Shape shape : shapes.getTargetShapes()) {
			items.add(new ShapeValidationItem(sourceName, formatShapeNode(shape.getShapeNode()), shape.getShapeNode(),
					shapePersistenceKey(shape.getShapeNode(), items.size()),
					collectConstraintNodes(shape)));
		}
		if (items.isEmpty()) {
			for (Shape shape : shapes) {
				items.add(new ShapeValidationItem(sourceName, formatShapeNode(shape.getShapeNode()), shape.getShapeNode(),
						shapePersistenceKey(shape.getShapeNode(), items.size()),
						collectConstraintNodes(shape)));
			}
		}
		return new LoadedShapes(shapeFile, sourceName, shapes, items, false);
	}

	private static String shapePersistenceKey(org.apache.jena.graph.Node node, int index) {
		return node.isURI() ? node.getURI() : "@index:" + index;
	}

	private void validateLoadedShapesAsync() {
		validateLoadedShapesAsync(-1);
	}

	private void validateLoadedShapesAsync(long workflowOperationId) {
		long revision = ++this.validationRevision;
		if (!isValidationAvailable()) {
			setValidationAvailable(false);
			if (workflowOperationId >= 0) {
				finishWorkflow(workflowOperationId, "Conversion complete", true);
			}
			return;
		}
		List<LoadedShapes> shapesToValidate = new ArrayList<>(this.loadedShapes);
		if (shapesToValidate.isEmpty()) {
			this.validateStatusLabel.setText("Load SHACL shapes before validating.");
			if (workflowOperationId >= 0) {
				finishWorkflow(workflowOperationId, "Conversion complete", true);
			}
			return;
		}
		if (workflowOperationId >= 0) {
			updateWorkflowStage(workflowOperationId, "Validating generated RDF", 0.97);
		}
		File outputFile = new File(this.lastSuccessfulConversionRequest.settings().rdfTargetName());
		for (ShapeValidationItem item : this.shapeValidationItems) {
			item.conforms = null;
			item.message = "Validation pending.";
			item.violations = List.of();
		}
		this.shaclShapesList.refresh();
		refreshValidationFindings();
		this.validateStatusLabel.setText("Validating " + this.shapeValidationItems.size() + " shapes...");
		this.running_validation = this.executor.submit(() -> {
			Model dataModel = null;
			try {
				dataModel = readExplorationModel(outputFile);
				List<ShapeValidationResult> updated = new ArrayList<>();
				for (LoadedShapes loaded : shapesToValidate) {
					ValidationReport report = ShaclValidator.get().validate(loaded.shapes(), dataModel.getGraph());
					List<ShapeValidationItem> copies = loaded.items().stream().map(item -> new ShapeValidationItem(
							item.sourceName, item.displayName, item.shapeNode, item.persistentKey, item.constraintNodes)).toList();
					applyValidationReport(copies, report, dataModel);
					for (int i = 0; i < copies.size(); i++) {
						ShapeValidationItem copy = copies.get(i);
						updated.add(new ShapeValidationResult(loaded.items().get(i), copy.conforms, copy.message,
								copy.violations));
					}
				}
				Platform.runLater(() -> {
					publishValidationResults(revision, updated, outputFile.getName());
					if (workflowOperationId >= 0) {
						finishWorkflow(workflowOperationId, "Conversion and validation complete", true);
					}
				});
			} catch (Exception e) {
				Platform.runLater(() -> {
					if (revision == this.validationRevision)
						this.validateStatusLabel.setText("Validation failed: " + e.getMessage());
					if (workflowOperationId >= 0) {
						finishWorkflow(workflowOperationId, "Conversion complete; validation failed", true);
					}
				});
			} finally {
				if (dataModel != null) dataModel.close();
			}
		});
	}

	private void publishValidationResults(long revision, List<ShapeValidationResult> updated, String outputName) {
		if (revision != this.validationRevision) return;
		for (ShapeValidationResult result : updated) {
			result.item().conforms = result.conforms();
			result.item().message = result.message();
			result.item().violations = result.violations();
		}
		if (this.shaclShapesList != null) this.shaclShapesList.refresh();
		refreshValidationFindings();
		if (this.validateStatusLabel != null)
			this.validateStatusLabel.setText("Validated " + updated.size() + " shapes against "
					+ outputName + ".");
	}

	private void applyValidationReport(List<ShapeValidationItem> items, ValidationReport report, Model dataModel) {
		for (ShapeValidationItem item : items) {
			item.conforms = true;
			item.message = "Validation passed.";
			item.violations = List.of();
		}
		for (ReportEntry entry : report.getEntries()) {
			org.apache.jena.graph.Node source = entry.source();
			for (ShapeValidationItem item : items) {
				if (source != null && item.constraintNodes.contains(source)) {
					item.conforms = false;
					item.message = entry.message() == null || entry.message().isBlank() ? "Validation failed."
							: entry.message();
					List<ValidationFindings.Finding> violations = new ArrayList<>(item.violations);
					violations.add(new ValidationFindings.Finding(item.displayName, item.sourceName,
							ValidationFindings.nodeText(entry.focusNode()), resolveFocusResourceUri(dataModel, entry.focusNode()),
							entry.resultPath() == null ? null : entry.resultPath().toString(),
							ValidationFindings.nodeText(entry.value()), item.message,
							ValidationFindings.levelOf(entry.severity())));
					item.violations = List.copyOf(violations);
				}
			}
		}
	}

	private static String resolveFocusResourceUri(Model model, org.apache.jena.graph.Node focusNode) {
		if (model == null || focusNode == null || !focusNode.isURI()) {
			return focusNode == null ? null : focusNode.toString();
		}
		Resource start = model.createResource(focusNode.getURI());
		Deque<Resource> pending = new ArrayDeque<>();
		Set<Resource> visited = new HashSet<>();
		pending.add(start);
		for (int depth = 0; depth < 4 && !pending.isEmpty(); depth++) {
			int levelSize = pending.size();
			for (int i = 0; i < levelSize; i++) {
				Resource candidate = pending.removeFirst();
				if (!visited.add(candidate)) continue;
				if (candidate.isURIResource() && (model.contains(candidate, model.createProperty(OMG_HAS_GEOMETRY))
						|| model.contains(candidate, org.apache.jena.vocabulary.RDF.type,
								model.createResource(BOT_ELEMENT)))) {
					return candidate.getURI();
				}
				model.listStatements(null, null, candidate).forEachRemaining(statement -> {
					if (statement.getSubject().isResource()) pending.addLast(statement.getSubject());
				});
			}
		}
		return focusNode.getURI();
	}

	private static Set<org.apache.jena.graph.Node> collectConstraintNodes(Shape shape) {
		Set<org.apache.jena.graph.Node> nodes = new HashSet<>();
		collectConstraintNodes(shape, nodes);
		return nodes;
	}

	private static void collectConstraintNodes(Shape shape, Set<org.apache.jena.graph.Node> nodes) {
		nodes.add(shape.getShapeNode());
		for (Shape propertyShape : shape.getPropertyShapes()) {
			collectConstraintNodes(propertyShape, nodes);
		}
	}

	private static String formatShapeNode(org.apache.jena.graph.Node node) {
		if (node == null) {
			return "Unnamed shape";
		}
		if (node.isURI()) {
			String localName = node.getLocalName();
			return localName == null || localName.isBlank() ? node.getURI() : localName;
		}
		if (node.isBlank()) {
			return "_:" + node.getBlankNodeLabel();
		}
		return node.toString();
	}

	private static File resourceFile(String resourceName) throws IOException, URISyntaxException {
		URL resourceUrl = IFCtoLBDController.class.getClassLoader().getResource(resourceName);
		if (resourceUrl == null) {
			throw new IOException("Resource not found: " + resourceName);
		}
		if ("file".equals(resourceUrl.getProtocol())) {
			return new File(resourceUrl.toURI());
		}
		File tempFile = File.createTempFile(resourceName.replaceAll("[^A-Za-z0-9._-]", "_"), ".ttl");
		tempFile.deleteOnExit();
		try (InputStream input = resourceUrl.openStream()) {
			Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return tempFile;
	}

	private static Model readOutputModel(File outputFile) {
		Model model = ModelFactory.createDefaultModel();
		try {
			Lang language = RDFLanguages.filenameToLang(outputFile.getName(), Lang.TURTLE);
			RDFDataMgr.read(model, outputFile.getAbsolutePath(), language);
			return model;
		} catch (RuntimeException e) {
			model.close();
			throw e;
		}
	}

	private static SparqlOutput executeSparqlQueryData(Model model, String queryText, long startedNanos) {
		Query query = QueryFactory.create(queryText);
		try (QueryExecution queryExecution = QueryExecutionFactory.create(query, model)) {
			if (query.isSelectType()) {
				SelectResults results = collectSelectResults(queryExecution.execSelect());
				return new SparqlOutput(results, null, elapsedMillis(startedNanos));
			}
			if (query.isAskType()) {
				return new SparqlOutput(null, "ASK result: " + queryExecution.execAsk(), elapsedMillis(startedNanos));
			}
			if (query.isConstructType()) {
				Model result = queryExecution.execConstruct();
				try {
					return new SparqlOutput(null, serializeQueryModel(result), elapsedMillis(startedNanos));
				} finally {
					result.close();
				}
			}
			if (query.isDescribeType()) {
				Model result = queryExecution.execDescribe();
				try {
					return new SparqlOutput(null, serializeQueryModel(result), elapsedMillis(startedNanos));
				} finally {
					result.close();
				}
			}
		}
		return new SparqlOutput(null, "Unsupported SPARQL query type.", elapsedMillis(startedNanos));
	}

	private static long elapsedMillis(long startedNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
	}

	static SelectResults collectSelectResults(ResultSet resultSet) {
		List<String> variables = List.copyOf(resultSet.getResultVars());
		List<SparqlRow> rows = new ArrayList<>();
		while (resultSet.hasNext() && rows.size() < MAX_SPARQL_RESULT_ROWS) {
			QuerySolution solution = resultSet.nextSolution();
			Map<String, SparqlCell> cells = new LinkedHashMap<>();
			for (String variable : variables) {
				RDFNode node = solution.get(variable);
				String uri = node != null && node.isURIResource() ? node.asResource().getURI() : null;
				cells.put(variable, new SparqlCell(node == null ? "" : formatSparqlNode(node), uri));
			}
			rows.add(new SparqlRow(java.util.Collections.unmodifiableMap(cells)));
		}
		return new SelectResults(variables, List.copyOf(rows), resultSet.hasNext());
	}

	private void showSparqlOutput(SparqlOutput output) {
		this.sparqlResultMetrics = output.select() == null
				? "Completed in " + output.elapsedMillis() + " ms"
				: output.select().rows().size() + " rows"
						+ (output.select().limited() ? " (first " + MAX_SPARQL_RESULT_ROWS + ")" : "")
						+ " · " + output.elapsedMillis() + " ms";
		this.sparqlResultsStatusLabel.setText(this.sparqlResultMetrics);
		this.currentSelectResults = output.select();
		boolean select = output.select() != null;
		this.copySparqlResultsButton.setDisable(!select);
		this.exportSparqlResultsButton.setDisable(!select);
		this.sparqlResultsTable.setManaged(select);
		this.sparqlResultsTable.setVisible(select);
		this.sparqlResultsTxt.setManaged(!select);
		this.sparqlResultsTxt.setVisible(!select);
		if (select) {
			showSelectResults(output.select());
		} else {
			this.sparqlResultsTable.getItems().clear();
			this.sparqlResultsTable.getColumns().clear();
			this.sparqlResultsTxt.setText(output.text());
		}
	}

	private void showSparqlMessage(String message) {
		this.currentSelectResults = null;
		this.sparqlResultMetrics = "";
		this.copySparqlResultsButton.setDisable(true);
		this.exportSparqlResultsButton.setDisable(true);
		this.sparqlResultsTable.setManaged(false);
		this.sparqlResultsTable.setVisible(false);
		this.sparqlResultsTable.getItems().clear();
		this.sparqlResultsTable.getColumns().clear();
		this.sparqlResultsTxt.setManaged(true);
		this.sparqlResultsTxt.setVisible(true);
		this.sparqlResultsTxt.setText(message);
		this.sparqlResultsStatusLabel.setText(message.startsWith("Running query") ? "Running..." : "No SELECT results");
	}

	private void showSelectResults(SelectResults results) {
		this.sparqlResultsTable.setPlaceholder(new Label("No rows matched this query."));
		this.sparqlResultsTable.getSortOrder().clear();
		this.sparqlResultsTable.getColumns().clear();
		for (String variable : results.variables()) {
			TableColumn<SparqlRow, SparqlCell> column = new TableColumn<>(variable);
			column.setMinWidth(90);
			column.setPrefWidth(variable.toLowerCase(Locale.ROOT).contains("count") ? 105 : 220);
			column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().cells().get(variable)));
			column.setComparator(Comparator.nullsFirst(Comparator.comparing(SparqlCell::text,
					String.CASE_INSENSITIVE_ORDER)));
			column.setCellFactory(ignored -> new TableCell<>() {
				private final Hyperlink link = new Hyperlink();

				@Override
				protected void updateItem(SparqlCell cell, boolean empty) {
					super.updateItem(cell, empty);
					setText(null);
					setGraphic(null);
					setTooltip(null);
					if (empty || cell == null) return;
					if (cell.resourceUri() != null) {
						this.link.setText(cell.text());
						this.link.setTooltip(new Tooltip(cell.resourceUri()));
						this.link.setOnAction(event -> highlightSparqlResource(cell.resourceUri()));
						setGraphic(this.link);
					} else {
						setText(cell.text());
					}
				}
			});
			this.sparqlResultsTable.getColumns().add(column);
		}
		this.sparqlResultsTable.setItems(FXCollections.observableArrayList(results.rows()));
	}

	static String selectResultsAsCsv(SelectResults results, List<SparqlRow> rows) {
		StringBuilder csv = new StringBuilder();
		for (int i = 0; i < results.variables().size(); i++) {
			if (i > 0) csv.append(',');
			csv.append(escapeCsv(results.variables().get(i)));
		}
		csv.append('\n');
		for (SparqlRow row : rows) {
			for (int i = 0; i < results.variables().size(); i++) {
				if (i > 0) csv.append(',');
				SparqlCell cell = row.cells().get(results.variables().get(i));
				csv.append(escapeCsv(cell == null ? "" : cell.text()));
			}
			csv.append('\n');
		}
		return csv.toString();
	}

	private static String escapeCsv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private static String formatSparqlNode(RDFNode node) {
		if (node.isLiteral()) {
			return node.asLiteral().getString();
		}
		if (node.isResource() && node.asResource().isURIResource()) {
			return node.asResource().getURI();
		}
		return node.toString();
	}

	private static String serializeQueryModel(Model model) {
		StringBuilder output = new StringBuilder();
		model.write(new StringBuilderWriter(output), "TURTLE");
		if (output.isEmpty()) {
			return "No triples.";
		}
		return output.toString();
	}

	private static class StringBuilderWriter extends java.io.Writer {
		private final StringBuilder output;

		private StringBuilderWriter(StringBuilder output) {
			this.output = output;
		}

		@Override
		public void write(char[] cbuf, int off, int len) {
			this.output.append(cbuf, off, len);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	}

	private void clearGeometryPreview(String message) {
		this.currentPreviewMesh = null;
		if (this.geometryEmptyStateLabel != null) {
			this.geometryEmptyStateLabel.setText(message);
			this.geometryEmptyStateLabel.setVisible(true);
			this.geometryEmptyStateLabel.setManaged(true);
		}
		if (this.geometryFitButton != null) {
			this.geometryFitButton.setVisible(false);
			this.geometryFitButton.setManaged(false);
		}
		this.geometryExplodeSlider.setValue(0);
		this.geometryExplodeSlider.setDisable(true);
		this.selectedResourceUri = null;
		this.selectedPropertyPath = null;
		this.sparqlSelectionHighlighted = false;
		renderGeometryScene();
		clearSelectedResourceDetails();
		appendGeometryPreviewMessage(message);
	}

	private void clearSelectedResourceDetails() {
		if (this.selectedObjectHeadingLabel == null) return;
		this.selectedObjectHeadingLabel.setText("Select an object in 3D, SPARQL, validation, or Filters");
		this.selectedObjectTypeLabel.setText("IFC type: —");
		this.selectedObjectGuidLabel.setText("GUID: —");
		this.selectedObjectUriLabel.setText("RDF URI: —");
		this.selectedObjectPropertiesTxt.setText("");
	}

	private void scheduleGeometryPreview(ConversionSettings settings) {
		if (settings == null || !settings.hasGeometry()) {
			Platform.runLater(() -> clearGeometryPreview(
					"Geometry was not generated for this conversion. Enable Geometry and run again."));
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
		Model model = readExplorationModel(rdfFile);
		try {
			Property objProperty = model.createProperty(GEOMETRY_OBJ_PROPERTY);
			Property materialColorProperty = model.createProperty(GEOMETRY_MTL_KD_PROPERTY);
			Property materialProperty = model.createProperty(GEOMETRY_MTL_PROPERTY);
			var geometries = model.listStatements(null, objProperty, (RDFNode) null);
			while (geometries.hasNext() && !builder.clipped()) {
				Statement geometry = geometries.next();
				if (!geometry.getObject().isLiteral()) continue;
				int color = geometryColor(geometry.getSubject(), materialColorProperty, materialProperty);
				Resource element = geometryOwner(model, geometry.getSubject());
				ResourceDetails details = describeResource(model, element);
				try {
					String obj = new String(Base64.getDecoder().decode(
							geometry.getString()), StandardCharsets.UTF_8);
					builder.addObj(obj, color, details, geometry.getSubject().isURIResource()
							? geometry.getSubject().getURI() : geometry.getSubject().toString());
				} catch (IllegalArgumentException ignored) {
					// Ignore malformed geometry literals and continue with the remaining geometry.
				}
			}
		} finally {
			model.close();
		}
		return builder.toPreviewMesh();
	}

	private static Model readExplorationModel(File outputFile) {
		Model model = readOutputModel(outputFile);
		try {
			String path = outputFile.getAbsolutePath();
			int separatorIndex = Math.max(path.lastIndexOf(File.separatorChar), path.lastIndexOf('/'));
			int dotIndex = path.lastIndexOf('.');
			String stem = dotIndex > separatorIndex ? path.substring(0, dotIndex) : path;
			String sidecarExtension = path.toLowerCase(Locale.ROOT).endsWith(".ttl") ? ".ttl" : ".json";
			for (String suffix : List.of("_building_elements", "_element_properties")) {
				File sidecar = new File(stem + suffix + sidecarExtension);
				if (sidecar.isFile()) {
					Lang language = RDFLanguages.filenameToLang(sidecar.getName(), Lang.TURTLE);
					RDFDataMgr.read(model, sidecar.getAbsolutePath(), language);
				}
			}
			return model;
		} catch (RuntimeException e) {
			model.close();
			throw e;
		}
	}

	private static Resource geometryOwner(Model model, Resource geometry) {
		Property hasGeometry = model.createProperty(OMG_HAS_GEOMETRY);
		var owners = model.listStatements(null, hasGeometry, geometry);
		try {
			return owners.hasNext() ? owners.nextStatement().getSubject() : geometry;
		} finally {
			owners.close();
		}
	}

	private static ResourceDetails describeResource(Model model, Resource resource) {
		String uri = resource.isURIResource() ? resource.getURI() : resource.toString();
		Set<String> typeNames = new HashSet<>();
		List<String> displayTypes = new ArrayList<>();
		model.listStatements(resource, org.apache.jena.vocabulary.RDF.type, (RDFNode) null).forEachRemaining(statement -> {
			if (!statement.getObject().isResource()) return;
			Resource type = statement.getResource();
			String name = type.getLocalName() == null ? compactResourceName(type.toString()) : type.getLocalName();
			typeNames.add(name);
			if (!BOT_ELEMENT.equals(type.getURI())) displayTypes.add(name);
		});
		if (displayTypes.isEmpty()) displayTypes.addAll(typeNames);
		displayTypes.sort(String::compareToIgnoreCase);
		String ifcType = displayTypes.isEmpty() ? "Unknown" : String.join(", ", displayTypes.stream().limit(3).toList());
		String guid = guidForResource(model, resource, uri);
		List<String> properties = new ArrayList<>();
		model.listStatements(resource, null, (RDFNode) null).forEachRemaining(statement -> {
			String predicate = statement.getPredicate().getURI();
			if (org.apache.jena.vocabulary.RDF.type.getURI().equals(predicate) || OMG_HAS_GEOMETRY.equals(predicate)
					|| GEOMETRY_OBJ_PROPERTY.equals(predicate) || predicate.startsWith("https://lbd.org/#asMTL")) return;
			if (properties.size() >= 20) return;
			String name = statement.getPredicate().getLocalName();
			properties.add((name == null ? predicate : name) + " = "
					+ summarizeRdfValue(model, statement.getObject()));
		});
		return new ResourceDetails(uri, ifcType, Set.copyOf(typeNames), guid, List.copyOf(properties));
	}

	private static String guidForResource(Model model, Resource resource, String uri) {
		Matcher uriGuid = GUID_PATTERN.matcher(uri);
		if (uriGuid.find()) return uriGuid.group(1);
		var statements = model.listStatements(resource, null, (RDFNode) null);
		while (statements.hasNext()) {
			Statement statement = statements.next();
			String name = statement.getPredicate().getLocalName();
			if (name != null && name.toLowerCase(Locale.ROOT).contains("globalid")) {
				String value = firstLiteralValue(model, statement.getObject(), 3, new HashSet<>());
				if (value != null) return value;
			}
		}
		return null;
	}

	private static String summarizeRdfValue(Model model, RDFNode value) {
		if (value.isLiteral()) return abbreviate(value.asLiteral().getString(), 160);
		String resolved = firstLiteralValue(model, value, 2, new HashSet<>());
		if (resolved != null) return abbreviate(resolved, 160);
		return value.isResource() && value.asResource().isURIResource()
				? compactResourceName(value.asResource().getURI()) : value.toString();
	}

	private static String firstLiteralValue(Model model, RDFNode node, int depth, Set<Resource> visited) {
		if (node == null) return null;
		if (node.isLiteral()) return node.asLiteral().getString();
		if (!node.isResource() || depth <= 0 || !visited.add(node.asResource())) return null;
		var statements = model.listStatements(node.asResource(), null, (RDFNode) null);
		while (statements.hasNext()) {
			Statement statement = statements.next();
			String local = statement.getPredicate().getLocalName();
			if (statement.getObject().isLiteral() && local != null
					&& (local.equalsIgnoreCase("value") || local.toLowerCase(Locale.ROOT).contains("value"))) {
				return statement.getString();
			}
		}
		statements = model.listStatements(node.asResource(), null, (RDFNode) null);
		while (statements.hasNext()) {
			String value = firstLiteralValue(model, statements.next().getObject(), depth - 1, visited);
			if (value != null) return value;
		}
		return null;
	}

	private static String abbreviate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) return value;
		return value.substring(0, maxLength - 1) + "…";
	}

	private static int geometryColor(Resource geometry, Property materialColorProperty, Property materialProperty) {
		Statement color = geometry.getProperty(materialColorProperty);
		if (color != null && color.getObject().isLiteral()) {
			return parseHexColor(color.getString());
		}
		Statement material = geometry.getProperty(materialProperty);
		if (material != null && material.getObject().isLiteral()) {
			return parseMtlDiffuseColor(material.getString(), DEFAULT_PREVIEW_COLOR);
		}
		return DEFAULT_PREVIEW_COLOR;
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
		if (this.geometryEmptyStateLabel != null) {
			this.geometryEmptyStateLabel.setVisible(false);
			this.geometryEmptyStateLabel.setManaged(false);
		}
		if (this.geometryFitButton != null) {
			this.geometryFitButton.setVisible(true);
			this.geometryFitButton.setManaged(true);
		}
		this.geometryExplodeSlider.setValue(0);
		fitGeometryPreview();
		renderGeometryScene();
		this.geometryExplodeSlider.setDisable(false);
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
		this.geometryViewsByResource.clear();
		this.geometryExplosionVectors.clear();
		this.geometryBoundsByResource.clear();
		this.geometryMaterials.clear();
		if (this.currentPreviewMesh == null || this.currentPreviewMesh.triangleCount() == 0) {
			return;
		}

		float[] points = this.currentPreviewMesh.points();
		int[] faces = this.currentPreviewMesh.faces();
		int[] triangleColors = this.currentPreviewMesh.triangleColors();
		String[] triangleResources = this.currentPreviewMesh.triangleResourceUris();
		Map<GeometryRenderKey, List<Integer>> facesByResource = new LinkedHashMap<>();
		GeometryBounds modelBounds = new GeometryBounds();
		for (int faceOffset = 0; faceOffset + 5 < faces.length; faceOffset += 6) {
			int colorIndex = faceOffset / 6;
			int color = colorIndex < triangleColors.length ? triangleColors[colorIndex] : DEFAULT_PREVIEW_COLOR;
			String resourceUri = colorIndex < triangleResources.length ? triangleResources[colorIndex] : null;
			facesByResource.computeIfAbsent(new GeometryRenderKey(resourceUri, color), ignored -> new ArrayList<>())
					.add(faceOffset);
			GeometryBounds resourceBounds = this.geometryBoundsByResource.computeIfAbsent(resourceUri,
					ignored -> new GeometryBounds());
			for (int vertexOffset = 0; vertexOffset < 6; vertexOffset += 2) {
				int pointOffset = faces[faceOffset + vertexOffset] * 3;
				if (pointOffset >= 0 && pointOffset + 2 < points.length) {
					resourceBounds.include(points[pointOffset], points[pointOffset + 1], points[pointOffset + 2]);
					modelBounds.include(points[pointOffset], points[pointOffset + 1], points[pointOffset + 2]);
				}
			}
		}
		for (Map.Entry<String, GeometryBounds> entry : this.geometryBoundsByResource.entrySet()) {
			this.geometryExplosionVectors.put(entry.getKey(), explosionVector(modelBounds, entry.getValue(), entry.getKey()));
		}

		for (Map.Entry<GeometryRenderKey, List<Integer>> entry : facesByResource.entrySet()) {
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
			PhongMaterial material = createPreviewMaterial(entry.getKey().color());
			meshView.setMaterial(material);
			meshView.setUserData(entry.getKey().resourceUri());
			meshView.setOnMouseClicked(event -> {
				if (event.getButton() == MouseButton.PRIMARY && entry.getKey().resourceUri() != null) {
					focusResource(entry.getKey().resourceUri(), null, "3D selection");
					event.consume();
				}
			});
			this.geometryMaterials.put(meshView, material);
			this.geometryViewsByResource.computeIfAbsent(entry.getKey().resourceUri(), ignored -> new ArrayList<>())
					.add(meshView);
			this.geometryModelRoot.getChildren().add(meshView);
		}
		applyGeometryExplosion();
		applyGeometrySelection();
	}

	private void applyGeometryExplosion() {
		double amount = this.geometryExplodeSlider.getValue() / this.geometryExplodeSlider.getMax();
		for (Map.Entry<String, List<MeshView>> entry : this.geometryViewsByResource.entrySet()) {
			Point3D displacement = this.geometryExplosionVectors.get(entry.getKey());
			if (displacement == null) {
				continue;
			}
			for (MeshView view : entry.getValue()) {
				view.setTranslateX(displacement.getX() * amount);
				view.setTranslateY(displacement.getY() * amount);
				view.setTranslateZ(displacement.getZ() * amount);
			}
		}
	}

	static Point3D explosionVector(GeometryBounds model, GeometryBounds element, String resourceUri) {
		Point3D direction = element.center().subtract(model.center());
		if (direction.magnitude() < 0.000001) {
			int hash = Objects.hashCode(resourceUri);
			double angle = (Integer.toUnsignedLong(hash) / (double) (1L << 32)) * Math.PI * 2;
			direction = new Point3D(Math.cos(angle), Math.sin(angle), 0.35);
		}
		return direction.normalize().multiply(model.maxSpan() * 0.18);
	}

	static final class GeometryBounds {
		private double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		private double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

		void include(double x, double y, double z) {
			minX = Math.min(minX, x);
			minY = Math.min(minY, y);
			minZ = Math.min(minZ, z);
			maxX = Math.max(maxX, x);
			maxY = Math.max(maxY, y);
			maxZ = Math.max(maxZ, z);
		}

		Point3D center() {
			return new Point3D((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
		}

		double maxSpan() {
			return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
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

	private record PreviewMesh(float[] points, int[] faces, int[] triangleColors, String[] triangleResourceUris,
			Map<String, ResourceDetails> resources, Map<String, String> resourceAliases, int objectCount,
			int triangleCount, boolean clipped) {
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
		private final List<String> triangleResourceUris = new ArrayList<>();
		private final Map<String, ResourceDetails> resources = new LinkedHashMap<>();
		private final Map<String, String> resourceAliases = new LinkedHashMap<>();
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

		private void addObj(String obj, int color, ResourceDetails details, String geometryUri) {
			if (this.clipped) {
				return;
			}
			this.objectCount++;
			this.resources.putIfAbsent(details.uri(), details);
			if (geometryUri != null) this.resourceAliases.put(geometryUri, details.uri());
			List<double[]> localVertices = new ArrayList<>();
			for (String rawLine : obj.split("\\R")) {
				String line = rawLine.strip();
				if (line.startsWith("v ")) {
					addLocalVertex(line, localVertices);
				} else if (line.startsWith("f ")) {
					addFace(line, localVertices, color, details.uri());
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

		private void addFace(String line, List<double[]> localVertices, int color, String resourceUri) {
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
				this.triangleResourceUris.add(resourceUri);
			}
		}

		private PreviewMesh toPreviewMesh() {
			if (this.rawPoints.isEmpty() || this.triangles.isEmpty()) {
				return new PreviewMesh(new float[0], new int[0], new int[0], new String[0], Map.copyOf(this.resources),
						Map.copyOf(this.resourceAliases), this.objectCount, 0, this.clipped);
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
			return new PreviewMesh(points, faces, colors, this.triangleResourceUris.toArray(String[]::new),
					Map.copyOf(this.resources), Map.copyOf(this.resourceAliases), this.objectCount,
					this.triangles.size(), this.clipped);
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
			this.runAfterReadIn = true;
			this.conversionTxt.appendText("Re-reading the model to apply changed settings.\n");
			readInIFCExecute(currentSettings);
			this.conversionTxt.appendText("Conversion will start automatically when the model is ready.\n");
			return;
		}

		persistSettings(currentSettings);
			this.conversionTxt.setText("");
			try {
				if (this.running_conversion != null && !this.running_conversion.isDone()) {
					this.conversionTxt.appendText("\nThe last conversion is still running. \n");
					return;
				}
				setWorkflowDataAvailable(isFiltersAvailable(), false, false);
				this.options_panel.setDisable(true);

			Set<String> selected_types = selectedElementTypes();
			Set<String> selected_psets = selectedPropertySets();
					IFCtoLBDConverter converter = this.running_read_in.get();
					if (converter == null) {
						this.conversionTxt.appendText("Read-in failed. Conversion cannot continue.\n");
						this.options_panel.setDisable(false);
						return;
					}
				long operationId = beginWorkflow("Starting conversion", 0.0);
				converter.setTargetFile(currentSettings.rdfTargetName());
				converter.setHasSimplified_properties(currentSettings.hasSimpleProperties());
				converter.setPropertiesAsPropertySets(currentSettings.propertiesAsPropertySets());
				ConversionRequest conversionRequest = new ConversionRequest(currentSettings, selected_types, selected_psets);
				this.pendingConversionRequest = conversionRequest;
				if (!currentSettings.hasSeparateBuildingElementsModel()
						&& !hasConversionRequestChanged(this.lastSuccessfulConversionRequest, conversionRequest)) {
					this.running_conversion = this.executor.submit(() -> {
						try {
							this.eventBus.post(new WorkflowProgressEvent(operationId, "Writing RDF output", 0.82));
							if (currentSettings.exportAsIcdd()) {
								converter.exportExistingOutputAsIcdd(currentSettings.rdfTargetName(),
										currentSettings.ifcFileName());
							} else {
								converter.exportExistingOutput(currentSettings.rdfTargetName(),
										currentSettings.hasSeparatePropertiesModel(), currentSettings.createTrig(),
										currentSettings.exportAsJsonLd());
							}
							this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.CONVERT, operationId));
						} catch (Exception e) {
							this.eventBus.post(new IFCtoLBD_SystemStatusEvent(e.getMessage()));
							this.eventBus.post(new ProcessReadyEvent(ProcessReadyEvent.ERROR, operationId));
						}
						return 0;
					});
					return;
				}
			this.running_conversion = this.executor.submit(new ConversionThread(operationId, converter, selected_types, selected_psets,
					currentSettings.ifcFileName(), currentSettings.baseUri(), currentSettings.rdfTargetName(),
					currentSettings.propsLevel(), currentSettings.hasBuildingElements(),
					currentSettings.hasSeparateBuildingElementsModel(), currentSettings.hasBuildingProperties(),
					currentSettings.hasSeparatePropertiesModel(), currentSettings.hasPropertiesBlankNodes(),
					currentSettings.hasGeolocation(), currentSettings.hasGeometry(), currentSettings.exportIfcOwl(),
					currentSettings.hasUnits(), currentSettings.hasPerformanceBoost(),
					currentSettings.hasBoundingBoxWkt(), currentSettings.hasHierarchicalNaming(),
					currentSettings.hasIfcBasedElements(), currentSettings.hasInterfaces(), currentSettings.createTrig(),
					currentSettings.exportAsJsonLd(), currentSettings.exportAsIcdd(),
					currentSettings.hasElementWireframe(),
					currentSettings.propertiesAsPropertySets(), currentSettings.hasMaterialsBmo()));
		} catch (Exception e) {
			long operationId = this.activeOperationId;
			if (isActiveOperation(operationId)) {
				finishWorkflow(operationId, "Conversion failed", false);
			}
			this.options_panel.setDisable(false);
			Platform.runLater(() -> this.conversionTxt.appendText(e.getMessage()));
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.eventBus.register(this);
		this.border.widthProperty().bind(this.root.widthProperty());
		this.border.heightProperty().bind(this.root.heightProperty());
		setupCommandLinePreview();
		setupGeometryPreview();
		setupSparqlQueryWindow();
		setupValidateWindow();
		setupFloatingCards();
		this.workflowTabs.getSelectionModel().select(0);
		this.modelSummaryCard.expandedProperty().addListener((observable, wasExpanded, isExpanded) ->
				updateModelSummaryTitle(isExpanded));
		setWorkflowStatusVisible(false);
		setWorkflowDataAvailable(false, false, false);
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
							IFCtoLBDController.this.setIfcFileLabel(file.getName());
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
								IFCtoLBDController.this.setTargetFileLabel(IFCtoLBDController.this.rdfTargetName);
							}
							if (IFCtoLBDController.this.ifcFileName != null && IFCtoLBDController.this.rdfTargetName != null) {
								IFCtoLBDController.this.selectTargetFileButton.setDisable(false);
								IFCtoLBDController.this.setSelectIfcDefault(false);
								IFCtoLBDController.this.prefs.put("ifc_work_directory", file.getParentFile().getAbsolutePath());
								IFCtoLBDController.this.readInIFC();
							}
					}
				}
				event.setDropCompleted(success);
				event.consume();
			}
		};

		setDropHandlers(this.selectIFCFileButton, ad_conversion, dh_conversion);
		setDropHandlers(this.basicSelectIFCFileButton, ad_conversion, dh_conversion);
		setDropHandlers(this.convert2RDFButton, ad_conversion, dh_conversion);
		setDropHandlers(this.basicConvert2RDFButton, ad_conversion, dh_conversion);
		setDropHandlers(this.labelIFCFile, ad_conversion, dh_conversion);
		setDropHandlers(this.basicLabelIFCFile, ad_conversion, dh_conversion);
		setDropHandlers(this.conversionTxt, ad_conversion, dh_conversion);

		this.labelBaseURI
				.setText(this.prefs.get("lbd_props_base_url", "https://www.ugent.be/myAwesomeFirstBIMProject#"));
		this.building_elements.setSelected(this.prefs.getBoolean("lbd_building_elements", true));
		this.materialsBmo.setSelected(this.prefs.getBoolean("lbd_materials_bmo", false));
		this.building_elements_separate_file
				.setSelected(this.prefs.getBoolean("lbd_building_elements_separate_file", false));
		this.building_props.setSelected(this.prefs.getBoolean("lbd_building_props", true));
		this.building_props_blank_nodes.setSelected(this.prefs.getBoolean("lbd_building_props_blank_nodes", false));
		this.building_props_separate_file.setSelected(this.prefs.getBoolean("lbd_building_props_separate_file", false));

		this.geometry_elements.setSelected(this.prefs.getBoolean("lbd_boundinbox_elements", true));
		this.geometry_interfaces.setSelected(this.prefs.getBoolean("lbd_boundinbox_interfaces", false));
		this.hasBoundingBox_WKT.setSelected(this.prefs.getBoolean("lbd_boundinbox_wkt", false));
		this.hasElementWireframe.setSelected(this.prefs.getBoolean("lbd_element_wireframe", false));
		this.ifcOWL_elements.setSelected(this.prefs.getBoolean("lbd_ifcOWL_elements", false));
		this.createUnits.setSelected(this.prefs.getBoolean("lbd_createUnits", false));
		this.geolocation.setSelected(this.prefs.getBoolean("lbd_geolocation", true));
		
		this.hasHierarchicalNaming.setSelected(this.prefs.getBoolean("lbd_hasHierarchicalNaming", false));
		this.hasSimpleProperties.setSelected(this.prefs.getBoolean("lbd_hasSimpleProperties", false));
		this.propertiesAsPropertySets.setSelected(this.prefs.getBoolean("lbd_propertiesAsPropertySets", false));
		this.propertiesAsPropertySets.setTooltip(new Tooltip(
				"Export bSDD-typed property-set resources containing OPM properties and current value states."));

		this.ifc_based_elements.setSelected(this.prefs.getBoolean("ifc_based_elements", false));
		this.createTrig.setSelected(this.prefs.getBoolean("createTrig", false));
		
		this.hasPerformanceBoost.setSelected(this.prefs.getBoolean("lbd_performance", true));
		if (this.ifcOWL_elements.isSelected() || this.materialsBmo.isSelected()) {
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

		
		
		setupTargetFileEditor();
		setupOutputFormatChoices();
		setupPresets();
        
        
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

		String selectIfcTooltip = "Select an IFC Step formatted file to convert.\nThe supported IFC versions are\n2x3 TC1 & Final, 4 ADD1, 4 ADD2, 4  ";
		this.selectIFCFileButton.setTooltip(new Tooltip(selectIfcTooltip));
		this.basicSelectIFCFileButton.setTooltip(new Tooltip(selectIfcTooltip));
		this.selectTargetFileButton.setTooltip(new Tooltip(
				"Select an target file for the conversion.\nIf there will be many files, the separate files are named accorrdingly."));
		String runTooltip = "Press this button to start the conversion process.";
		this.convert2RDFButton.setTooltip(new Tooltip(runTooltip));
		this.basicConvert2RDFButton.setTooltip(new Tooltip(runTooltip));
		this.conversionTxt.setTooltip(
				new Tooltip("This shows the conversion process related messages\nand any error that occurs. "));

		String ifcFileTooltip = "The selected IFC file. ";
		this.labelIFCFile.setTooltip(new Tooltip(ifcFileTooltip));
		this.basicLabelIFCFile.setTooltip(new Tooltip(ifcFileTooltip));

		this.labelTargetFile.setTooltip(new Tooltip("The selected target RDF file. "));

		this.labelBaseURI
				.setTooltip(new Tooltip("The base URL is the consistent part of your links generated in the output. "));

		this.hasPerformanceBoost.setTooltip(
				new Tooltip("When used, the memory consumption is saved by removing the ifcOWL geometry. "));

		this.geometry_interfaces.setTooltip(
				new Tooltip("Creates bounding box based BOT interfaces. "));

		this.hasElementWireframe.setTooltip(
				new Tooltip("Exports simple mesh wireframes as lbd:hasWireframe WKT literals. "));
		
		this.createTrig.setTooltip(
				new Tooltip("If selected, creates also a RDF 1.1. TriG file (https://www.w3.org/TR/trig/). "));

		setupReactiveSettings();
	}

	@Override
	public void handle_notification(String txt) {
		this.conversionTxt.insertText(0, txt + "\n");
	}

	public void shutdown() {
		if (this.workflowElapsedTimeline != null) {
			this.workflowElapsedTimeline.stop();
		}
		if (this.running_read_in != null && !this.running_read_in.isDone()) {
			this.running_read_in.cancel(true);
		}
		if (this.running_conversion != null && !this.running_conversion.isDone()) {
			this.running_conversion.cancel(true);
		}
		if (this.running_validation != null && !this.running_validation.isDone()) {
			this.running_validation.cancel(true);
		}
		this.executor.shutdownNow();
		this.explorationModelCache.close();
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
	public void handleEvent(final WorkflowProgressEvent event) {
		if (!isActiveOperation(event.operationId())) {
			return;
		}
		Platform.runLater(() -> updateWorkflowStage(event.operationId(), event.stage(), event.progress()));
	}

	@Subscribe
	public void handleEvent(final ProcessReadyEvent event) {
		long operationId = event.getOperationId();
		if (!isActiveOperation(operationId)) {
			return;
		}
		Platform.runLater(() -> {
			if (isActiveOperation(operationId)) {
				this.options_panel.setDisable(false);
			}
		});

		if (event.getPhase() == ProcessReadyEvent.READ_IN) {
			Platform.runLater(() -> {
				if (!isActiveOperation(operationId)) {
					return;
				}
				// prepare tree items
				try {
					IFCtoLBDConverter converter = this.running_read_in.get();
					Set<Resource> element_types = converter.getElementTypes();
					Map<String, Integer> element_type_counts = converter.getElementTypeCounts();
					CheckBoxTreeItem<String> types_checkbox_values = new CheckBoxTreeItem<>("Model");

					types_checkbox_values.getChildren().clear();
					// add items to the root
					for (Resource et : element_types) {
						String typeName = et.getLocalName();
						CheckBoxTreeItem<String> item = new CheckBoxTreeItem<>(typeName + " (" + element_type_counts.getOrDefault(typeName, 0) + ")");
						item.setSelected(true);
						types_checkbox_values.getChildren().add(item);

					}
					types_checkbox_values.setExpanded(true);
					
					Set<String> pset_names = converter.getPropertySetNames();
					ModelSummary modelSummary = null;
					try {
						modelSummary = createModelSummary(converter, element_type_counts, pset_names);
					} catch (RuntimeException summaryError) {
						this.conversionTxt.appendText("Model summary unavailable: " + summaryError.getMessage() + "\n");
					}

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
					if (modelSummary != null) {
						showModelSummary(modelSummary);
					}
					setWorkflowDataAvailable(true, false, false);
					setRunReady(true);
					finishWorkflow(operationId, "IFC model ready", true);
					refreshReactiveSettings();
					if (this.runAfterReadIn) {
						this.runAfterReadIn = false;
						Platform.runLater(this::convertIFCToRDF);
					}
				} catch (InterruptedException e) {
					this.runAfterReadIn = false;
					finishWorkflow(operationId, "Read-in interrupted", false);
					e.printStackTrace();
				} catch (ExecutionException e) {
					this.runAfterReadIn = false;
					setWorkflowDataAvailable(false, false, false);
					setRunReady(false);
					finishWorkflow(operationId, "Read-in failed", false);
					e.printStackTrace();
				} catch (RuntimeException e) {
					this.runAfterReadIn = false;
					resetModelSummary();
					setWorkflowDataAvailable(false, false, false);
					setRunReady(false);
					finishWorkflow(operationId, "Could not summarize IFC model", false);
					this.conversionTxt.appendText("Model summary failed: " + e.getMessage() + "\n");
				}
			});
		}
		if (event.getPhase() == ProcessReadyEvent.CONVERT) {
			Platform.runLater(() -> {
				if (!isActiveOperation(operationId)) {
					return;
				}
				ConversionRequest successfulRequest = this.pendingConversionRequest;
				this.lastSuccessfulConversionRequest = successfulRequest;
				this.explorationModelCache.invalidate();
				this.pendingConversionRequest = null;
				if (successfulRequest != null) {
					if (successfulRequest.settings().exportAsIcdd()) {
						clearGeometryPreview("Geometry preview is unavailable for packaged ICDD output.");
					} else {
						scheduleGeometryPreview(successfulRequest.settings());
					}
					String outputPath = new File(successfulRequest.settings().rdfTargetName()).getAbsolutePath();
					this.conversionTxt.appendText((successfulRequest.settings().exportAsIcdd()
							? "ICDD package written to: " : "LBD file written to: ") + outputPath + "\n");
					boolean queryAvailable = !successfulRequest.settings().exportAsIcdd()
							&& new File(successfulRequest.settings().rdfTargetName()).isFile();
					setWorkflowDataAvailable(isFiltersAvailable(),
							!successfulRequest.settings().exportAsIcdd(), queryAvailable);
					refreshReactiveSettings();
					if (queryAvailable) {
						showSparqlMessage("Ready. Run a SPARQL query against "
								+ new File(successfulRequest.settings().rdfTargetName()).getName() + ".");
						validateLoadedShapesAsync(operationId);
					} else {
						finishWorkflow(operationId, "Conversion complete", true);
					}
				} else {
					finishWorkflow(operationId, "Conversion failed", false);
				}
			});
		}
		if (event.getPhase() == ProcessReadyEvent.ERROR) {
			Platform.runLater(() -> {
				if (!isActiveOperation(operationId)) {
					return;
				}
				this.pendingConversionRequest = null;
				this.runAfterReadIn = false;
				this.lastSuccessfulConversionRequest = null;
				finishWorkflow(operationId, "Operation failed", false);
				setRunReady(false);
				setWorkflowDataAvailable(isFiltersAvailable(), false, false);
				clearGeometryPreview("Geometry preview unavailable because conversion failed.");
			});
		}
	}
}
