package com.drivelite.client;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Entry point cho Client application (JavaFX).
 * Placeholder - sẽ implement chi tiết ở M12.
 */
public class ClientMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("Drive-lite Client v1.0.0\n\n[M0] Placeholder - Client structure ready!");
        label.setStyle("-fx-font-size: 18px; -fx-text-alignment: center;");
        
        StackPane root = new StackPane(label);
        Scene scene = new Scene(root, 600, 400);
        
        primaryStage.setTitle("Drive-lite Client");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
