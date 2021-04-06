package de.rwth_aachen.dc.bcf;

import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.ObjectProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;

public class CreateBCFOntology {
	private OntModel ontology_model = ModelFactory.createOntologyModel();
	String bcf_ns = "http://lbd.arch.rwth-aachen.de/bcf/v2.1/";

	public CreateBCFOntology() {
		try {
			
			
			OntClass oc_Project  = ontology_model.createClass(this.bcf_ns + "Project");
			oc_Project.addComment("The project contains reference information about the project the topics belong to.", "en");
			
			DatatypeProperty dp_hasProjectId = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "ProjectId");
			dp_hasProjectId.addComment("ProjectId of the project", "en");
			dp_hasProjectId.addDomain(oc_Project);
			dp_hasProjectId.addRange(XSD.xstring);
			setOptionalAttribute(oc_Project, dp_hasProjectId);
			
			DatatypeProperty dp_hasName = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "Name");
			dp_hasName.addComment("Name of the project.", "en");
			dp_hasName.addDomain(oc_Project);
			dp_hasName.addRange(XSD.xstring);
			setOptionalElement(oc_Project, dp_hasName);

			ObjectProperty op_hasExtensionSchema = ontology_model.createObjectProperty(this.bcf_ns + "has" + "ExtensionSchema");
			op_hasExtensionSchema.addComment("URI to the extension schema.", "en");
			op_hasExtensionSchema.addDomain(oc_Project);
			setObligatoryElement(oc_Project, op_hasExtensionSchema);
			
