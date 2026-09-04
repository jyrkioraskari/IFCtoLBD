
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


package org.linkedbuildingdata.ifc2lbd.desktop;

import java.net.URL;

import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit;

import com.google.common.eventbus.EventBus;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


public class IFCtoLBD_Desktop extends Application {
    private static final String RESOURCE_ROOT = "/org/linkedbuildingdata/ifc2lbd/desktop/";
    private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getDefaultEventBus();
    private IFCtoLBDController controller;
    
    @Override
    public void start(Stage stage) throws Exception {
        stage.initStyle(StageStyle.DECORATED);
        URL fxml = IFCtoLBD_Desktop.class.getResource(RESOURCE_ROOT + "IFCtoLBD.fxml");
        if (fxml == null) {
            throw new IllegalStateException("Missing application resource " + RESOURCE_ROOT
                    + "IFCtoLBD.fxml. Ensure src/main/resources is included in the runnable JAR.");
        }
        FXMLLoader loader = new FXMLLoader(fxml);
        Parent root = loader.load();
        this.controller = loader.getController();
        Scene scene = new Scene(root);
        URL stylesheet = IFCtoLBD_Desktop.class.getResource(RESOURCE_ROOT + "app.css");
        if (stylesheet == null) {
            throw new IllegalStateException("Missing application resource " + RESOURCE_ROOT
                    + "app.css. Ensure src/main/resources is included in the runnable JAR.");
        }
        scene.getStylesheets().add(stylesheet.toExternalForm());
        stage.setScene(scene);
        stage.setTitle("IFCtoLBD Desktop");
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(640);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (this.controller != null) {
            this.controller.shutdown();
        }
        eventBus.post(new IFCtoLBD_SystemExit("Application exit."));
    }

    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(IFCtoLBD_Desktop.class, args);
    }
    
}
