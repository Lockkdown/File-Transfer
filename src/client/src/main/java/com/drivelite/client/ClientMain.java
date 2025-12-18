package com.drivelite.client;

import com.drivelite.client.net.TcpClient;
import com.drivelite.client.service.AuthService;
import com.drivelite.client.service.FileService;
import com.drivelite.client.ui.FileManagerScreen;
import com.drivelite.client.ui.LoginScreen;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point cho Client application (JavaFX).
 * Quản lý navigation giữa các screens.
 */
public class ClientMain extends Application {

    private static ClientMain instance;
    
    private Stage primaryStage;
    private TcpClient tcpClient;
    private AuthService authService;
    private FileService fileService;
    
    private String currentUserEmail;

    @Override
    public void start(Stage primaryStage) {
        instance = this;
        this.primaryStage = primaryStage;
        
        // Initialize services
        tcpClient = new TcpClient();
        authService = new AuthService(tcpClient);
        fileService = new FileService(tcpClient);
        
        // Show login screen
        showLoginScreen();
        
        primaryStage.setTitle("Drive-lite Client");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(e -> {
            tcpClient.close();
            Platform.exit();
        });
        primaryStage.show();
    }

    public void showLoginScreen() {
        LoginScreen loginScreen = new LoginScreen(this);
        Scene scene = new Scene(loginScreen, 450, 500);
        loadStylesheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Drive-lite - Đăng nhập");
    }

    public void showFileManager(String userEmail) {
        this.currentUserEmail = userEmail;
        FileManagerScreen fileManager = new FileManagerScreen(this);
        Scene scene = new Scene(fileManager, 1000, 700);
        loadStylesheet(scene);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Drive-lite - " + userEmail);
    }

    private void loadStylesheet(Scene scene) {
        var cssUrl = getClass().getResource("/styles/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.out.println("[WARN] CSS file not found: /styles/app.css");
        }
    }

    public void logout() {
        authService.logout();
        tcpClient.disconnect();
        currentUserEmail = null;
        showLoginScreen();
    }

    // Getters
    public static ClientMain getInstance() { return instance; }
    public TcpClient getTcpClient() { return tcpClient; }
    public AuthService getAuthService() { return authService; }
    public FileService getFileService() { return fileService; }
    public String getCurrentUserEmail() { return currentUserEmail; }
    public Stage getPrimaryStage() { return primaryStage; }

    public static void main(String[] args) {
        launch(args);
    }
}