			createMarkup();
			createVisualizationInfo();
			
			
			ontology_model.write(System.out);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void createVisualizationInfo() {
		OntClass oc_VisualizationInfo = ontology_model.createClass(this.bcf_ns + "VisualizationInfo");		
		
		
		OntClass oc_Components = ontology_model.createClass(this.bcf_ns + "Components");
		oc_Components.addComment("The components node contains a set of Component references. The numeric values in this file are all given in fixed units (meters for length and degrees for angle). Unit conversion is not required, since the values are not relevant to the user. The components node has also the DefaultVisibility attribute which indicates true or false for all components of the viewpoint.", "en");
		ObjectProperty op_hasComponents = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Components");
		op_hasComponents.addDomain(oc_VisualizationInfo);
		op_hasComponents.addRange(oc_Components);	
		addComponents(oc_Components);
		
		

		OntClass oc_Camera = ontology_model.createClass(this.bcf_ns + "Camera");
		ObjectProperty op_CameraViewPoint = ontology_model.createObjectProperty(this.bcf_ns + "has" + "CameraViewPoint");
		op_CameraViewPoint.addComment("	Camera location", "en");
		op_CameraViewPoint.addDomain(oc_Camera);
		setObligatoryElement(oc_Camera, op_CameraViewPoint);
		
		ObjectProperty op_CameraDirection = ontology_model.createObjectProperty(this.bcf_ns + "has" + "CameraDirection");
		op_CameraDirection.addComment("Camera direction", "en");
		op_CameraDirection.addDomain(oc_Camera);
		setObligatoryElement(oc_Camera, op_CameraDirection);
		
		ObjectProperty op_CameraUpVector= ontology_model.createObjectProperty(this.bcf_ns + "has" + "CameraUpVector");
		op_CameraUpVector.addComment("Camera up vector", "en");
		op_CameraUpVector.addDomain(oc_Camera);
		setObligatoryElement(oc_Camera, op_CameraUpVector);
		
		OntClass oc_OrthogonalCamera = ontology_model.createClass(this.bcf_ns + "OrthogonalCamera");
		oc_OrthogonalCamera.addComment("This element describes a viewpoint using orthogonal camera. ", "en");
		oc_Camera.addSubClass(oc_OrthogonalCamera);
		oc_OrthogonalCamera.addSuperClass(oc_Camera);		
		ObjectProperty op_hasOrthogonalCamera = ontology_model.createObjectProperty(this.bcf_ns + "has" + "OrthogonalCamera");
		op_hasOrthogonalCamera.addDomain(oc_VisualizationInfo);
		op_hasOrthogonalCamera.addRange(oc_OrthogonalCamera);
		ObjectProperty op_ViewToWorldScale= ontology_model.createObjectProperty(this.bcf_ns + "has" + "ViewToWorldScale");
		op_ViewToWorldScale.addComment("Scaling from view to world", "en");
		op_ViewToWorldScale.addDomain(oc_OrthogonalCamera);
		setOptionalElement(oc_VisualizationInfo, op_hasOrthogonalCamera);
		setObligatoryElement(oc_OrthogonalCamera, op_ViewToWorldScale);


		OntClass oc_PerspectiveCamera = ontology_model.createClass(this.bcf_ns + "PerspectiveCamera");
		oc_PerspectiveCamera.addComment("This element describes a viewpoint using perspective camera.", "en");
		oc_Camera.addSubClass(oc_PerspectiveCamera);
		oc_PerspectiveCamera.addSuperClass(oc_Camera);		
		ObjectProperty op_hasPerspectiveCamera = ontology_model.createObjectProperty(this.bcf_ns + "has" + "PerspectiveCamera");
		op_hasPerspectiveCamera.addDomain(oc_VisualizationInfo);
		op_hasPerspectiveCamera.addRange(oc_PerspectiveCamera);
		ObjectProperty op_FieldOfView= ontology_model.createObjectProperty(this.bcf_ns + "has" + "FieldOfView");
		op_FieldOfView.addComment("Camera’s field of view angle in degrees.", "en");
		op_FieldOfView.addDomain(oc_PerspectiveCamera);
		setOptionalElement(oc_VisualizationInfo, op_hasPerspectiveCamera);
		setObligatoryElement(oc_PerspectiveCamera, op_FieldOfView);

		

		OntClass oc_Lines  = ontology_model.createClass(this.bcf_ns + "Lines");
		oc_Lines.addComment("Lines can be used to add markup in 3D. Each line is defined by three dimensional Start Point and End Point. Lines that have the same start and end points are to be considered points and may be displayed accordingly.", "en");
		ObjectProperty op_hasLines = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Lines");
		op_hasLines.addDomain(oc_VisualizationInfo);
		op_hasLines.addRange(oc_Lines);
		
		OntClass oc_ClippingPlanes = ontology_model.createClass(this.bcf_ns + "ClippingPlanes");
		oc_ClippingPlanes.addComment("ClippingPlanes can be used to define a subsection of a building model that is related to the topic. Each clipping plane is defined by Location and Direction.", "en");
		ObjectProperty op_hasClippingPlanes = ontology_model.createObjectProperty(this.bcf_ns + "has" + "ClippingPlanes");
		op_hasClippingPlanes.addDomain(oc_VisualizationInfo);
		op_hasClippingPlanes.addRange(oc_ClippingPlanes);
		
		OntClass oc_Bitmap = ontology_model.createClass(this.bcf_ns + "Bitmap");
		oc_Bitmap.addComment("A list of bitmaps can be used to add more information, for example, text in the visualization.", "en");
		ObjectProperty op_hasBitmap = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Bitmap");
		op_hasBitmap.addDomain(oc_VisualizationInfo);
		op_hasBitmap.addRange(oc_Bitmap);
	}

