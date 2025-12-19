package com.drivelite.client.ui;

import java.util.prefs.Preferences;

import com.drivelite.client.ClientMain;
import com.drivelite.client.service.ServiceException;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Screen S1 - Login Screen.
 * Cho phép user nhập server host/port và đăng nhập.
 */
public class LoginScreen extends VBox {

    private final ClientMain app;
    
    private TextField txtServerHost;
    private TextField txtServerPort;
    private TextField txtEmail;
    private PasswordField txtPassword;
    private Button btnLogin;
    private Button btnRegister;
    private Button btnForgotPassword;
    private CheckBox chkRememberMe;
    private ProgressIndicator progress;
    private Label lblError;
    
    private static final String PREF_EMAIL = "saved_email";
    private static final String PREF_HOST = "saved_host";
    private static final String PREF_PORT = "saved_port";
    private static final String PREF_REMEMBER = "remember_me";
    private final Preferences prefs = Preferences.userNodeForPackage(LoginScreen.class);

    public LoginScreen(ClientMain app) {
        this.app = app;
        initUI();
    }

    private void initUI() {
        setAlignment(Pos.CENTER);
        setSpacing(15);
        setPadding(new Insets(40));
        getStyleClass().add("login-screen");

        // Title
        Label title = new Label("Drive-lite");
        title.getStyleClass().add("title");

        Label subtitle = new Label("Đăng nhập để tiếp tục");
        subtitle.getStyleClass().add("subtitle");

        // Server section
        Label lblServer = new Label("Kết nối Server");
        lblServer.getStyleClass().add("section-label");

        HBox serverBox = new HBox(10);
        serverBox.setAlignment(Pos.CENTER);
        
        txtServerHost = new TextField("localhost");
        txtServerHost.setPromptText("Server IP");
        txtServerHost.setPrefWidth(200);
        
        txtServerPort = new TextField("9000");
        txtServerPort.setPromptText("Port");
        txtServerPort.setPrefWidth(80);
        
        serverBox.getChildren().addAll(txtServerHost, txtServerPort);

        // Login form
        Label lblLogin = new Label("Thông tin đăng nhập");
        lblLogin.getStyleClass().add("section-label");

        txtEmail = new TextField();
        txtEmail.setPromptText("Email");
        txtEmail.setPrefWidth(290);

        txtPassword = new PasswordField();
        txtPassword.setPromptText("Mật khẩu");
        txtPassword.setPrefWidth(290);

        // Remember me checkbox
        chkRememberMe = new CheckBox("Ghi nhớ tài khoản");
        
        // Load saved preferences
        loadSavedCredentials();

        // Buttons
        btnLogin = new Button("Đăng nhập");
        btnLogin.getStyleClass().add("primary-button");
        btnLogin.setPrefWidth(290);
        btnLogin.setOnAction(e -> handleLogin());

        HBox linkBox = new HBox(20);
        linkBox.setAlignment(Pos.CENTER);
        
        btnRegister = new Button("Đăng ký");
        btnRegister.getStyleClass().add("link-button");
        btnRegister.setOnAction(e -> showRegisterDialog());
        
        btnForgotPassword = new Button("Quên mật khẩu?");
        btnForgotPassword.getStyleClass().add("link-button");
        btnForgotPassword.setOnAction(e -> showForgotPasswordDialog());
        
        linkBox.getChildren().addAll(btnRegister, btnForgotPassword);

        // Progress & Error
        progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(30, 30);

        lblError = new Label();
        lblError.getStyleClass().add("error-label");
        lblError.setWrapText(true);
        lblError.setMaxWidth(290);

        // Layout
        getChildren().addAll(
            title, subtitle,
            new Separator(),
            lblServer, serverBox,
            lblLogin, txtEmail, txtPassword,
            chkRememberMe,
            btnLogin,
            linkBox,
            progress,
            lblError
        );

        // Enter key to login
        txtPassword.setOnAction(e -> handleLogin());
    }

    private void handleLogin() {
        String host = txtServerHost.getText().trim();
        String portStr = txtServerPort.getText().trim();
        String email = txtEmail.getText().trim();
        String password = txtPassword.getText();

        // Validation
        if (host.isEmpty()) {
            showError("Vui lòng nhập địa chỉ server");
            return;
        }
        
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Port không hợp lệ (1-65535)");
            return;
        }

