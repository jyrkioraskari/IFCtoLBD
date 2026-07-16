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
 * To compile this, Java 8 is needed. jfxrt.jar is included, so, the the plug-in should not be mandatory
 * but installing the http://www.eclipse.org/efxclipse/index.html and http://gluonhq.com/open-source/scene-builder/
 * make coding easier. 
 */

package org.linkedbuildingdata.ifc2lbd.desktop;

import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit;

// Importing Google's EventBus library for event handling
import com.google.common.eventbus.EventBus;

// Importing JavaFX libraries for creating the GUI application
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/*
 * JavaFX FXML is an XML-based language designed to build the user interface for JavaFX applications.
 * It allows developers to define the UI layout separately from the application logic, supporting a 
 * Model-View-Controller (MVC) architecture. This separation helps in maintaining a clean structure 
 * for larger applications. For more details, visit: https://examples.javacodegeeks.com/java-development/desktop-java/javafx/fxml/javafx-fxml-tutorial/
 */

public class IFCtoLBD_Desktop extends Application {
    // Initializing the event bus for handling application events
    private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
    
    @Override
    public void start(Stage stage) throws Exception {
        // Loading the FXML file for the GUI layout
        Parent root = FXMLLoader.load(getClass().getResource("IFCtoLBD.fxml"));
        // Creating a new scene with the loaded layout
        Scene scene = new Scene(root);
        // Setting the scene to the stage and displaying it
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        // Posting a system exit event to the event bus when the application stops
        eventBus.post(new IFCtoLBD_SystemExit("Application exit."));
    }

   
    public static void main(String[] args) {
        // Launching the JavaFX application
        launch(args);
    }
}
