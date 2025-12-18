package com.drivelite.client.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import com.drivelite.client.ClientMain;
import com.drivelite.client.model.FileItem;
import com.drivelite.client.model.VersionInfo;
import com.drivelite.client.service.FileService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * Screen S2 - File Manager Screen.
 * Hiển thị danh sách files, cho phép upload/download/share.
 */
public class FileManagerScreen extends BorderPane {

    private final ClientMain app;
    private final FileService fileService;
    
    private TableView<FileItem> tblFiles;
    private ObservableList<FileItem> fileList;
    private Label lblStatus;
    private Label lblUser;
    private ProgressIndicator progress;
    private Button btnUpload, btnDownload, btnShare, btnDelete, btnVersions, btnRefresh, btnUpdateVersion;
    
    private FileItem selectedFile;
    private boolean showingMyFiles = true;

    public FileManagerScreen(ClientMain app) {
        this.app = app;
        this.fileService = app.getFileService();
        initUI();
        loadFiles();
    }

    private void initUI() {
        // Top bar
        setTop(createTopBar());
        
        // Center - File table
        setCenter(createFileTable());
        
        // Right panel - Actions
        setRight(createActionPanel());
        
        // Bottom - Status bar
        setBottom(createStatusBar());
    }

    private HBox createTopBar() {
        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(10, 15, 10, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");

        lblUser = new Label("👤 " + app.getCurrentUserEmail());
        lblUser.getStyleClass().add("user-label");


        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToggleGroup viewToggle = new ToggleGroup();
        RadioButton rbMyFiles = new RadioButton("File của tôi");
        rbMyFiles.setToggleGroup(viewToggle);
        rbMyFiles.setSelected(true);
        rbMyFiles.setOnAction(e -> { showingMyFiles = true; loadFiles(); });
        
        RadioButton rbShared = new RadioButton("Được chia sẻ");
        rbShared.setToggleGroup(viewToggle);
        rbShared.setOnAction(e -> { showingMyFiles = false; loadFiles(); });

        btnRefresh = new Button("🔄 Làm mới");
        btnRefresh.setOnAction(e -> loadFiles());

        Button btnLogout = new Button("Đăng xuất");
        btnLogout.getStyleClass().add("link-button");
        btnLogout.setOnAction(e -> app.logout());

        topBar.getChildren().addAll(lblUser, spacer, rbMyFiles, rbShared, btnRefresh, btnLogout);
        return topBar;
    }

    @SuppressWarnings("unchecked")
    private VBox createFileTable() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(10));

        fileList = FXCollections.observableArrayList();
        tblFiles = new TableView<>(fileList);
        tblFiles.setPlaceholder(new Label("Không có file nào"));

        // Columns
        TableColumn<FileItem, String> colName = new TableColumn<>("Tên file");
        colName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        colName.setPrefWidth(300);

