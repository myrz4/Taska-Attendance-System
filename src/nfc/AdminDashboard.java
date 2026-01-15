package nfc;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.PauseTransition;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import nfc.ChildrenView;
import nfc.StaffManagementView;
import nfc.LoginView;
import org.opencv.core.Mat;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"rawtypes", "unchecked"})
public class AdminDashboard extends Application {

    private static AdminDashboard instance;
    private Label mainContent;
    private static Label scannedToday;
    private static Label notScanned;
    private static ListView<String> liveIns, liveOuts;
    private static boolean dashboardReady = false;
    private StackPane contentPane;
    private static NFCReader reader;
    private static Thread nfcReaderThread;
    private double xOffset = 0;
    private double yOffset = 0;
    private CameraPreviewWindow camWin;
    private final FaceCameraService cameraService = new FaceCameraService();
    private ImageView cameraView;

    private VBox checkInList = new VBox(6);
    private VBox checkOutList = new VBox(6);
    
    // Firestore helper
    private static Firestore db() { return FirestoreService.db(); }
    private static String today() { return java.time.LocalDate.now().toString(); } // "YYYY-MM-DD"
    
    public static AdminDashboard getInstance() { 
    	return instance; }
    
    // ✅ Load OpenCV once, but don't block dashboard if it fails
    static {
        try {
            OpenCVLoader.load();
        } catch (Throwable t) {
            System.err.println("[OpenCV] Not loaded, camera features disabled.");
            // continue; do NOT rethrow
        }
    }

            // Fallback: force exact path if needed
            // System.load("C:\\dev\\opencv\\build\\install\\java\\opencv_java4100.dll");

    public static void main(String[] args) {
        launch(args);
    }

    public AdminDashboard() {
        instance = this;
    }

    public static boolean isDashboardReady() {
        return dashboardReady;
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Admin Dashboard - Taska Attendance");

        VBox mainLayout = new VBox();
        mainLayout.setSpacing(0);
        mainLayout.setStyle(
        		"-fx-background-color: linear-gradient(to bottom right, #2E8B57 0%, #247a4b 100%);" // dark green outside
        	);

        HBox topBar = createTopBar(primaryStage);
        topBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        topBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        HBox bodyLayout = createBodyLayout(primaryStage);

        mainLayout.getChildren().addAll(topBar, bodyLayout);
        VBox.setVgrow(bodyLayout, Priority.ALWAYS);

        Scene scene = new Scene(mainLayout, 1000, 800);
        java.net.URL css = getClass().getResource("style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        else System.out.println("⚠️ Missing: style.css");

        primaryStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("👋 Closing application, releasing Serial Port...");
            if (reader != null) reader.stopReading();
            if (camWin != null) { camWin.stop(); camWin = null; }
            cameraService.stop();
            Platform.exit(); // let JavaFX call your stop()
        });

        primaryStage.show();

