/*
 * Originally from: 
 * http://www.javaworld.com/article/2078545/mobile-java/practical-javafx-2--part-3--refactoring-swing-jpad-s-advanced-ui-features.html
 */


/*
 * To compile this, Java 8 is needed. jfxrt.jar is included, so, the the plugin should not be mandatory
 * but installing the http://www.eclipse.org/efxclipse/index.html and http://gluonhq.com/open-source/scene-builder/
 * make coding easier. 
 * 
 */

package org.linkedbuildingdata.ifc2lbd;


import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.effect.Reflection;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


public class About extends Stage
{
   public About(Stage owner)
   {
      initOwner(owner);
      initStyle(StageStyle.UNDECORATED);
      initModality(Modality.APPLICATION_MODAL);
      Group root = new Group();
      Image img = new Image(getClass().getResourceAsStream("about.png"));
      ImageView iv = new ImageView(img);
      double width = iv.layoutBoundsProperty().getValue().getWidth();
      double height = iv.layoutBoundsProperty().getValue().getHeight();
      iv.setX(10.0);
      iv.setY((180.0-height)/2.0);
      root.getChildren().add(iv);
      Text msg1 = new Text("IFCtoLBD Desktop 2018, 2019, 2020, 2021, 2022");
      msg1.setFill(Color.WHITE);
      msg1.setFont(new Font("Arial", 16.0));
      msg1.setX(iv.getX()+width+5);
      msg1.setY(iv.getY()+height/2.0);
      root.getChildren().add(msg1);
      Text msg2 = new Text("Authors: Oraskari, Bonduel, McGlinn, Wagner,\nPauwels, Kukkonen, Steyskaland, and Lehtonen");
      msg2.setFill(Color.WHITE);
      msg2.setFont(new Font("Arial", 12.0));
      msg2.setX(msg1.getX());
      msg2.setY(msg1.getY()+25.0);
      root.getChildren().add(msg2);
      
      Reflection r = new Reflection();
      r.setFraction(1.0);
      root.setEffect(r);
      Scene scene = new Scene(root, 550.0, 250.0, Color.BLACK);
      EventHandler<MouseEvent> ehme;
      ehme = new EventHandler<MouseEvent>()
      {

		public void handle(MouseEvent event) {
			close();
		}
       
      };
      scene.setOnMousePressed(ehme);
      setScene(scene);
      setX(owner.getX()+Math.abs(owner.getWidth()-scene.getWidth())/2.0);
      setY(owner.getY()+Math.abs(owner.getHeight()-scene.getHeight())/2.0);
   }
}