        TableColumn<FileItem, String> colSize = new TableColumn<>("Kích thước");
        colSize.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFormattedSize()));
        colSize.setPrefWidth(100);

        TableColumn<FileItem, String> colOwner = new TableColumn<>("Chủ sở hữu");
        colOwner.setCellValueFactory(new PropertyValueFactory<>("ownerEmail"));
        colOwner.setPrefWidth(200);

        TableColumn<FileItem, String> colPermission = new TableColumn<>("Quyền");
        colPermission.setCellValueFactory(new PropertyValueFactory<>("permission"));
        colPermission.setPrefWidth(80);

        tblFiles.getColumns().addAll(colName, colSize, colOwner, colPermission);
        
        // Selection listener
        tblFiles.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedFile = newVal;
            updateActionButtons();
        });

        // Double click to show file info/preview
        tblFiles.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && selectedFile != null) {
                showFilePreviewDialog(selectedFile);
            }
        });

        VBox.setVgrow(tblFiles, Priority.ALWAYS);
        container.getChildren().add(tblFiles);
        return container;
    }

    private VBox createActionPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));
        panel.setPrefWidth(180);
        panel.getStyleClass().add("action-panel");

        Label lblActions = new Label("Thao tác");
        lblActions.getStyleClass().add("section-label");

        btnUpload = new Button("📤 Upload");
        btnUpload.setPrefWidth(150);
        btnUpload.setOnAction(e -> handleUpload());

        btnDownload = new Button("📥 Download");
        btnDownload.setPrefWidth(150);
        btnDownload.setDisable(true);
        btnDownload.setOnAction(e -> handleDownload());

        btnShare = new Button("🔗 Chia sẻ");
        btnShare.setPrefWidth(150);
        btnShare.setDisable(true);
        btnShare.setOnAction(e -> handleShare());

        btnDelete = new Button("🗑️ Xóa");
        btnDelete.setPrefWidth(150);
        btnDelete.setDisable(true);
        btnDelete.getStyleClass().add("danger-button");
        btnDelete.setOnAction(e -> handleDelete());

        btnVersions = new Button("📋 Lịch sử");
        btnVersions.setPrefWidth(150);
        btnVersions.setDisable(true);
        btnVersions.setOnAction(e -> handleVersionHistory());

        btnUpdateVersion = new Button("🔄 Cập nhật");
        btnUpdateVersion.setPrefWidth(150);
        btnUpdateVersion.setDisable(true);
        btnUpdateVersion.setOnAction(e -> handleUploadNewVersion());

        progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(40, 40);

        panel.getChildren().addAll(lblActions, btnUpload, btnDownload, btnShare, btnDelete, btnVersions, btnUpdateVersion, progress);
        return panel;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8, 15, 8, 15));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");

        lblStatus = new Label("Sẵn sàng");
        statusBar.getChildren().add(lblStatus);
        return statusBar;
    }

    private void updateActionButtons() {
        boolean hasSelection = selectedFile != null;
        boolean isOwner = hasSelection && "OWNER".equals(selectedFile.getPermission());

        // Update action panel buttons
        btnDownload.setDisable(!hasSelection);
        btnShare.setDisable(!isOwner);
        btnDelete.setDisable(!isOwner);
        btnVersions.setDisable(!hasSelection);
        
        // Cập nhật chỉ cho EDIT hoặc OWNER
        boolean canEdit = hasSelection && ("OWNER".equals(selectedFile.getPermission()) || "EDIT".equals(selectedFile.getPermission()));
        btnUpdateVersion.setDisable(!canEdit);
    }

    private void loadFiles() {
        setLoading(true);
        setStatus("Đang tải danh sách file...");

        Task<List<FileItem>> task = new Task<>() {
            @Override
            protected List<FileItem> call() throws Exception {
                if (showingMyFiles) {
                    return fileService.listMyFiles();
                } else {
                    return fileService.listSharedWithMe();
                }
            }
        };

        task.setOnSucceeded(e -> {
            fileList.setAll(task.getValue());
            setLoading(false);
            setStatus("Đã tải " + fileList.size() + " file");
        });

        task.setOnFailed(e -> {
            setLoading(false);
            showError("Lỗi tải file: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để upload");
        File file = fileChooser.showOpenDialog(app.getPrimaryStage());
        
        if (file == null) return;

        setLoading(true);
        setStatus("Đang upload: " + file.getName());

        Task<FileItem> task = new Task<>() {
            @Override
            protected FileItem call() throws Exception {
                return fileService.uploadFile(file, (current, total) -> {
                    double percent = (double) current / total * 100;
                    Platform.runLater(() -> setStatus(String.format("Upload: %.1f%%", percent)));
                });
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            setStatus("Upload thành công: " + file.getName());
            loadFiles();
        });

        task.setOnFailed(e -> {
            setLoading(false);
            showError("Upload thất bại: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void handleDownload() {
        if (selectedFile == null) return;

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Chọn thư mục lưu file");
        File dir = dirChooser.showDialog(app.getPrimaryStage());
        
        if (dir == null) return;

        File destination = new File(dir, selectedFile.getFileName());
        
        if (destination.exists()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("File đã tồn tại");
            confirm.setHeaderText("File đã tồn tại trong thư mục");
            confirm.setContentText("File \"" + selectedFile.getFileName() + "\" đã tồn tại.\nBạn có muốn ghi đè không?");
            confirm.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        setLoading(true);
        setStatus("Đang download: " + selectedFile.getFileName());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fileService.downloadFile(selectedFile.getFileId(), destination, (current, total) -> {
                    double percent = (double) current / total * 100;
                    Platform.runLater(() -> setStatus(String.format("Download: %.1f%%", percent)));
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            setStatus("Download thành công: " + selectedFile.getFileName());
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã tải file về: " + destination.getAbsolutePath());
            alert.showAndWait();
        });

        task.setOnFailed(e -> {
            setLoading(false);
            showError("Download thất bại: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void handleShare() {
        if (selectedFile == null) return;
        
        final int fileId = selectedFile.getFileId();
        final String fileName = selectedFile.getFileName();

        // Tạo custom dialog
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Chia sẻ file");
        dialog.setHeaderText("Chia sẻ: " + fileName);
        
        // Content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));
        
        javafx.scene.control.TextField emailField = new javafx.scene.control.TextField();
        emailField.setPromptText("example@gmail.com");
        emailField.setPrefWidth(250);
        
        javafx.scene.control.ComboBox<String> permBox = new javafx.scene.control.ComboBox<>();
        permBox.getItems().addAll("VIEW", "EDIT");
        permBox.setValue("VIEW");
        permBox.setPrefWidth(250);
        
        grid.add(new Label("Email người nhận:"), 0, 0);
        grid.add(emailField, 0, 1);
        grid.add(new Label("Quyền truy cập:"), 0, 2);
        grid.add(permBox, 0, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(320);
        
        // Convert result
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{emailField.getText().trim(), permBox.getValue()};
            }
            return null;
        });
        
        Optional<String[]> result = dialog.showAndWait();
        if (result.isEmpty() || result.get()[0].isEmpty()) {
            return;
        }
        
        String email = result.get()[0];
        String permission = result.get()[1];
        
        // Share trên background thread
        setStatus("Đang chia sẻ...");
        Task<Void> shareTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fileService.shareFile(fileId, email, permission);
                return null;
            }
        };
        
        shareTask.setOnSucceeded(e -> {
            setStatus("Đã chia sẻ với " + email);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã chia sẻ \"" + fileName + "\" với " + email + "\nQuyền: " + permission);
            alert.showAndWait();
        });
        
        shareTask.setOnFailed(e -> {
            showError("Chia sẻ thất bại: " + shareTask.getException().getMessage());
        });
        
        new Thread(shareTask).start();
    }

    private void handleDelete() {
        if (selectedFile == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xóa");
        confirm.setHeaderText(null);
        confirm.setContentText("Bạn có chắc muốn xóa file: " + selectedFile.getFileName() + "?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        setLoading(true);
        setStatus("Đang xóa: " + selectedFile.getFileName());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fileService.deleteFile(selectedFile.getFileId());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            setStatus("Đã xóa: " + selectedFile.getFileName());
            loadFiles();
        });

        task.setOnFailed(e -> {
            setLoading(false);
            showError("Xóa thất bại: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void handleUploadNewVersion() {
        if (selectedFile == null) return;
        
        // Kiểm tra quyền
        String perm = selectedFile.getPermission();
        if (!"OWNER".equals(perm) && !"EDIT".equals(perm)) {
            showError("Bạn không có quyền cập nhật file này.\nYêu cầu quyền EDIT hoặc OWNER.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để cập nhật: " + selectedFile.getFileName());
        File file = fileChooser.showOpenDialog(app.getPrimaryStage());
        
        if (file == null) return;
        
        // Hỏi ghi chú (optional)
        javafx.scene.control.TextInputDialog noteDialog = new javafx.scene.control.TextInputDialog();
        noteDialog.setTitle("Ghi chú phiên bản");
        noteDialog.setHeaderText("Cập nhật: " + selectedFile.getFileName());
        noteDialog.setContentText("Ghi chú (tùy chọn):");
        noteDialog.getDialogPane().setPrefWidth(350);
        
        String note = noteDialog.showAndWait().orElse("");
        
        final int fileId = selectedFile.getFileId();
        final String fileName = selectedFile.getFileName();
        
        setLoading(true);
        setStatus("Đang cập nhật: " + fileName);
        
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fileService.uploadNewVersion(fileId, file, note, (current, total) -> {
                    double percent = (double) current / total * 100;
                    Platform.runLater(() -> setStatus(String.format("Cập nhật: %.1f%%", percent)));
                });
                return null;
            }
        };
        
        task.setOnSucceeded(e -> {
            setLoading(false);
            setStatus("Cập nhật thành công: " + fileName);
            loadFiles();
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText("Đã cập nhật phiên bản mới cho \"" + fileName + "\"");
            alert.showAndWait();
        });
        
        task.setOnFailed(e -> {
            setLoading(false);
            showError("Cập nhật thất bại: " + task.getException().getMessage());
        });
        
        new Thread(task).start();
    }

    private void handleVersionHistory() {
        if (selectedFile == null) return;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Lịch sử phiên bản");
        dialog.setHeaderText("File: " + selectedFile.getFileName());

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefWidth(500);

        ListView<VersionInfo> listVersions = new ListView<>();
        listVersions.setPrefHeight(300);

        try {
            List<VersionInfo> versions = fileService.getVersions(selectedFile.getFileId());
            listVersions.getItems().addAll(versions);
            
            listVersions.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(VersionInfo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String note = item.getNote();
                        String noteText = (note != null && !note.isEmpty()) ? "\n   📝 " + note : "";
                        setText(String.format("v%d - %s - %s%s", 
                            item.getVersionNumber(),
                            item.getFormattedSize(),
                            item.getUploaderEmail() != null ? item.getUploaderEmail() : "Unknown",
                            noteText));
                    }
                }
            });
        } catch (Exception e) {
            listVersions.setPlaceholder(new Label("Lỗi tải lịch sử: " + e.getMessage()));
        }

        Button btnDownloadVersion = new Button("Tải phiên bản này");
        btnDownloadVersion.setDisable(true);
        
        listVersions.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            btnDownloadVersion.setDisable(newVal == null);
        });

        btnDownloadVersion.setOnAction(e -> {
            VersionInfo selected = listVersions.getSelectionModel().getSelectedItem();
            if (selected != null) {
                downloadVersion(selected.getVersionNumber());
                dialog.close();
            }
        });

        content.getChildren().addAll(listVersions, btnDownloadVersion);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    private void downloadVersion(int versionNumber) {
        if (selectedFile == null) return;

        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Chọn thư mục lưu file");
        File dir = dirChooser.showDialog(app.getPrimaryStage());
        
        if (dir == null) return;

        String fileName = selectedFile.getFileName();
        int dotIndex = fileName.lastIndexOf('.');
        String newName = dotIndex > 0
            ? fileName.substring(0, dotIndex) + "_v" + versionNumber + fileName.substring(dotIndex)
            : fileName + "_v" + versionNumber;
            
        File destination = new File(dir, newName);

        setLoading(true);
        setStatus("Đang download phiên bản " + versionNumber);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fileService.downloadFile(selectedFile.getFileId(), versionNumber, destination, (current, total) -> {
                    double percent = (double) current / total * 100;
                    Platform.runLater(() -> setStatus(String.format("Download: %.1f%%", percent)));
                });
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            setStatus("Download thành công phiên bản " + versionNumber);
        });

        task.setOnFailed(e -> {
            setLoading(false);
            showError("Download thất bại: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            progress.setVisible(loading);
            btnUpload.setDisable(loading);
            btnRefresh.setDisable(loading);
        });
    }

    private void setStatus(String status) {
        Platform.runLater(() -> lblStatus.setText(status));
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            lblStatus.setText("Lỗi: " + message);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showFilePreviewDialog(FileItem file) {
        String fileName = file.getFileName().toLowerCase();
        
        // Kiểm tra có thể preview không
        boolean isImage = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
                         fileName.endsWith(".png") || fileName.endsWith(".gif") || fileName.endsWith(".bmp");
        boolean isText = fileName.endsWith(".txt") || fileName.endsWith(".md") || 
                        fileName.endsWith(".json") || fileName.endsWith(".xml") || 
                        fileName.endsWith(".java") || fileName.endsWith(".css") ||
                        fileName.endsWith(".html") || fileName.endsWith(".js") ||
                        fileName.endsWith(".py") || fileName.endsWith(".sql");
        boolean canPreview = (isImage || isText) && file.getFileSize() < 10 * 1024 * 1024; // < 10MB
        
        if (canPreview) {
            showPreviewWithContent(file, isImage, isText);
        } else {
            showBasicFileInfo(file);
        }
    }
    
    private void showPreviewWithContent(FileItem file, boolean isImage, boolean isText) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Xem trước: " + file.getFileName());
        dialog.setHeaderText(null);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        
        // Loading indicator
        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        Label loadingLabel = new Label("Đang tải preview...");
        VBox loadingBox = new VBox(10, loadingIndicator, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefSize(600, 400);
        content.getChildren().add(loadingBox);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setMinWidth(650);
        dialog.getDialogPane().setMinHeight(500);
        
        // Tải file về temp và hiển thị preview
        final int fileId = file.getFileId();
        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                File tempFile = File.createTempFile("drivelite_preview_", "_" + file.getFileName());
                tempFile.deleteOnExit();
                fileService.downloadFile(fileId, tempFile, null);
                return tempFile;
            }
        };
        
        downloadTask.setOnSucceeded(e -> {
            File tempFile = downloadTask.getValue();
            content.getChildren().clear();
            
            // File info bar
            HBox infoBar = new HBox(20);
            infoBar.setAlignment(Pos.CENTER_LEFT);
            infoBar.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10;");
            infoBar.getChildren().addAll(
                new Label("📄 " + file.getFileName()),
                new Label("Kích thước: " + file.getFormattedSize()),
                new Label("Quyền: " + file.getPermission())
            );
            content.getChildren().add(infoBar);
            
            if (isImage) {
                // Image preview
                try {
                    Image image = new Image(tempFile.toURI().toString());
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setFitWidth(600);
                    imageView.setFitHeight(400);
                    
                    ScrollPane scrollPane = new ScrollPane(imageView);
                    scrollPane.setFitToWidth(true);
                    scrollPane.setPrefSize(600, 400);
                    content.getChildren().add(scrollPane);
                } catch (Exception ex) {
                    content.getChildren().add(new Label("❌ Không thể hiển thị hình ảnh: " + ex.getMessage()));
                }
            } else if (isText) {
                // Text preview
                try {
                    String textContent = Files.readString(tempFile.toPath(), StandardCharsets.UTF_8);
                    // Giới hạn 50KB text để tránh lag
                    if (textContent.length() > 50000) {
                        textContent = textContent.substring(0, 50000) + "\n\n... (Nội dung bị cắt bớt, tải xuống để xem đầy đủ)";
                    }
                    
                    TextArea textArea = new TextArea(textContent);
                    textArea.setEditable(false);
                    textArea.setWrapText(true);
                    textArea.setPrefSize(600, 400);
                    textArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
                    content.getChildren().add(textArea);
                } catch (IOException ex) {
                    content.getChildren().add(new Label("❌ Không thể đọc file: " + ex.getMessage()));
                }
            }
            
        });
        
        downloadTask.setOnFailed(e -> {
            content.getChildren().clear();
            Label errorLabel = new Label("❌ Lỗi tải preview: " + downloadTask.getException().getMessage());
            errorLabel.setStyle("-fx-text-fill: red;");
            content.getChildren().add(errorLabel);
        });
        
        new Thread(downloadTask).start();
        dialog.showAndWait();
    }
    
    private void showBasicFileInfo(FileItem file) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thông tin file");
        dialog.setHeaderText(file.getFileName());

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(400);

        // File info
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(8);

        infoGrid.add(new Label("Tên file:"), 0, 0);
        infoGrid.add(new Label(file.getFileName()), 1, 0);

        infoGrid.add(new Label("Kích thước:"), 0, 1);
        infoGrid.add(new Label(file.getFormattedSize()), 1, 1);

        infoGrid.add(new Label("Quyền:"), 0, 2);
        infoGrid.add(new Label(file.getPermission()), 1, 2);

        if (file.getOwnerEmail() != null) {
            infoGrid.add(new Label("Chủ sở hữu:"), 0, 3);
            infoGrid.add(new Label(file.getOwnerEmail()), 1, 3);
        }

        content.getChildren().add(infoGrid);

        // Preview message based on file type
        String fileName = file.getFileName().toLowerCase();
        Label previewLabel = new Label();
        previewLabel.setWrapText(true);
        previewLabel.setStyle("-fx-padding: 15; -fx-background-color: #f5f5f5; -fx-background-radius: 4;");

        if (file.getFileSize() > 10 * 1024 * 1024) {
            previewLabel.setText("⚠️ Tệp này quá lớn để xem trước (>10MB).\nVui lòng tải xuống để xem.");
        } else if (fileName.endsWith(".pdf")) {
            previewLabel.setText("📄 File PDF - Tải xuống để xem nội dung.");
        } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx") || 
                   fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            previewLabel.setText("📝 File Office - Tải xuống để mở.");
        } else if (fileName.endsWith(".zip") || fileName.endsWith(".rar") || 
                   fileName.endsWith(".7z")) {
            previewLabel.setText("📦 File nén - Tải xuống để giải nén.");
        } else {
            previewLabel.setText("📁 Không thể xem trước loại file này.\nVui lòng tải xuống để mở.");
        }

        content.getChildren().add(previewLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }
}