        if (email.isEmpty()) {
            showError("Vui lòng nhập email");
            return;
        }

        if (password.isEmpty()) {
            showError("Vui lòng nhập mật khẩu");
            return;
        }

        // Login task
        setLoading(true);
        clearError();

        Task<String> loginTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Enable SSL/TLS with TrustAll for development
                // In production, use proper truststore
                try {
                    app.getTcpClient().enableSSLTrustAll();
                } catch (Exception e) {
                    System.err.println("[SSL] Failed to enable SSL: " + e.getMessage());
                    // Continue without SSL if it fails
                }
                
                // Connect to server
                app.getTcpClient().connect(host, port);
                
                // Login
                return app.getAuthService().login(email, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            setLoading(false);
            saveCredentials();
            app.showFileManager(email);
        });

        loginTask.setOnFailed(e -> {
            setLoading(false);
            Throwable ex = loginTask.getException();
            // Debug: print full stack trace
            System.err.println("[LOGIN ERROR]");
            ex.printStackTrace();
            
            if (ex instanceof ServiceException) {
                showError(ex.getMessage());
            } else if (ex.getMessage() != null && ex.getMessage().contains("Connection refused")) {
                showError("Không kết nối được tới server. Kiểm tra IP/port/firewall.");
            } else {
                showError("Lỗi: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName()));
            }
        });

        new Thread(loginTask).start();
    }

    private void showRegisterDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Đăng ký tài khoản");
        dialog.setHeaderText("Tạo tài khoản mới");

        // Form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField regEmail = new TextField();
        regEmail.setPromptText("Email");
        TextField regDisplayName = new TextField();
        regDisplayName.setPromptText("Tên hiển thị");
        PasswordField regPassword = new PasswordField();
        regPassword.setPromptText("Mật khẩu (tối thiểu 6 ký tự)");
        PasswordField regConfirmPassword = new PasswordField();
        regConfirmPassword.setPromptText("Xác nhận mật khẩu");
        Label regError = new Label();
        regError.getStyleClass().add("error-label");

        grid.add(new Label("Email:"), 0, 0);
        grid.add(regEmail, 1, 0);
        grid.add(new Label("Tên hiển thị:"), 0, 1);
        grid.add(regDisplayName, 1, 1);
        grid.add(new Label("Mật khẩu:"), 0, 2);
        grid.add(regPassword, 1, 2);
        grid.add(new Label("Xác nhận:"), 0, 3);
        grid.add(regConfirmPassword, 1, 3);
        grid.add(regError, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Đăng ký");
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String email = regEmail.getText().trim();
            String displayName = regDisplayName.getText().trim();
            String password = regPassword.getText();
            String confirmPassword = regConfirmPassword.getText();

            if (email.isEmpty() || displayName.isEmpty() || password.isEmpty()) {
                regError.setText("Vui lòng điền đầy đủ thông tin");
                event.consume();
                return;
            }

            if (!password.equals(confirmPassword)) {
                regError.setText("Mật khẩu xác nhận không khớp");
                event.consume();
                return;
            }

            if (password.length() < 6) {
                regError.setText("Mật khẩu phải có ít nhất 6 ký tự");
                event.consume();
                return;
            }

            // Connect and register
            String host = txtServerHost.getText().trim();
            int port = Integer.parseInt(txtServerPort.getText().trim());

            try {
                if (!app.getTcpClient().isConnected()) {
                    app.getTcpClient().connect(host, port);
                }
                app.getAuthService().register(email, password, displayName);
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thành công");
                    alert.setHeaderText(null);
                    alert.setContentText("Đăng ký thành công! Bạn có thể đăng nhập ngay.");
                    alert.showAndWait();
                });
                
            } catch (Exception ex) {
                regError.setText(ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private void showForgotPasswordDialog() {
        // Step 1: Enter email
        TextInputDialog emailDialog = new TextInputDialog();
        emailDialog.setTitle("Quên mật khẩu");
        emailDialog.setHeaderText("Nhập email để nhận mã OTP");
        emailDialog.setContentText("Email:");

        emailDialog.showAndWait().ifPresent(email -> {
            if (email.trim().isEmpty()) return;

            String host = txtServerHost.getText().trim();
            int port = Integer.parseInt(txtServerPort.getText().trim());

            try {
                if (!app.getTcpClient().isConnected()) {
                    app.getTcpClient().connect(host, port);
                }
                app.getAuthService().forgotPassword(email.trim());

                // Step 2: Enter OTP and new password
                showResetPasswordDialog(email.trim());

            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void showResetPasswordDialog(String email) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Đặt lại mật khẩu");
        dialog.setHeaderText("OTP đã được gửi tới " + email);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField txtOtp = new TextField();
        txtOtp.setPromptText("Mã OTP 6 số");
        PasswordField txtNewPassword = new PasswordField();
        txtNewPassword.setPromptText("Mật khẩu mới");
        PasswordField txtConfirmPassword = new PasswordField();
        txtConfirmPassword.setPromptText("Xác nhận mật khẩu");
        Label lblResetError = new Label();
        lblResetError.getStyleClass().add("error-label");

        grid.add(new Label("OTP:"), 0, 0);
        grid.add(txtOtp, 1, 0);
        grid.add(new Label("Mật khẩu mới:"), 0, 1);
        grid.add(txtNewPassword, 1, 1);
        grid.add(new Label("Xác nhận:"), 0, 2);
        grid.add(txtConfirmPassword, 1, 2);
        grid.add(lblResetError, 0, 3, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Đặt lại");
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String otp = txtOtp.getText().trim();
            String newPassword = txtNewPassword.getText();
            String confirmPassword = txtConfirmPassword.getText();

            if (otp.isEmpty() || newPassword.isEmpty()) {
                lblResetError.setText("Vui lòng điền đầy đủ thông tin");
                event.consume();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                lblResetError.setText("Mật khẩu xác nhận không khớp");
                event.consume();
                return;
            }

            try {
                app.getAuthService().resetPassword(email, otp, newPassword);
                
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thành công");
                    alert.setHeaderText(null);
                    alert.setContentText("Đặt lại mật khẩu thành công!");
                    alert.showAndWait();
                });

            } catch (Exception ex) {
                lblResetError.setText(ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            progress.setVisible(loading);
            btnLogin.setDisable(loading);
            txtEmail.setDisable(loading);
            txtPassword.setDisable(loading);
            txtServerHost.setDisable(loading);
            txtServerPort.setDisable(loading);
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> lblError.setText(message));
    }

    private void clearError() {
        Platform.runLater(() -> lblError.setText(""));
    }

    private void loadSavedCredentials() {
        try {
            boolean remember = prefs.getBoolean(PREF_REMEMBER, false);
            String savedEmail = prefs.get(PREF_EMAIL, "");
            String savedHost = prefs.get(PREF_HOST, "localhost");
            String savedPort = prefs.get(PREF_PORT, "9000");
            
            System.out.println("[LOGIN] Loading saved credentials: remember=" + remember + 
                ", email=" + savedEmail + ", host=" + savedHost + ", port=" + savedPort);
            
            chkRememberMe.setSelected(remember);
            
            if (remember) {
                txtServerHost.setText(savedHost);
                txtServerPort.setText(savedPort);
                txtEmail.setText(savedEmail);
            }
        } catch (Exception e) {
            System.err.println("[LOGIN] Error loading credentials: " + e.getMessage());
        }
    }

    private void saveCredentials() {
        try {
            if (chkRememberMe.isSelected()) {
                prefs.putBoolean(PREF_REMEMBER, true);
                prefs.put(PREF_HOST, txtServerHost.getText().trim());
                prefs.put(PREF_PORT, txtServerPort.getText().trim());
                prefs.put(PREF_EMAIL, txtEmail.getText().trim());
            } else {
                prefs.putBoolean(PREF_REMEMBER, false);
                prefs.remove(PREF_HOST);
                prefs.remove(PREF_PORT);
                prefs.remove(PREF_EMAIL);
            }
            prefs.flush(); // Ghi xuống disk ngay lập tức
            System.out.println("[LOGIN] Saved credentials: remember=" + chkRememberMe.isSelected() + 
                ", email=" + txtEmail.getText().trim());
        } catch (Exception e) {
            System.err.println("[LOGIN] Error saving credentials: " + e.getMessage());
        }
    }
}
