package nfc;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AdminDashboard extends Application {

    private static volatile boolean missingAssetsWarningShown = false;
    private static volatile boolean realtimeModeNoticeShown = false;

    private static AdminDashboard instance;
    private static Label scannedToday;
    private static Label notScanned;
    private static Label billingAgeReview;
    private static Label billingOvertimeReview;
    private static ListView<String> liveIns, liveOuts;
    private static boolean dashboardReady = false;
    private StackPane contentPane;
    private static NFCReader reader;
    private static Thread nfcReaderThread;
    private double xOffset = 0;
    private double yOffset = 0;

    private final VBox checkInList = new VBox(6);
    private final VBox checkOutList = new VBox(6);

    private static volatile long lastDashboardFirestoreRefreshMs = 0;

    private static String today() { return java.time.LocalDate.now().toString(); } // "YYYY-MM-DD"
    private static void logError(String context, Exception error) {
        System.err.println("AdminDashboard: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
    }

    private static FirestoreRestClient rest() {
        return FirestoreRest.forCurrentUser();
    }
    
    public static AdminDashboard getInstance() { 
    	return instance; }

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

        HBox bodyLayout = createBodyLayout();

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
            Platform.exit(); // triggers your @Override stop()
        });

        // 4) Assemble the top bar
        HBox topBar = new HBox(10, title, spacer, minimizeButton, maximizeButton, closeButton);
        topBar.setPadding(new Insets(5, 10, 5, 10));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #30cd30;");

        return topBar;
    }


    private HBox createBodyLayout() {
        HBox bodyLayout = new HBox();
        bodyLayout.setStyle("-fx-background-color: transparent;");

        VBox sidebar = createSidebar();
        contentPane = new StackPane();
        contentPane.setStyle("-fx-background-color: transparent;");

        loadDashboardContent();

        bodyLayout.getChildren().addAll(sidebar, contentPane);
        HBox.setHgrow(contentPane, Priority.ALWAYS);
        return bodyLayout;
    }

    private VBox createSidebar() {
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

        if (pic != null && !pic.isBlank()) {
            String v = pic.trim();
            if (ImageCache.isRemoteUrl(v)) {
                profileView.setImage(ImageCache.loadCachedOrRemote(v, 130, 130));
                // Populate disk cache for future runs
                ImageCache.prefetch(v);
            } else {
                File imgFile = new File("profile_pics", v);
                if (imgFile.exists()) {
                    profileView.setImage(new Image(imgFile.toURI().toString()));
                } else {
                    profileView.setImage(ImageLoader.loadSafe("default_user.png"));
                }
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

        Button btnBillingPolicy = createNavButton("Billing Policy");
        btnBillingPolicy.setStyle(
            "-fx-background-color: #FFCB3C;" +
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 28px;"
        );
        btnBillingPolicy.setOnAction(e -> {
            BillingPolicyView view = new BillingPolicyView();
            setMainContent(view);
        });

        Button btnBillingLedger = createNavButton("Billing Ledger");
        btnBillingLedger.setStyle(
            "-fx-background-color: #FFCB3C;" +
            "-fx-font-size: 16px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 28px;"
        );
        btnBillingLedger.setOnAction(e -> {
            BillingLedgerView view = new BillingLedgerView();
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
           
            // Open login window
            LoginView loginView = new LoginView();
            Stage loginStage = new Stage();
            try {
                loginView.start(loginStage);
            } catch (Exception ex) {
                logError("failed to open LoginView", ex);
            }
            
            // Close the current (dashboard) window
            Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            currentStage.close();
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        

        // Teacher role: hide admin-only sections
        if (UserSession.isTeacher() && !UserSession.isAdmin()) {
            btnChildren.setVisible(false);
            btnChildren.setManaged(false);
            btnStaff.setVisible(false);
            btnStaff.setManaged(false);
            btnTeachers.setVisible(false);
            btnTeachers.setManaged(false);
            btnBillingPolicy.setVisible(false);
            btnBillingPolicy.setManaged(false);
            btnBillingLedger.setVisible(false);
            btnBillingLedger.setManaged(false);
            btnGenerateReport.setVisible(false);
            btnGenerateReport.setManaged(false);
            btnDaily.setVisible(false);
            btnDaily.setManaged(false);
            btnMonthly.setVisible(false);
            btnMonthly.setManaged(false);
        }
        
        sidebar.getChildren().addAll(
        	    profileBox,
        	    btnDashboard,
        	    btnAttendance,
        	    attendanceSubMenu,
        	    btnChildren,
        	    btnStaff,
                btnTeachers,
                btnBillingLedger,
                btnBillingPolicy,
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
                btnBillingLedger,
                btnBillingPolicy,
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
                    Thread.currentThread().interrupt();
                    logError("interrupted while stopping NFC reader", ex);
                }
            }
        }

        String portName = NFCReader.resolveConfiguredPortName();
        if (portName == null) {
            System.out.println("ℹ️ NFC reader disabled by configuration.");
            return;
        }

        if (!NFCReader.isPortAvailable(portName)) {
            System.out.println("ℹ️ NFC reader not started. Port " + portName + " is unavailable. Available ports: " + NFCReader.availablePortsSummary());
            return;
        }

        reader = new NFCReader(portName);
        nfcReaderThread = new Thread(reader);
        nfcReaderThread.setName("taska-nfc-reader");
        nfcReaderThread.setDaemon(true);
        nfcReaderThread.start();
    }



    private void loadDashboardContent() {
        // Dashboard Header Bar (full-width)
        HBox dashboardHeader = new HBox(18);
        dashboardHeader.setAlignment(Pos.CENTER_LEFT);
        dashboardHeader.setPrefHeight(70);
        dashboardHeader.setMaxWidth(Double.MAX_VALUE); // Stretch to parent
        dashboardHeader.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;" +
            "-fx-background-insets: 0, 0 0 3 0;" +
            "-fx-background-radius: 0, 0;"
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
        billingAgeReview = new Label("Age Review: 0 fam / 0 inv");
        billingAgeReview.setStyle("-fx-background-color: #ffdda8; -fx-text-fill: #7a4200; -fx-padding: 20px; -fx-background-radius: 50px;");
        billingOvertimeReview = new Label("OT Review: 0 fam / 0 inv");
        billingOvertimeReview.setStyle("-fx-background-color: #fff2a8; -fx-text-fill: #7a5a00; -fx-padding: 20px; -fx-background-radius: 50px;");
        configureDashboardBillingBadge(
            billingAgeReview,
            "Open Billing Ledger with age-review invoices first.",
            () -> {
                BillingLedgerView view = new BillingLedgerView();
                view.showAgeReviewView();
                setMainContent(view);
            }
        );
        configureDashboardBillingBadge(
            billingOvertimeReview,
            "Open Billing Ledger with overtime-review invoices first.",
            () -> {
                BillingLedgerView view = new BillingLedgerView();
                view.showOvertimeReviewView();
                setMainContent(view);
            }
        );
        updateStatistics();

        HBox topRow = new HBox(28, scannedToday, notScanned, billingAgeReview, billingOvertimeReview);
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
    liveIns.setCellFactory(list -> new ListCell<String>() {
         @Override
         protected void updateItem(String item, boolean empty) {
             super.updateItem(item, empty);
             setText(item);
             setFont(checkFont);
             setStyle("-fx-text-fill: #181818;"); // Optional: custom text color
         }
     });
     // For Check-Outs
    liveOuts.setCellFactory(list -> new ListCell<String>() {
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
        refreshDashboardFromFirestoreAsync();
    }

    public static void updateStatistics() {
        refreshDashboardFromFirestoreAsync();
    }

    private static void refreshDashboardFromFirestoreAsync() {
        if (instance == null || scannedToday == null || notScanned == null) return;
        if (!UserSession.isLoggedIn()) return;

        long now = System.currentTimeMillis();
        if (now - lastDashboardFirestoreRefreshMs < 1200) return;
        lastDashboardFirestoreRefreshMs = now;

        CompletableFuture.runAsync(() -> {
            try {
                FirestoreRestClient client = FirestoreRest.forCurrentUser();

                List<FsDocument> childDocs = client.listDocuments("children");
                List<FsDocument> parentDocs = client.listDocuments("parents");
                int totalChildren = 0;
                Map<Long, String> childIdToNfcUid = new HashMap<>();
                Map<String, String> nfcUidToName = new HashMap<>();
                Set<String> ageReviewParents = new HashSet<>();
                Set<String> overtimeReviewParents = new HashSet<>();
                int ageReviewInvoices = 0;
                int overtimeReviewInvoices = 0;
                for (FsDocument c : childDocs) {
                    String migratedTo = c.getString("migratedToChildId");
                    if (migratedTo != null && !migratedTo.isBlank()) {
                        continue;
                    }

                    totalChildren++;

                    Long cid = c.getLong("child_id");
                    String uid = c.getString("nfc_uid");
                    String nm = c.getString("name");
                    if (cid != null && uid != null && !uid.isBlank()) {
                        childIdToNfcUid.put(cid, uid);
                    }
                    if (uid != null && nm != null) {
                        nfcUidToName.put(uid, nm);
                    }
                }

                for (FsDocument parent : parentDocs) {
                    String parentId = parent.getId();
                    if (parentId == null || parentId.isBlank()) {
                        continue;
                    }
                    List<FsDocument> invoices = client.listSubcollectionDocuments("parents", parentId, "invoices");
                    for (FsDocument invoice : invoices) {
                        Object billingMeta = invoice.get("billingMeta");
                        if (!(billingMeta instanceof Map<?, ?>)) {
                            continue;
                        }
                        Map<?, ?> billingMetaMap = (Map<?, ?>) billingMeta;
                        Object reviewRaw = billingMetaMap.get("managementReviewRecommended");
                        boolean managementReview = reviewRaw instanceof Boolean
                            ? (Boolean) reviewRaw
                            : "true".equalsIgnoreCase(String.valueOf(reviewRaw));
                        if (!managementReview) {
                            continue;
                        }

                        Object ageRaw = billingMetaMap.get("ageOutOfPolicy");
                        boolean ageReview = ageRaw instanceof Boolean
                            ? (Boolean) ageRaw
                            : "true".equalsIgnoreCase(String.valueOf(ageRaw));
                        if (ageReview) {
                            ageReviewInvoices++;
                            ageReviewParents.add(parentId);
                        } else {
                            overtimeReviewInvoices++;
                            overtimeReviewParents.add(parentId);
                        }
                    }
                }

                java.util.Date startOfDay = java.util.Date.from(
                    java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                );
                List<FsDocument> docs = client.queryWhereEqual("attendance", "date", startOfDay);

                // Unique scanned children for the day
                Set<String> scannedChildren = new HashSet<>();

                List<Map.Entry<String, Date>> checkIns = new ArrayList<>();
                List<Map.Entry<String, Date>> checkOuts = new ArrayList<>();

                for (FsDocument doc : docs) {
                    Date in = doc.getDate("check_in_time");
                    Date out = doc.getDate("check_out_time");

                    Boolean presentFlag = doc.getBoolean("isPresent");
                    if (presentFlag == null) presentFlag = doc.getBoolean("is_present");

                    String childKey = doc.getString("childId");
                    if (childKey == null || childKey.isBlank()) {
                        Long numericChildId = doc.getLong("child_id");
                        if (numericChildId != null) {
                            childKey = childIdToNfcUid.getOrDefault(numericChildId, String.valueOf(numericChildId));
                        } else {
                            String id = doc.getId();
                            if (id != null && id.contains("_")) {
                                childKey = id.substring(id.indexOf('_') + 1);
                            } else {
                                childKey = id;
                            }
                        }
                    }

                    String name = doc.getString("name");
                    if ((name == null || name.isBlank()) && childKey != null) {
                        name = nfcUidToName.getOrDefault(childKey, childKey);
                    }

                    boolean scanned = (in != null) || Boolean.TRUE.equals(presentFlag);
                    if (scanned) scannedChildren.add(childKey);

                    if (in != null) checkIns.add(Map.entry(name, in));
                    if (out != null) checkOuts.add(Map.entry(name, out));
                }

                checkIns.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                checkOuts.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                int presentCount = scannedChildren.size();
                int absentCount = Math.max(0, totalChildren - presentCount);
                final int finalAgeReviewInvoices = ageReviewInvoices;
                final int finalOvertimeReviewInvoices = overtimeReviewInvoices;
                final int finalAgeReviewFamilies = ageReviewParents.size();
                final int finalOvertimeReviewFamilies = overtimeReviewParents.size();

                SimpleDateFormat tf = new SimpleDateFormat("hh:mm a");
                List<String> inLines = checkIns.stream()
                        .map(e -> "✅ " + e.getKey() + " – " + tf.format(e.getValue()))
                        .toList();
                List<String> outLines = checkOuts.stream()
                        .map(e -> "🏁 " + e.getKey() + " – " + tf.format(e.getValue()))
                        .toList();

                Platform.runLater(() -> {
                    scannedToday.setText("Scanned Today: " + presentCount);
                    notScanned.setText("Not Yet Scanned: " + absentCount);
                    if (billingAgeReview != null) {
                        billingAgeReview.setText("Age Review: " + finalAgeReviewFamilies + " fam / " + finalAgeReviewInvoices + " inv");
                    }
                    if (billingOvertimeReview != null) {
                        billingOvertimeReview.setText("OT Review: " + finalOvertimeReviewFamilies + " fam / " + finalOvertimeReviewInvoices + " inv");
                    }

                    instance.checkInList.getChildren().clear();
                    instance.checkOutList.getChildren().clear();

                    if (inLines.isEmpty()) {
                        Label empty = new Label("No check-ins yet today.");
                        empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
                        instance.checkInList.getChildren().add(empty);
                    } else {
                        for (String text : inLines) {
                            Label lbl = new Label(text);
                            lbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
                            instance.checkInList.getChildren().add(lbl);
                        }
                    }

                    if (outLines.isEmpty()) {
                        Label empty = new Label("No check-outs yet today.");
                        empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
                        instance.checkOutList.getChildren().add(empty);
                    } else {
                        for (String text : outLines) {
                            Label lbl = new Label(text);
                            lbl.setStyle("-fx-font-size: 15px; -fx-text-fill: #2b3b2b;");
                            instance.checkOutList.getChildren().add(lbl);
                        }
                    }
                });

                System.out.println("✅ Dashboard refreshed (Firestore) → In=" + inLines.size() + " | Out=" + outLines.size());
            } catch (IOException | InterruptedException e) {
                logError("dashboard refresh failed", e);
            }
        });
    }

    // ✅ Real-time Firestore listener for attendance updates
    private void loadTodayAttendanceRealtime() {
        // Realtime listeners were previously implemented via the Admin SDK.
        // In the distributed build (no service account), we rely on the existing UI timer refresh.
        if (!realtimeModeNoticeShown) {
            realtimeModeNoticeShown = true;
            System.out.println("ℹ️ Realtime listener disabled (REST mode). Using periodic refresh.");
        }
    }

    private void configureDashboardBillingBadge(Label label, String tooltipText, Runnable action) {
        label.setCursor(javafx.scene.Cursor.HAND);
        label.setTooltip(new Tooltip(tooltipText));
        label.setOnMouseClicked(event -> action.run());
    }

    // ✅ Add this
    public static void updateDashboardData() {
        Platform.runLater(() -> {
            if (instance != null) {
                refreshDashboardFromFirestoreAsync();
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
            FirestoreRestClient client = rest();

            // choose a child_id: max(existing)+1 (best-effort)
            int nextId = 1;
            for (FsDocument kid : client.listDocuments("children")) {
                Long v = kid.getLong("child_id");
                if (v != null) nextId = Math.max(nextId, Math.toIntExact(v + 1L));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("child_id", nextId);
            data.put("name", name);
            data.put("parent_contact", parentContact);
            data.put("nfc_uid", tagId == null ? "" : tagId.trim().toUpperCase());

            client.addDocumentAutoId("children", data);

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Child registered successfully!");
            alert.showAndWait();
            stage.close();

            Platform.runLater(FirestoreService::safeRefresh);
        } catch (IOException | InterruptedException ex) {
            logError("child registration failed", ex);
        }
    }
});

        // --- Left side: form ---
        VBox form = new VBox(10, nameLabel, nameField, parentContactLabel, parentContactField, saveButton);
        form.setPadding(new Insets(20));
        form.setAlignment(Pos.CENTER);

        // --- Put form into a BorderPane ---
        BorderPane root = new BorderPane();
        root.setCenter(form);

        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.show();
    }

    //UNTUK CHECK IN CHECK OUT
    public static void handleNfcAttendance(String nfcUid) {
        String uid = nfcUid == null ? "" : nfcUid.trim().toUpperCase();
        if (uid.isEmpty()) {
            Platform.runLater(() -> showAlert("⚠ Invalid NFC UID", Alert.AlertType.WARNING));
            return;
        }
        try {
            FirestoreRestClient client = rest();

            // 🔍 Find child using NFC UID
            List<FsDocument> hits = client.queryWhereEqual("children", "nfc_uid", uid);
            if (hits == null || hits.isEmpty()) {
                Platform.runLater(() -> showAlert("⚠ This card is not registered!", Alert.AlertType.WARNING));
                return;
            }

            // Prefer the active child doc (skip redirect docs left behind by migration).
            FsDocument childDoc = null;
            for (FsDocument d : hits) {
                String migratedTo = d.getString("migratedToChildId");
                if (migratedTo == null || migratedTo.trim().isEmpty()) {
                    childDoc = d;
                    break;
                }
            }
            if (childDoc == null) {
                FsDocument legacy = hits.get(0);
                String migratedTo = legacy.getString("migratedToChildId");
                if (migratedTo != null && !migratedTo.trim().isEmpty()) {
                    childDoc = client.getDocument("children", migratedTo.trim());
                }
            }
            if (childDoc == null) {
                Platform.runLater(() -> showAlert("⚠ This card is not registered!", Alert.AlertType.WARNING));
                return;
            }

            String childId = childDoc.getId();
            String childName = childDoc.getString("name");

            // 🧠 Check if attendance already exists
            String docId = today() + "_" + childId;
            FsDocument att = client.getDocument("attendance", docId);

            Date checkIn = att == null ? null : att.getDate("check_in_time");
            Date checkOut = att == null ? null : att.getDate("check_out_time");

            if (checkIn == null) {
                recordCheckIn(client, childId, uid, childName);
                Platform.runLater(() -> showAlert("✅ Check-in successful for " + childName, Alert.AlertType.INFORMATION));
            } else if (checkOut == null) {
                recordCheckOut(client, childId);
                Platform.runLater(() -> showAlert("✅ Check-out successful for " + childName, Alert.AlertType.INFORMATION));
            } else {
                Platform.runLater(() -> showAlert("⚠ Already checked out today for " + childName, Alert.AlertType.WARNING));
            }

            // ✅ Force real-time UI refresh across both screens
            Platform.runLater(FirestoreService::safeRefresh);

        } catch (Exception e) {
            logError("handle NFC attendance", e);
            Platform.runLater(() -> showAlert("❌ Firestore error: " + e.getMessage(), Alert.AlertType.ERROR));
        }
    }
    
    private static void recordCheckIn(FirestoreRestClient client, String childId, String nfcUid, String childName) throws Exception {
        String docId = today() + "_" + childId;

        FsDocument childDoc = client.getDocument("children", childId);
        String parentName = (childDoc != null && childDoc.getString("parentName") != null)
            ? childDoc.getString("parentName")
            : "-";

        Map<String, Object> data = new HashMap<>();
        data.put("date", today());
        data.put("childId", childId);
        data.put("childRef", new FirestoreRestClient.ReferenceValue(client.referenceValue("children", childId)));
        data.put("nfc_uid", nfcUid);
        data.put("name", childName);
        data.put("parentName", parentName);
        // No designated teacher per child.
        data.put("teacher", "");
        data.put("check_in_time", new Date());
        data.put("checkin_method", "NFC");
        data.put("isPresent", true);
        data.put("manual_in", false);
        data.put("manual_out", false);
        data.put("manualCheckout", false);
        data.put("reason", "Default");

        client.patchDocumentMerge("attendance", docId, data);
    }

    private static void recordCheckOut(FirestoreRestClient client, String childId) throws Exception {
        String docId = today() + "_" + childId;

        Map<String, Object> data = new HashMap<>();
        data.put("check_out_time", new Date());
        data.put("checkout_method", "NFC");
        data.put("isPresent", false);
        data.put("manualCheckout", false);
        data.put("manual_out", false);

        client.patchDocumentMerge("attendance", docId, data);
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
    }

    private Image loadSafe(String fileName) {
        try {
            // 1) Try classpath (preferred for packaged app)
            java.net.URL url = getClass().getResource("/nfc/" + fileName);
            if (url != null) return new Image(url.toExternalForm());

            // 2) Dev fallback: load directly from src folder
            File localFile = new File("src/nfc/" + fileName);
            if (localFile.exists()) return new Image(localFile.toURI().toString());

            // Missing asset: warn once, but do not crash the app
            System.err.println("⚠️ Missing image: " + fileName);
            if (!missingAssetsWarningShown) {
                missingAssetsWarningShown = true;
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Missing Assets");
                    alert.setHeaderText("Some images could not be loaded");
                    alert.setContentText(
                        "Some UI images are missing from the classpath. "
                        + "If you are running from source, rebuild to copy assets into bin/nfc."
                    );
                    alert.show();

                    PauseTransition delay = new PauseTransition(javafx.util.Duration.seconds(4));
                    delay.setOnFinished(e -> alert.close());
                    delay.play();
                });
            }

            return new Image("https://via.placeholder.com/60x60.png?text=Missing");
        } catch (Exception e) {
            logError("loadSafe image fallback", e);
            return new Image("https://via.placeholder.com/60x60.png?text=Error");
        }
    }

    // ✅ Shared function so both dashboard + pie chart use identical attendance logic
    public static int[] getTodayStats() throws Exception {
        FirestoreRestClient client = rest();

        // Get all children
        int totalChildren = client.listDocuments("children").size();

        // Get today's attendance
        List<FsDocument> todayDocs = client.queryWhereEqual("attendance", "date", today());

        // Unique per child logic
        Map<String, Boolean> childPresenceMap = new HashMap<>();
        for (FsDocument d : todayDocs) {
            String childName = d.getString("name");
            boolean hasCheckIn = d.getDate("check_in_time") != null;
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