	private void addComponents(OntClass oc_Components) {
		OntClass oc_ViewSetupHints = ontology_model.createClass(this.bcf_ns + "ViewSetupHints");
		oc_ViewSetupHints.addComment("This element contains information about the default visibility for elements of certain types (SpacesVisible, SpaceBoundariesVisible and OpeningsVisible) that should be applied if not stated otherwise.", "en");
		ObjectProperty op_hasViewSetupHints = ontology_model.createObjectProperty(this.bcf_ns + "has" + "ViewSetupHints");
		op_hasViewSetupHints.addDomain(oc_Components);
		op_hasViewSetupHints.addRange(oc_ViewSetupHints);
		
		OntClass oc_Selection = ontology_model.createClass(this.bcf_ns + "Selection");
		oc_Selection.addComment("The Selection element lists all components that should be either highlighted or selected when displaying a viewpoint.", "en");
		ObjectProperty op_hasSelection = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Selection");
		op_hasSelection.addDomain(oc_Components);
		op_hasSelection.addRange(oc_Selection);
		
		OntClass oc_Component = ontology_model.createClass(this.bcf_ns + "Component");
		ObjectProperty op_hasComponent = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Component");
		op_hasComponent.addDomain(oc_Selection);
		op_hasComponent.addRange(oc_Component);		
		addComponent(oc_Component);

		ObjectProperty op_AuthoringToolId = ontology_model.createObjectProperty(this.bcf_ns + "has" + "AuthoringToolId");
		op_AuthoringToolId.addDomain(oc_Component);

		
		OntClass oc_Visibility = ontology_model.createClass(this.bcf_ns + "Visibility");
		oc_Visibility.addComment("The Visibility element states the components DefaultVisibility and lists all Exceptions that apply to specific components.", "en");
		ObjectProperty op_hasVisibility = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Visibility");
		op_hasVisibility.addDomain(oc_Components);
		op_hasVisibility.addRange(oc_Visibility);
		
		OntClass oc_Coloring = ontology_model.createClass(this.bcf_ns + "Coloring");
		oc_Coloring.addComment("The Coloring element lists colors and a list of associated components that should be displayed with the specified color when displaying a viewpoint. The color is given in ARGB format. Colors are represented as 6 or 8 hexadecimal digits. If 8 digits are present, the first two represent the alpha (transparency) channel. For example, 40E0D0 would be the color <span style=\"color:#40E0D0;\";>Turquoise. More information about the color format can be found on Wikipedia.", "en");
		ObjectProperty op_hasColoring = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Coloring");
		op_hasColoring.addDomain(oc_Components);
		op_hasColoring.addRange(oc_Coloring);
	}

	private void addComponent(OntClass oc_Component) {
		ObjectProperty op_IfcGuid = ontology_model.createObjectProperty(this.bcf_ns + "has" + "IfcGuid");
		op_IfcGuid.addComment("The IfcGuid of the component", "en");
		op_IfcGuid.addDomain(oc_Component);

		ObjectProperty op_OriginatingSystem = ontology_model.createObjectProperty(this.bcf_ns + "has" + "OriginatingSystem");
		op_OriginatingSystem.addComment("Name of the system in which the component is originated", "en");
		op_OriginatingSystem.addDomain(oc_Component);
		
		ObjectProperty op_AuthoringToolId = ontology_model.createObjectProperty(this.bcf_ns + "has" + "AuthoringToolId");
		op_AuthoringToolId.addComment("Name of the system in which the component is originated", "en");
		op_AuthoringToolId.addDomain(oc_Component);

	}

	private void createMarkup() {
		OntClass oc_Markup = ontology_model.createClass(this.bcf_ns + "Markup");
		oc_Markup.addComment("The markup contains textual information about the topic.", "en");

		OntClass oc_Header = ontology_model.createClass(this.bcf_ns + "Header");
		oc_Header.addComment(
				"Header node contains information about the IFC files relevant to this topic. It has one attribute, ProjectGuid, which is the project GUID. Header has also a list of File nodes.",
				"en");
		OntClass oc_Topic = ontology_model.createClass(this.bcf_ns + "Topic");
		oc_Topic.addComment(
				"Topic node contains reference information of the topic. It has one attribute, Guid, which is the topic GUID.",
				"en");
		OntClass oc_Comment = ontology_model.createClass(this.bcf_ns + "Comment");
		oc_Comment.addComment(
				"The markup file can contain comments related to the topic. Their purpose is to record discussion between different parties related to the topic. Comment has also the Guid attribute for identifying it uniquely.",
				"en");
		OntClass oc_Viewpoints = ontology_model.createClass(this.bcf_ns + "Viewpoints");
		oc_Viewpoints.addComment(
				"The markup file can contain multiple viewpoints related to one or more comments. A viewpoint has also the Guid attribute for identifying it uniquely.",
				"en");
		
		OntClass oc_RelatedTopic = ontology_model.createClass(this.bcf_ns + "RelatedTopic");
		oc_RelatedTopic.addComment("Relation between topics (Clash -> PfV -> Opening)", "en");

		ObjectProperty op_hasHeader = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Header");
		op_hasHeader.addDomain(oc_Markup);
		op_hasHeader.addRange(oc_Header);
		addHeader(oc_Header);

		ObjectProperty op_hasTopic = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Topic");
		op_hasTopic.addDomain(oc_Markup);
		op_hasTopic.addRange(oc_Topic);
		addTopic(oc_Topic);

		ObjectProperty op_hasComment = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Comment");
		op_hasComment.addDomain(oc_Markup);
		op_hasComment.addRange(oc_Comment);
		addComment(oc_Comment);

		ObjectProperty op_hasViewpoints = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Viewpoints");
		op_hasViewpoints.addDomain(oc_Markup);
		op_hasViewpoints.addRange(oc_Viewpoints);
		addViePoints(oc_Viewpoints);


		ObjectProperty op_hasRelatedTopic = ontology_model.createObjectProperty(this.bcf_ns + "has" + "RelatedTopic");
		op_hasRelatedTopic.addDomain(oc_Markup);
		op_hasRelatedTopic.addRange(oc_RelatedTopic);
		addRelatedTopic(oc_RelatedTopic);
	}