        startAutoRefresh();
        startNFCReader();
    }
    
    private void startAutoRefresh() {
        Timeline autoRefreshTimeline = new Timeline(
            new KeyFrame(Duration.seconds(5), e -> {
                if (scannedToday != null && notScanned != null && liveIns != null && liveOuts != null) {
                    updateStatistics();
                    updateLiveScans();
                }
            })
        );
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
    }


    private HBox createTopBar(Stage primaryStage) {
        // 1) Application title on the left
        Label title = new Label("Taska Zurah Student Management System");
        title.setStyle("-fx-text-fill: black; -fx-font-size: 16px; -fx-font-weight: bold;");

        // 2) Spacer pushes window‐control buttons to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 3) Window buttons
        Button minimizeButton = new Button("-");
        Button maximizeButton = new Button("⬜");
        Button closeButton    = new Button("X");

        minimizeButton.getStyleClass().add("window-button");
        maximizeButton.getStyleClass().add("window-button");
        closeButton.getStyleClass().addAll("window-button", "close-button");

        minimizeButton.setOnAction(e -> primaryStage.setIconified(true));
        maximizeButton.setOnAction(e -> primaryStage.setMaximized(!primaryStage.isMaximized()));
        closeButton.setOnAction(e -> {
            System.out.println("👋 Closing application, releasing Serial Port...");
            if (reader != null) reader.stopReading();
            if (camWin != null) { camWin.stop(); camWin = null; }
            Platform.exit(); // triggers your @Override stop()
        });

        // 4) Assemble the top bar
        HBox topBar = new HBox(10, title, spacer, minimizeButton, maximizeButton, closeButton);
        topBar.setPadding(new Insets(5, 10, 5, 10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #30cd30;");

        return topBar;
    }


    private HBox createBodyLayout(Stage primaryStage) {
        HBox bodyLayout = new HBox();
        bodyLayout.setStyle("-fx-background-color: transparent;");

        VBox sidebar = createSidebar(primaryStage);
        contentPane = new StackPane();
        contentPane.setStyle("-fx-background-color: transparent;");

        loadDashboardContent(); //

        bodyLayout.getChildren().addAll(sidebar, contentPane);
        HBox.setHgrow(contentPane, Priority.ALWAYS);
        return bodyLayout;
    }

    private VBox createSidebar(Stage primaryStage) {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setMaxWidth(240);
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setStyle("-fx-background-color: #30af4a;"); // light green

        ImageView profileView = new ImageView();
        profileView.setFitWidth(130);
        profileView.setFitHeight(130);
        profileView.setPreserveRatio(true);
        // Optional circular mask with soft border & drop shadow
        Circle clip = new Circle(65, 65, 65);
        profileView.setClip(clip);
        profileView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0.2, 0, 3); " +
                            "-fx-border-color: #FFD700; -fx-border-width: 3; -fx-border-radius: 65px;");

        // ✅ Load profile picture from UserSession
        String pic = UserSession.getProfilePicture();
        System.out.println("🎯 Dashboard avatar file = " + pic);

        if (pic != null && !pic.isBlank()) {
            File imgFile = new File("profile_pics", pic);
            if (imgFile.exists()) {
                profileView.setImage(new Image(imgFile.toURI().toString()));
            } else {
                System.out.println("❌ Avatar file not found: " + imgFile.getAbsolutePath());
                profileView.setImage(ImageLoader.loadSafe("default_user.png"));
            }
        } else {
            profileView.setImage(ImageLoader.loadSafe("default_user.png"));
        }

        String displayName = UserSession.getName();
        if (displayName == null || displayName.isBlank()) {
            displayName = UserSession.getUsername(); // fallback
        }
        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        ImageView beeIcon = new ImageView(loadSafe("bee-icon.png"));
        beeIcon.setFitWidth(24);
        beeIcon.setFitHeight(24);
        HBox welcomeBox = new HBox(6, nameLabel, beeIcon);
        welcomeBox.setAlignment(Pos.CENTER);

        VBox profileBox = new VBox(10, profileView, welcomeBox);
        profileBox.setAlignment(Pos.CENTER);

        // Main navigation buttons
        Button btnDashboard = createNavButton("Dashboard");
        btnDashboard.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnDashboard.setMinHeight(28);
        btnDashboard.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnDashboard.setMaxWidth(Double.MAX_VALUE);
        btnDashboard.setWrapText(true);
        btnDashboard.setTextOverrun(OverrunStyle.CLIP);

        Button btnAttendance = createNavButton("Attendance");
        btnAttendance.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnAttendance.setMinHeight(28);
        btnAttendance.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnAttendance.setMaxWidth(Double.MAX_VALUE);
        btnAttendance.setWrapText(true);
        btnAttendance.setTextOverrun(OverrunStyle.CLIP);

        // Submenu: Generate Report (always visible and centered)
        VBox attendanceSubMenu = new VBox(10);
        attendanceSubMenu.setPadding(new Insets(0, 0, 0, 0));
        attendanceSubMenu.setAlignment(Pos.CENTER);

        Button btnGenerateReport = createNavButton("Generate Report");
        btnGenerateReport.setStyle("-fx-background-color: #FFF4B4;-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnGenerateReport.setPrefHeight(38);
        btnGenerateReport.setMaxWidth(170);

        Button btnDaily = createNavButton("Daily");
        btnDaily.setStyle("-fx-background-color: #FFF4B4;-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnDaily.setPrefHeight(34);
        btnDaily.setMaxWidth(150);

        Button btnMonthly = createNavButton("Monthly");
        btnMonthly.setStyle("-fx-background-color: #FFF4B4;-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnMonthly.setPrefHeight(34);
        btnMonthly.setMaxWidth(150);

        attendanceSubMenu.getChildren().addAll(btnGenerateReport, btnDaily, btnMonthly);

        Button btnChildren = createNavButton("Children & Parents");
        btnChildren.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnChildren.setMinHeight(28);
        btnChildren.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnChildren.setMaxWidth(Double.MAX_VALUE);
        btnChildren.setWrapText(true);
        btnChildren.setTextOverrun(OverrunStyle.CLIP);

        Button btnStaff = createNavButton("Admins");
        btnStaff.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnStaff.setMinHeight(28);
        btnStaff.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnStaff.setMaxWidth(Double.MAX_VALUE);
        btnStaff.setWrapText(true);
        btnStaff.setTextOverrun(OverrunStyle.CLIP);

        Button btnLogout = createNavButton("Logout");
        btnLogout.setStyle("-fx-background-color: red;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnLogout.setMinHeight(30);
        btnLogout.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setWrapText(true);
        btnLogout.setTextOverrun(OverrunStyle.CLIP);

        Button btnTeachers = createNavButton("Teachers");
        btnTeachers.setStyle(
            "-fx-background-color: #FFCB3C;" +
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 28px;"
        );

        btnTeachers.setOnAction(e -> {
            TeacherManagementView view = new TeacherManagementView();
            setMainContent(view);
        });

        // Navigation actions
        btnDashboard.setOnAction(e -> loadDashboardContent());
        btnAttendance.setOnAction(e -> {
            AttendanceView view = new AttendanceView();
            setMainContent(view.getRoot());
        });
        btnGenerateReport.setOnAction(e -> {
        	generateReport gr = new generateReport();
        	setMainContent(gr.getRoot());
        });
        btnDaily.setOnAction(e -> showDailyReportView());
        btnMonthly.setOnAction(e -> showMonthlyReportView());

        btnChildren.setOnAction(e -> {
            ChildrenView childrenPane = new ChildrenView();
            setMainContent(childrenPane);
        });

        btnStaff.setOnAction(e -> {
            StaffManagementView staffPane = new StaffManagementView();
            setMainContent(staffPane);
        });

        btnLogout.setOnAction(event -> {
            System.out.println("👋 Closing application, releasing Serial Port...");
            if (reader != null) {
                reader.stopReading();
            }
            if (camWin != null) { camWin.stop(); camWin = null; }
           
            // Open login window
            LoginView loginView = new LoginView();
            Stage loginStage = new Stage();
            try {
                loginView.start(loginStage);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            // Close the current (dashboard) window
            Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            currentStage.close();
        });
        
        Button btnOpenCam = createNavButton("Open Camera");
        btnOpenCam.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnOpenCam.setMinHeight(28);
        btnOpenCam.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnOpenCam.setMaxWidth(Double.MAX_VALUE);
        btnOpenCam.setWrapText(true);
        btnOpenCam.setTextOverrun(OverrunStyle.CLIP);


        // action: open camera preview window
        btnOpenCam.setOnAction(e -> {
            System.out.println("[AdminDashboard] Open Camera clicked");
            if (camWin == null) camWin = new CameraPreviewWindow(primaryStage);
            camWin.show(0);
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
     // --- Add two new buttons ---
        Button btnRegisterFace = createNavButton("Register Face");
        btnRegisterFace.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnRegisterFace.setMinHeight(28);
        btnRegisterFace.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnRegisterFace.setMaxWidth(Double.MAX_VALUE);
        btnRegisterFace.setWrapText(true);
        btnRegisterFace.setTextOverrun(OverrunStyle.CLIP);

        Button btnRetrain = createNavButton("Retrain Model");
        btnRetrain.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnRetrain.setMinHeight(28);
        btnRetrain.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnRetrain.setMaxWidth(Double.MAX_VALUE);
        btnRetrain.setWrapText(true);
        btnRetrain.setTextOverrun(OverrunStyle.CLIP);

        // Actions
        btnRegisterFace.setOnAction(e -> registerFaceFlow(primaryStage));
        btnRetrain.setOnAction(e -> {
            try {
                FaceTrainer.trainAll();
                showAlert("Face model retrained successfully.", Alert.AlertType.INFORMATION);
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Training failed: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        btnOpenCam.setVisible(false);
        btnRegisterFace.setVisible(false);
        btnRetrain.setVisible(false);
        
        sidebar.getChildren().addAll(
        	    profileBox,
        	    btnDashboard,
        	    btnAttendance,
        	    attendanceSubMenu,
        	    btnChildren,
        	    btnStaff,
                btnTeachers,
        	    btnOpenCam,
        	    btnRegisterFace,
        	    btnRetrain,
        	    spacer,      // ⬅️ spacer BEFORE logout pushes logout to the bottom
        	    btnLogout    // ⬅️ logout last, always visible
        	);
        
        applySidebarResponsiveness(
        	    sidebar,
        	    profileView, 
        	    nameLabel,
        	    btnDashboard,
        	    btnAttendance,
        	    btnDaily,
        	    btnMonthly,
        	    btnChildren,
        	    btnStaff,
                btnTeachers,
        	    btnOpenCam,
        	    btnRegisterFace,
        	    btnRetrain,
        	    btnLogout
        	);

        return sidebar;
    }
    
 // ---- Responsive sidebar sizing (compress when short) ----
    private void applySidebarResponsiveness(
            VBox sidebar,
            ImageView profilePic,
            Labeled nameLabel,     // Label is a Labeled
            Button... buttons
    ) {
        // default look
        sidebar.setFillWidth(true);
        sidebar.setSpacing(20);
        sidebar.setPadding(new Insets(20));

        // sensible defaults for profile box
        profilePic.setFitWidth(100);
        profilePic.setFitHeight(130);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        sidebar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            if (newScene.getWindow() == null) return;

            newScene.getWindow().heightProperty().addListener((o, oh, nh) -> {
                double h = nh.doubleValue();

             // three tiers of compression
                boolean compact      = h < 900;
                boolean ultraCompact = h < 800;
                boolean extreme      = h < 740;  // tiny height? go harder

             // ↑ taller buttons + more vertical padding so 2-line labels fit comfortably
                double btnH   = extreme ? 46 : (ultraCompact ? 50 : (compact ? 54 : 60));
                double spacing = extreme ? 6  : (ultraCompact ? 8  : (compact ? 12 : 18));
                double pad     = extreme ? 6  : (ultraCompact ? 10 : (compact ? 12 : 18));

                double fsMain = extreme ? 13 : (ultraCompact ? 14 : (compact ? 15 : 16));
                double padV   = extreme ? 10 : (ultraCompact ? 12 : (compact ? 12 : 14));
                double padH   = 18; // a touch more horizontal padding

                // profile picture scaling helps recover a lot of vertical space
                if (extreme) {
                    profilePic.setFitWidth(76);
                    profilePic.setFitHeight(100);
                    nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
                } else if (ultraCompact) {
                    profilePic.setFitWidth(86);
                    profilePic.setFitHeight(112);
                    nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
                } else if (compact) {
                    profilePic.setFitWidth(92);
                    profilePic.setFitHeight(120);
                    nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
                } else {
                    profilePic.setFitWidth(100);
                    profilePic.setFitHeight(130);
                    nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
                }

                sidebar.setSpacing(spacing);
                sidebar.setPadding(new Insets(pad, pad, pad, pad));

                for (Button b : buttons) {
                    b.setMinHeight(28);
                    b.setPrefHeight(btnH);
                    b.setMaxWidth(Double.MAX_VALUE);

                    b.setWrapText(true);
                    b.setTextAlignment(TextAlignment.CENTER);
                    b.setTextOverrun(OverrunStyle.CLIP);

                    // append font-size & padding
                    b.setStyle(
                        "-fx-background-color: #FFCB3C;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 28px;" +
                        "-fx-font-size: " + fsMain + "px;" +
                        "-fx-padding: " + padV + " " + padH + ";"
                    );
                }
            });
        });
    }

 // --- Helper: Register Face capture flow ---
    private void registerFaceFlow(Stage owner) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Register Face");
        dlg.setHeaderText("Enter Student ID (or NFC UID) to register");
        dlg.setContentText("ID:");
        var res = dlg.showAndWait();
        if (res.isEmpty() || res.get().trim().isEmpty()) return;

        String studentId = res.get().trim();

        try {
            // Ensure folders
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("faces", studentId));

            // Start/ensure camera preview window
            if (camWin == null) camWin = new CameraPreviewWindow(owner);
            camWin.show(0); // show device 0 (if this already starts the camera, fcs.start() below is a no-op)

            // Use the preview ImageView from the camera window if available; fallback to headless
            ImageView previewView = (camWin != null && camWin.getView() != null)
                    ? camWin.getView()
                    : new ImageView();

            // Reuse the single shared camera service and make sure it's running
            FaceCameraService fcs = new FaceCameraService();
            fcs.setView(previewView);
            fcs.start(0);   // 0 = default camera

            FaceDetector fd = new FaceDetector();

            int saved = 0;
            long endAt = System.currentTimeMillis() + 8000; // ~8s capture
            int idx = 1;

            while (System.currentTimeMillis() < endAt && saved < 60) {
                org.opencv.core.Mat frame = fcs.getLastFrame();   // clone from cache
                if (frame == null || frame.empty()) {
                    try { Thread.sleep(40); } catch (InterruptedException ignored) {}
                    continue;
                }

                var rects = fd.detectFaces(frame);
                if (!rects.isEmpty()) {
                    // pick largest
                    org.opencv.core.Rect best = rects.stream()
                            .max(java.util.Comparator.comparingInt(r -> r.width * r.height))
                            .get();

                    // save 200x200 gray crop
                    java.nio.file.Path out = java.nio.file.Path.of("faces", studentId, String.format("img_%03d.png", idx++));
                    saveFace(frame, best, out);
                    saved++;
                }

                // 🔑 EXACT SPOT: release the clone at the END of each loop iteration
                frame.release();

                try { Thread.sleep(80); } catch (InterruptedException ignored) {}
            }

            // Do NOT necessarily stop the service here if your preview should stay open.
            // If you want to close the camera when done, uncomment the next line:
            // fcs.stop();

            fd.release();
            showAlert("Saved " + saved + " face images for " + studentId + ".", Alert.AlertType.INFORMATION);

        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Face registration failed: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // --- Tiny saver (same logic as FaceCaptureUtil.saveFace) ---
    private static void saveFace(org.opencv.core.Mat bgrFrame, org.opencv.core.Rect r, java.nio.file.Path outPath) {
        org.opencv.core.Mat gray = new org.opencv.core.Mat();
        org.opencv.imgproc.Imgproc.cvtColor(bgrFrame, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
        org.opencv.core.Mat face = new org.opencv.core.Mat(gray, r);
        org.opencv.imgproc.Imgproc.resize(face, face, new org.opencv.core.Size(200, 200));
        org.opencv.imgcodecs.Imgcodecs.imwrite(outPath.toString(), face);
        gray.release(); face.release();
    }
    
    public void showDailyReportView() {
        dailyReport drv = new dailyReport();
        setMainContent(drv.getRoot());
    }

    public void showMonthlyReportView() {
    	monthlyReport mrv = new monthlyReport();
        setMainContent(mrv.getRoot());
    }

    private Button createNavButton(String title) {
        Button button = new Button(title);
        //button.getStyleClass().add("s");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private void setMainContent(Node node) {
        contentPane.getChildren().setAll(node);
    }

    private void startNFCReader() {
        if (reader != null) {
            reader.stopReading();
            if (nfcReaderThread != null && nfcReaderThread.isAlive()) {
                nfcReaderThread.interrupt();
                try {
                    nfcReaderThread.join();
                    Thread.sleep(1000); // 🧠 Add this small 1 second delay to fully release COM4
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }

        reader = new NFCReader("COM3");
        nfcReaderThread = new Thread(reader);
        nfcReaderThread.start();
    }



    private void loadDashboardContent() {
        // Dashboard Header Bar (full-width)
        HBox dashboardHeader = new HBox(18);
        dashboardHeader.setAlignment(Pos.CENTER_LEFT);
        dashboardHeader.setPrefHeight(70);
        dashboardHeader.setMaxWidth(Double.MAX_VALUE); // Stretch to parent
        dashboardHeader.setStyle(
            "-fx-background-color: #2e8b57;" +
            "-fx-border-color: #f4b400; -fx-border-width: 0 0 3 0;" +
            "-fx-background-image: repeating-linear-gradient(to bottom, transparent, transparent 12px, #FECF4D 12px, #FECF4D 15px);"
        );
        ImageView honeyPot = new ImageView(loadSafe("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);
        Label dashboardTitle = new Label("DASHBOARD");
        dashboardTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));
        dashboardTitle.setTextFill(Color.web("#181818"));
        dashboardHeader.getChildren().addAll(honeyPot, dashboardTitle);

        // Dashboard main body content (center area)
        VBox dashboardBody = new VBox(20);
        dashboardBody.setPadding(new Insets(20));
        dashboardBody.setAlignment(Pos.TOP_LEFT);
        dashboardBody.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        // Date and time labels
        Label dateLabel = new Label();
        dateLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label clockLabel = new Label();
        clockLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText("Time: " + java.time.LocalTime.now().withNano(0).toString());
            dateLabel.setText("Date: " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        // Statistics (scanned today, not yet scanned)
        scannedToday = new Label();
        scannedToday.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 20px; -fx-background-radius: 50px;");
        notScanned = new Label();
        notScanned.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-padding: 20px; -fx-background-radius: 50px;");
        updateStatistics();

        HBox topRow = new HBox(50, scannedToday, notScanned);
        topRow.setAlignment(Pos.CENTER);

        // Live scan stats
        Label scanLabel = new Label("Live Scans:");
        scanLabel.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));
        HBox statsBox = new HBox(30, scanLabel, new Region(), dateLabel, clockLabel);
        HBox.setHgrow(statsBox.getChildren().get(1), Priority.ALWAYS);
        statsBox.setAlignment(Pos.TOP_CENTER);

        // Live check-ins/outs
        liveIns  = new ListView<>();
        liveIns.setPrefHeight(300);
        liveIns.setPrefWidth(300);
        liveOuts = new ListView<>();
        liveOuts.setPrefHeight(300);
        liveOuts.setPrefWidth(300);
        updateLiveScans();
        
        Font checkFont = Font.font("JetBrains Mono", FontWeight.NORMAL, 16); // or "Fira Mono", "Consolas", etc.

     // For Check-Ins
     liveIns.setCellFactory(list -> new ListCell<>() {
         @Override
         protected void updateItem(String item, boolean empty) {
             super.updateItem(item, empty);
             setText(item);
             setFont(checkFont);
             setStyle("-fx-text-fill: #181818;"); // Optional: custom text color
         }
     });
     // For Check-Outs
     liveOuts.setCellFactory(list -> new ListCell<>() {
         @Override
         protected void updateItem(String item, boolean empty) {
             super.updateItem(item, empty);
             setText(item);
             setFont(checkFont);
             setStyle("-fx-text-fill: #181818;");
         }
     });


        // --- Live Check-In / Check-Out Firestore Boxes ---
        Label checkInTitle = new Label("Check-Ins:");
        Label checkOutTitle = new Label("Check-Outs:");
        checkInTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));
        checkOutTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));

        ScrollPane checkInPane = new ScrollPane(checkInList);
        ScrollPane checkOutPane = new ScrollPane(checkOutList);
        checkInPane.setFitToWidth(true);
        checkOutPane.setFitToWidth(true);
        checkInPane.setPrefSize(300, 180);
        checkOutPane.setPrefSize(300, 180);

        VBox checkInBox = new VBox(8, checkInTitle, checkInPane);
        VBox checkOutBox = new VBox(8, checkOutTitle, checkOutPane);
        checkInBox.setStyle("-fx-background-color: #FFC72C; -fx-background-radius: 18; -fx-padding: 18;");
        checkOutBox.setStyle("-fx-background-color: #FFC72C; -fx-background-radius: 18; -fx-padding: 18;");

        HBox livePane = new HBox(50, checkInBox, checkOutBox);
        livePane.setAlignment(Pos.CENTER);
        livePane.setMaxWidth(Double.MAX_VALUE);
      
        Label announcementTitle = new Label("Announcements");
        announcementTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));
        announcementTitle.setAlignment(Pos.CENTER_LEFT);
        announcementTitle.setMaxWidth(Double.MAX_VALUE);

        // Bee icon (use your real path)
        ImageView beeIcon = new ImageView(loadSafe("bee-speaker.png"));
        beeIcon.setFitWidth(64);
        beeIcon.setFitHeight(64);

        // Announcement text label
        Label announcementText = new Label(
                "Dear Taska Zurah Team,\n" +
                "Here’s what’s coming up this week:\n" +
                "• Monday: Staff meeting at 8:00 AM in the Teachers’ Lounge (Room 2)\n" +
                "Please check the Staff Docs section for updated duty rosters and activity guides."
        );
        announcementText.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        announcementText.setStyle("-fx-text-fill: #181818;");
        announcementText.setWrapText(true);

        // Green rounded background HBox
        HBox announcementBox = new HBox(18, beeIcon, announcementText);
        announcementBox.setPadding(new Insets(24, 24, 24, 24));
        announcementBox.setBackground(new Background(new BackgroundFill(
                Color.web("#fff4b4"), new CornerRadii(18), Insets.EMPTY
        )));
        announcementBox.setMaxWidth(Double.MAX_VALUE);
        announcementBox.setAlignment(Pos.CENTER_LEFT);

        VBox announcementArea = new VBox(10, announcementTitle, announcementBox);
        announcementArea.setPadding(new Insets(10, 0, 0, 0));


        // Flexible spacer for visual balance
        Region flexibleSpacer = new Region();
        VBox.setVgrow(flexibleSpacer, Priority.ALWAYS);

        // Add all dashboard body content (not the header!)
        dashboardBody.getChildren().addAll(
            topRow,
            statsBox,
            new Separator(),
            livePane,
            new Separator(),
            flexibleSpacer,
            announcementArea
        );

        // Final layout: header at top, dashboardBody in center
        BorderPane dashboardLayout = new BorderPane();
        dashboardLayout.setTop(dashboardHeader);
        dashboardLayout.setCenter(dashboardBody);

        setMainContent(dashboardLayout);
        dashboardReady = true;

        loadTodayAttendanceRealtime();

        // Optional auto-refresh every 15 seconds:
        //Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
        //    this::loadTodayAttendanceRealtime, 15, 15, TimeUnit.SECONDS);
    }

    public static void updateLiveScans() {
        Platform.runLater(() -> {
            try {
                if (instance == null) return;

                List<AttendanceRecord> records = AttendanceView.getCurrentAttendanceState();
                if (records == null || records.isEmpty()) {
                    instance.checkInList.getChildren().clear();
                    instance.checkOutList.getChildren().clear();
                    Label empty = new Label("No attendance records loaded.");
                    empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
                    instance.checkInList.getChildren().add(empty);
                    return;
                }

                List<String> checkIns = new ArrayList<>();
                List<String> checkOuts = new ArrayList<>();

                for (AttendanceRecord rec : records) {
                    String name = rec.getName();
                    String inTime = rec.getCheckInTime();
                    String outTime = rec.getCheckOutTime();

                    if (rec.isPresent() || (inTime != null && !inTime.isEmpty())) {
                        checkIns.add("✅ " + name + " – " + inTime);
                    }
                    if (rec.isManualCheckOut() || (outTime != null && !outTime.isEmpty())) {
                        checkOuts.add("🏁 " + name + " – " + outTime);
                    }
                }

                instance.checkInList.getChildren().clear();
                instance.checkOutList.getChildren().clear();

                for (String text : checkIns) {
                    Label lbl = new Label(text);
                    lbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
                    instance.checkInList.getChildren().add(lbl);
                }

                for (String text : checkOuts) {
                    Label lbl = new Label(text);
                    lbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
                    instance.checkOutList.getChildren().add(lbl);
                }

                System.out.println("✅ Live lists updated → In=" + checkIns.size() + " | Out=" + checkOuts.size());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void updateStatistics() {
        Platform.runLater(() -> {
            try {
                // 🔹 Use current AttendanceView table state instead of Firestore
                List<AttendanceRecord> records = AttendanceView.getCurrentAttendanceState();

                if (records == null || records.isEmpty()) {
                    scannedToday.setText("Scanned Today: 0");
                    notScanned.setText("Not Yet Scanned: 0");
                    System.out.println("📊 No local attendance data yet.");
                    return;
                }

                int totalChildren = records.size();
                int presentCount = 0;

                for (AttendanceRecord rec : records) {
                    boolean isPresent = rec.isPresent();
                    if (isPresent) presentCount++;
                }

                int absentCount = Math.max(0, totalChildren - presentCount);

                scannedToday.setText("Scanned Today: " + presentCount);
                notScanned.setText("Not Yet Scanned: " + absentCount);

                System.out.println("📊 Dashboard (from local state) >> Present=" + presentCount + " | Absent=" + absentCount);

            } catch (Exception e) {
                scannedToday.setText("Scanned Today: N/A");
                notScanned.setText("Not Yet Scanned: N/A");
                e.printStackTrace();
            }
        });
    }

    // ✅ Real-time Firestore listener for attendance updates
    private void loadTodayAttendanceRealtime() {
        System.out.println("✅ Firestore realtime listener enabled.");

        Firestore fdb = db();
        String todayPrefix = today() + "_";

        fdb.collection("attendance")
            .addSnapshotListener((snap, err) -> {
                if (err != null) {
                    System.err.println("⚠️ Listener error: " + err.getMessage());
                    return;
                }
                if (snap == null) return;

                System.out.println("🔁 Firestore snapshot updated → refreshing Attendance & Dashboard");
                Platform.runLater(FirestoreService::safeRefresh);
            });
    }

    // 🔄 Helper – updates list smoothly without flicker
    private void syncVBoxWithData(VBox box, Map<String, String> data, String prefix) {
        ObservableList<Node> children = FXCollections.observableArrayList(box.getChildren());
        if (children.size() != data.size()) {
            box.getChildren().clear();
            data.forEach((name, time) -> {
                Label lbl = new Label(prefix + " " + name + " – " + time);
                lbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
                box.getChildren().add(lbl);
            });
            return;
        }
        // If same size, verify content differences
        List<String> current = children.stream()
            .filter(n -> n instanceof Label)
            .map(n -> ((Label) n).getText())
            .toList();
        List<String> updated = data.entrySet().stream()
            .map(e -> prefix + " " + e.getKey() + " – " + e.getValue())
            .toList();

        if (!current.equals(updated)) {
            box.getChildren().clear();
            for (String text : updated) {
                Label lbl = new Label(text);
                lbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
                box.getChildren().add(lbl);
            }
        }
    }

    // small formatter
    private static String formatTime(Date d) {
        return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).toString();
    }

    // ✅ Add this
    public static void updateDashboardData() {
        Platform.runLater(() -> {
            if (instance != null) {
                updateStatistics();
                updateLiveScans();
            }
        });
    }
    
    public static void showRegisterForm(String tagId) {
        Stage stage = new Stage();
        stage.setTitle("Register New Child");

        Label nameLabel = new Label("Child's Name:");
        TextField nameField = new TextField();

        Label parentContactLabel = new Label("Parent Contact:");
        TextField parentContactField = new TextField();

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
    String name = nameField.getText().trim();
    String parentContact = parentContactField.getText().trim();

    if (name.isEmpty() || parentContact.isEmpty()) {
        Alert alert = new Alert(Alert.AlertType.WARNING, "Please fill all fields.");
        alert.showAndWait();
    } else {
        try {
            Firestore fdb = db();

            // choose a child_id: max(existing)+1 or timestamp-based if not using numeric IDs
            int nextId = 1;
            List<QueryDocumentSnapshot> kids = fdb.collection("children").get().get().getDocuments();
            for (DocumentSnapshot kd : kids) {
                if (kd.getLong("child_id") != null) {
                    nextId = Math.max(nextId, kd.getLong("child_id").intValue() + 1);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("child_id", nextId);
            data.put("name", name);
            data.put("parent_contact", parentContact);
            data.put("nfc_uid", tagId);

            fdb.collection("children").add(data).get();

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Child registered successfully!");
            alert.showAndWait();
            stage.close();

            Platform.runLater(FirestoreService::safeRefresh);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
});

        // --- Left side: form ---
        VBox form = new VBox(10, nameLabel, nameField, parentContactLabel, parentContactField, saveButton);
        form.setPadding(new Insets(20));
        form.setAlignment(Pos.CENTER);

        // --- Right side: camera preview (local service; safe in static method) ---
        ImageView camView = new ImageView();
        camView.setFitWidth(360);
        camView.setPreserveRatio(true);

        VBox rightPanel = new VBox(12, camView);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPadding(new Insets(20));

        // Use a local FaceCameraService so we don't touch instance fields
        FaceCameraService localCam = new FaceCameraService();
        try {
            localCam.setView(camView);
            localCam.start(0); // 0 = default webcam
        } catch (Throwable t) {
            System.err.println("[RegisterForm] Camera failed to start: " + t);
        }

        // Stop the local camera when this window closes
        stage.setOnCloseRequest(ev -> {
            try { localCam.stop(); } catch (Throwable ignore) {}
        });

        // --- Put both into a BorderPane ---
        BorderPane root = new BorderPane();
        root.setCenter(form);
        root.setRight(rightPanel);

        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.show();
    }

    //UNTUK CHECK IN CHECK OUT
    public static void handleNfcAttendance(String nfcUid) {
        LocalDate today = LocalDate.now();

        try {
            Firestore fdb = db();

            // 🔍 Find child using NFC UID
            QuerySnapshot qs = fdb.collection("children")
                    .whereEqualTo("nfc_uid", nfcUid)
                    .limit(1)
                    .get()
                    .get();

            if (qs.isEmpty()) {
                Platform.runLater(() -> showAlert("⚠ This card is not registered!", Alert.AlertType.WARNING));
                return;
            }

            DocumentSnapshot childDoc = qs.getDocuments().get(0);
            String childName = childDoc.getString("name");

            // 🧠 Check if attendance already exists
            String docId = today() + "_" + nfcUid;
            DocumentReference attRef = fdb.collection("attendance").document(docId);
            DocumentSnapshot att = attRef.get().get();

            Date checkIn = att.exists() ? att.getDate("check_in_time") : null;
            Date checkOut = att.exists() ? att.getDate("check_out_time") : null;

            if (checkIn == null) {
                recordCheckIn(nfcUid, childName);
                Platform.runLater(() -> showAlert("✅ Check-in successful for " + childName, Alert.AlertType.INFORMATION));
            } else if (checkOut == null) {
                recordCheckOut(nfcUid, childName);
                Platform.runLater(() -> showAlert("✅ Check-out successful for " + childName, Alert.AlertType.INFORMATION));
            } else {
                Platform.runLater(() -> showAlert("⚠ Already checked out today for " + childName, Alert.AlertType.WARNING));
            }

            // ✅ Force real-time UI refresh across both screens
            Platform.runLater(FirestoreService::safeRefresh);

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showAlert("❌ Firestore error: " + e.getMessage(), Alert.AlertType.ERROR));
        }
    }
    
    private static void recordCheckIn(String nfcUid, String childName) throws Exception {
        Firestore fdb = db();
        String docId = today() + "_" + nfcUid;

        DocumentSnapshot childDoc = fdb.collection("children")
                .whereEqualTo("nfc_uid", nfcUid)
                .limit(1)
                .get()
                .get()
                .getDocuments()
                .get(0);

        String parentName = childDoc.getString("parentName") != null ? childDoc.getString("parentName") : "-";
        String teacherName = childDoc.getString("teacher_username") != null ? childDoc.getString("teacher_username") : "-";

        Map<String, Object> data = new HashMap<>();
        data.put("date", today());
        data.put("childId", nfcUid);
        data.put("name", childName);
        data.put("parentName", parentName);
        data.put("teacher", teacherName);
        data.put("check_in_time", new Date());
        data.put("checkin_method", "NFC");
        data.put("isPresent", true);
        data.put("manual_in", false);
        data.put("manual_out", false);
        data.put("manualCheckout", false);
        data.put("reason", "Default");

        fdb.collection("attendance").document(docId).set(data, SetOptions.merge()).get();
    }

    private static void recordCheckOut(String nfcUid, String childName) throws Exception {
        Firestore fdb = db();
        String docId = today() + "_" + nfcUid;

        Map<String, Object> data = new HashMap<>();
        data.put("check_out_time", new Date());
        data.put("checkout_method", "NFC");
        data.put("isPresent", false);
        data.put("manualCheckout", false);
        data.put("manual_out", false);

        fdb.collection("attendance").document(docId).set(data, SetOptions.merge()).get();
    }

    public static void showToast(Stage owner, String message) {
        Label toastLabel = new Label(message);
        toastLabel.setStyle("-fx-background-color: #323232; -fx-text-fill: white; -fx-padding: 16px 32px; -fx-background-radius: 32px; -fx-font-size: 20px; -fx-font-weight: bold;");
        toastLabel.setOpacity(0);

        StackPane root = (StackPane) owner.getScene().getRoot();
        root.getChildren().add(toastLabel);

        StackPane.setAlignment(toastLabel, Pos.CENTER);

        Timeline fadeIn = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(toastLabel.opacityProperty(), 0)),
            new KeyFrame(Duration.seconds(0.2), new KeyValue(toastLabel.opacityProperty(), 1))
        );
        Timeline stay = new Timeline(new KeyFrame(Duration.seconds(2)));
        Timeline fadeOut = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(toastLabel.opacityProperty(), 1)),
            new KeyFrame(Duration.seconds(0.5), new KeyValue(toastLabel.opacityProperty(), 0))
        );

        fadeIn.setOnFinished(e -> stay.play());
        stay.setOnFinished(e -> fadeOut.play());
        fadeOut.setOnFinished(e -> root.getChildren().remove(toastLabel));

        fadeIn.play();
    }


    // Helper to show alerts on UI
    private static void showAlert(String msg, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "Error" : "Scan Successful");
        alert.setHeaderText(null);
        alert.setContentText(msg);

        // Make alert non-blocking (not wait for OK)
        alert.show();

        // Auto-close after 3 seconds (3000 ms)
        PauseTransition delay = new PauseTransition(javafx.util.Duration.seconds(3));
        delay.setOnFinished(e -> alert.close());
        delay.play();
    }
    
    @Override
    public void stop() {
        try {
            if (camWin != null) { camWin.stop(); camWin = null; }
        } catch (Exception ignored) {}
    }

    private Image loadSafe(String fileName) {
        try {
            // Try from compiled classpath (bin/nfc)
            java.net.URL url = getClass().getResource(fileName);
            if (url == null) url = getClass().getResource("/nfc/" + fileName);
            if (url != null) return new Image(url.toExternalForm());

            if (url == null) {
                System.err.println("❌ Critical image missing: " + fileName);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Missing Assets");
                    alert.setHeaderText("Critical file missing!");
                    alert.setContentText("Please ensure all images are in /src/nfc/ before launching the app.");
                    alert.showAndWait();
                    Platform.exit();
                });
                return new Image("https://via.placeholder.com/60x60.png?text=Missing");
            }

            // Fallback: look directly in source folders
            File localFile = new File("src/nfc/" + fileName);
            if (localFile.exists()) return new Image(localFile.toURI().toString());

            System.out.println("⚠️ Missing image: " + fileName);
            return new Image("https://via.placeholder.com/60x60.png?text=Missing");
        } catch (Exception e) {
            e.printStackTrace();
            return new Image("https://via.placeholder.com/60x60.png?text=Error");
        }
    }

    // ✅ Shared function so both dashboard + pie chart use identical attendance logic
    public static int[] getTodayStats() throws Exception {
        Firestore fdb = db();

        // Get all children
        List<QueryDocumentSnapshot> allKids = fdb.collection("children").get().get().getDocuments();
        int totalChildren = allKids.size();

        // Get today's attendance
        List<QueryDocumentSnapshot> allDocs = fdb.collection("attendance").get().get().getDocuments();
        List<DocumentSnapshot> todayDocs = new ArrayList<>();
        String todayStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        for (DocumentSnapshot d : allDocs) {
            String dateField = d.getString("date");
            if (dateField == null) continue;
            boolean sameDate = false;
            try {
                SimpleDateFormat parser =
                    new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH);
                Date parsed = parser.parse(dateField.replace(" (Malaysia Time)", ""));
                LocalDate parsedDate = parsed.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                sameDate = parsedDate.equals(LocalDate.now());
            } catch (Exception ex) {
                sameDate = dateField.contains(todayStr) || dateField.equals(todayStr);
            }
            if (sameDate) todayDocs.add(d);
        }

        // Unique per child logic
        Map<String, Boolean> childPresenceMap = new HashMap<>();
        for (DocumentSnapshot d : todayDocs) {
            String childName = d.getString("name"); // ✅ corrected field name
            boolean hasCheckIn = d.contains("check_in_time") && d.get("check_in_time") != null;
            boolean hasManualIn =
                Boolean.TRUE.equals(d.getBoolean("manual_in")) ||
                Boolean.TRUE.equals(d.getBoolean("manualIn")) ||
                Boolean.TRUE.equals(d.getBoolean("Manual In")) ||
                Boolean.TRUE.equals(d.getBoolean("ManualIn")) ||
                Boolean.TRUE.equals(d.getBoolean("isPresent"));

            if (childName != null) {
                boolean alreadyPresent = childPresenceMap.getOrDefault(childName, false);
                childPresenceMap.put(childName, alreadyPresent || hasCheckIn || hasManualIn);
            }
        }

        int presentCount = (int) childPresenceMap.values().stream().filter(v -> v).count();
        int absentCount = Math.max(0, totalChildren - presentCount);
        return new int[]{presentCount, absentCount};
    }
}