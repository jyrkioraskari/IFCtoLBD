
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

import org.linkedbuildingdata.ifc2lbd.application_messaging.IFC2LBD_ApplicationEventBusService;
import org.linkedbuildingdata.ifc2lbd.application_messaging.events.IFCtoLBD_SystemExit;

import com.google.common.eventbus.EventBus;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class IFCtoLBD_Desktop extends Application {
    private final EventBus eventBus = IFC2LBD_ApplicationEventBusService.getEventBus();
    private IFCtoLBDController controller;
    
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("IFCtoLBD.fxml"));
        Parent root = loader.load();
        this.controller = loader.getController();
        Scene scene = new Scene(root);
        stage.setScene(scene);
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
        launch(args);
    }
    
}