	private void addHeader(OntClass oc_Header) {
		DatatypeProperty dp_IfcProject = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "IfcProject");
		dp_IfcProject.addComment("IfcGuid Reference to the project to which this topic is related in the IFC file", "en");
		dp_IfcProject.addDomain(oc_Header);
		dp_IfcProject.addRange(XSD.xstring);
		setOptionalAttribute(oc_Header, dp_IfcProject);
		
		DatatypeProperty IfcSpatialStructureElement = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "IfcSpatialStructureElement");
		IfcSpatialStructureElement.addComment("IfcGuid Reference to the spatial structure element, e.g. IfcBuildingStorey, to which this topic is related.", "en");
		IfcSpatialStructureElement.addDomain(oc_Header);
		dp_IfcProject.addRange(XSD.xstring);
		setOptionalAttribute(oc_Header, IfcSpatialStructureElement);

		ObjectProperty op_FileReference = ontology_model.createObjectProperty(this.bcf_ns + "has" + "FileReference");
		op_FileReference.addComment("URI to IfcFile.", "en");
		op_FileReference.addDomain(oc_Header);
		setOptionalAttribute(oc_Header, op_FileReference);

		ObjectProperty op_FileDate = ontology_model.createObjectProperty(this.bcf_ns + "has" + "FileDate");
		op_FileDate.addDomain(oc_Header);
		setOptionalAttribute(oc_Header, op_FileDate);

	}

	private void addTopic(OntClass oc_Topic) {
		DatatypeProperty dp_TopicGuid = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "TopicGuid");
		dp_TopicGuid.addComment("Guid of the topic", "en");
		dp_TopicGuid.addDomain(oc_Topic);
		dp_TopicGuid.addRange(XSD.xstring);
		setObligatoryAttribute(oc_Topic, dp_TopicGuid);  // In specification:  Optional=NO, but has an URI

		ObjectProperty op_TopicType = ontology_model.createObjectProperty(this.bcf_ns + "has" + "TopicType");
		op_TopicType.addComment("Type of the topic (Predefined list)", "en");
		op_TopicType.addDomain(oc_Topic);
		setOptionalAttribute(oc_Topic, op_TopicType);

		ObjectProperty op_TopicStatus = ontology_model.createObjectProperty(this.bcf_ns + "has" + "TopicStatus");
		op_TopicStatus.addComment("Type of the topic (Predefined list)", "en");
		op_TopicStatus.addDomain(oc_Topic);
		setOptionalAttribute(oc_Topic, op_TopicStatus);

		addTopicElements(oc_Topic);
	}

	private void addTopicElements(OntClass oc_Topic) {
		ObjectProperty op_ReferenceLink = ontology_model.createObjectProperty(this.bcf_ns + "has" + "ReferenceLink");
		op_ReferenceLink.addComment("List of references to the topic, for example, a work request management system or an URI to a model.", "en");
		op_ReferenceLink.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_ReferenceLink);

		ObjectProperty op_hasTitle = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Title");
		op_hasTitle.addComment("Title of the topic.", "en");
		op_hasTitle.addDomain(oc_Topic);
		setObligatoryElement(oc_Topic, op_hasTitle);

		ObjectProperty op_Priority = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Priority");
		op_Priority.addComment("Topic priority. The list of possible values are defined in the extension schema.", "en");
		op_Priority.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_Priority);

		ObjectProperty op_Index = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Index");
		op_Index.addComment("Number to maintain the order of the topics.", "en");
		op_Index.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_Index);

		ObjectProperty op_Labels = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Labels");
		op_Labels.addComment("Tags for grouping Topics. The list of possible values are defined in the extension schema.", "en");
		op_Labels.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_Labels);

		DatatypeProperty dp_CreationDate = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "CreationDate");
		dp_CreationDate.addComment("Date when the topic was created.", "en");
		dp_CreationDate.addDomain(oc_Topic);
		dp_CreationDate.addRange(XSD.dateTime);
		setObligatoryElement(oc_Topic, dp_CreationDate);
		
		ObjectProperty op_CreationAuthor = ontology_model.createObjectProperty(this.bcf_ns + "has" + "CreationAuthor");
		op_CreationAuthor.addComment("User who created the topic.", "en");
		op_CreationAuthor.addDomain(oc_Topic);
		setObligatoryElement(oc_Topic, op_CreationAuthor);

		DatatypeProperty dp_ModifiedDate = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "Topic_ModifiedDate");
		dp_ModifiedDate.addComment("Date when the topic was last modified. Exists only when Topic has been modified after creation.", "en");
		dp_ModifiedDate.addDomain(oc_Topic);
		dp_ModifiedDate.addRange(XSD.dateTime);
		setOptionalElement(oc_Topic, dp_ModifiedDate);
		
		
		ObjectProperty op_ModifiedAuthor = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Topic_ModifiedAuthor");
		op_ModifiedAuthor.addComment("User who modified the topic. Exists only when Topic has been modified after creation.", "en");
		op_ModifiedAuthor.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_ModifiedAuthor);


		DatatypeProperty dp_DueDate = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "DueDate");
		dp_DueDate.addComment("Date until when the topics issue needs to be resolved.", "en");
		dp_DueDate.addDomain(oc_Topic);
		dp_DueDate.addRange(XSD.dateTime);
		setOptionalElement(oc_Topic, dp_DueDate);

		ObjectProperty op_AssignedTo = ontology_model.createObjectProperty(this.bcf_ns + "has" + "AssignedTo");
		op_AssignedTo.addComment("	The user to whom this topic is assigned to. Recommended to be in email format. The list of possible values are defined in the extension schema.", "en");
		op_AssignedTo.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_AssignedTo);

		ObjectProperty op_Description = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Description");
		op_Description.addComment("Description of the topic.", "en");
		op_Description.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_Description);
		
		ObjectProperty op_Stage = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Stage");
		op_Stage.addComment("Stage this topic is part of (Predefined list).", "en");
		op_Stage.addDomain(oc_Topic);
		setOptionalElement(oc_Topic, op_Stage);

	}


	private void addRelatedTopic(OntClass oc_RelatedTopic) {
		ObjectProperty op_RelatedTopic = ontology_model.createObjectProperty(this.bcf_ns + "has" + "RelatedTopic");
		op_RelatedTopic.addComment(" GUID of the referenced topic", "en");
		op_RelatedTopic.addDomain(oc_RelatedTopic);
		setOptionalElement(oc_RelatedTopic, op_RelatedTopic);  // element, since can be many
	}

	private void addComment(OntClass oc_Comment) {
		DatatypeProperty dp_Date = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "CommentDate");
		dp_Date.addComment("Date of the comment", "en");
		dp_Date.addDomain(oc_Comment);
		dp_Date.addRange(XSD.dateTime);
		setObligatoryAttribute(oc_Comment, dp_Date);  // ATTR, since one

		ObjectProperty op_Author = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Author");
		op_Author.addComment("Comment author", "en");
		op_Author.addDomain(oc_Comment);
		setObligatoryElement(oc_Comment, op_Author);  // can be many

		DatatypeProperty dp_Comment = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "Comment");
		dp_Comment.addComment("The comment text", "en");
		dp_Comment.addDomain(oc_Comment);
		dp_Comment.addRange(XSD.xstring);
		setObligatoryAttribute(oc_Comment, dp_Comment);  // ATTR, since one per comment
		
		ObjectProperty op_Viewpoint = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Viewpoint");
		op_Viewpoint.addComment("Back reference to the viewpoint", "en");
		op_Viewpoint.addDomain(oc_Comment);
		setOptionalElement(oc_Comment, op_Viewpoint);

		//TODO make Modification class
		DatatypeProperty dp_ModifiedDate = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "Comment_ModifiedDate");
		dp_ModifiedDate.addComment("The date when comment was modified", "en");
		dp_ModifiedDate.addDomain(oc_Comment);
		dp_ModifiedDate.addRange(XSD.dateTime);
		setOptionalElement(oc_Comment, dp_ModifiedDate);  


		ObjectProperty op_ModifiedAuthor = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Comment:ModifiedAuthor");
		op_ModifiedAuthor.addComment("The author who modified the comment", "en");
		op_ModifiedAuthor.addDomain(oc_Comment);
		setOptionalElement(oc_Comment, op_ModifiedAuthor); 


	}

	private void addViePoints(OntClass oc_Viewpoints) {
		ObjectProperty op_Viewpoint = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Viewpoint");
		op_Viewpoint.addComment("The viewpoint ", "en");
		op_Viewpoint.addDomain(oc_Viewpoints);
		setOptionalElement(oc_Viewpoints, op_Viewpoint); 

		ObjectProperty op_Snapshot = ontology_model.createObjectProperty(this.bcf_ns + "has" + "Snapshot");
		op_Snapshot.addComment("The URL of the snapshot(.png)", "en");
		op_Snapshot.addDomain(oc_Viewpoints);
		setOptionalElement(oc_Viewpoints, op_Snapshot); 

		DatatypeProperty dp_Index = ontology_model.createDatatypeProperty(this.bcf_ns + "has" + "Index");
		dp_Index.addComment("Parameter for sorting", "en");
		dp_Index.addDomain(oc_Viewpoints);
		dp_Index.addRange(XSD.integer);
		setOptionalElement(oc_Viewpoints, dp_Index);  

	}

	

    private void setOptionalElement(OntClass ontoClass, OntProperty prop) {
        OntClass minCardinalityRestriction = ontology_model.createMinCardinalityRestriction(null, prop, 0);
        ontoClass.addSuperClass(minCardinalityRestriction);
    }
    
    
    private void setOptionalAttribute(OntClass ontoClass, OntProperty prop) {
        OntClass minCardinalityRestriction = ontology_model.createMinCardinalityRestriction(null, prop, 0);
        ontoClass.addSuperClass(minCardinalityRestriction);
        OntClass maxCardinalityRestriction = ontology_model.createMaxCardinalityRestriction(null, prop, 1);
        ontoClass.addSuperClass(maxCardinalityRestriction);
    }

    private void setObligatoryAttribute(OntClass ontoClass, OntProperty prop) {
        OntClass minCardinalityRestriction = ontology_model.createMinCardinalityRestriction(null, prop, 1);
        ontoClass.addSuperClass(minCardinalityRestriction);
        OntClass maxCardinalityRestriction = ontology_model.createMaxCardinalityRestriction(null, prop, 1);
        ontoClass.addSuperClass(maxCardinalityRestriction);
    }

    
    private void setObligatoryElement(OntClass ontoClass, OntProperty prop) {
        OntClass minCardinalityRestriction = ontology_model.createMinCardinalityRestriction(null, prop, 1);
        ontoClass.addSuperClass(minCardinalityRestriction);
    }

	public static void main(String[] args) {
		new CreateBCFOntology();
	}
}
