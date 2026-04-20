package nfc;

import javafx.application.Platform;

public class AdminDashboard extends javafx.application.Application {
    private static volatile boolean realtimeModeNoticeShown = false;

    private static AdminDashboard instance;
    private static AdminDashboardContentSupport.DashboardWidgets dashboardWidgets;
    private static boolean dashboardReady = false;
    private javafx.scene.layout.StackPane contentPane;
    private static NFCReader reader;
    private static Thread nfcReaderThread;

    private static void logError(String context, Exception error) {
        System.err.println("AdminDashboard: " + context + " - " + error.getMessage());
        error.printStackTrace(System.err);
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
    public void start(javafx.stage.Stage primaryStage) {
        primaryStage.setTitle("Admin Dashboard - Taska Attendance");

        javafx.scene.layout.VBox mainLayout = new javafx.scene.layout.VBox();
        mainLayout.setSpacing(0);
        mainLayout.setStyle(
        		"-fx-background-color: linear-gradient(to bottom right, #2E8B57 0%, #247a4b 100%);" // dark green outside
        	);

        javafx.scene.layout.HBox topBar = AdminDashboardWindowSupport.createTopBar(primaryStage, this::closeApplication);

        javafx.scene.layout.HBox bodyLayout = createBodyLayout();

        mainLayout.getChildren().addAll(topBar, bodyLayout);
        javafx.scene.layout.VBox.setVgrow(bodyLayout, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(mainLayout, 1000, 800);
        java.net.URL css = getClass().getResource("style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        else System.out.println("⚠️ Missing: style.css");

        primaryStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> closeApplication());

        primaryStage.show();

        startAutoRefresh();
        startNFCReader();
    }
    
    private void startAutoRefresh() {
        AdminDashboardWindowSupport.startAutoRefresh(() -> {
            if (dashboardWidgets != null) {
                updateStatistics();
                updateLiveScans();
            }
        });
    }

    private void loadDashboardContent() {
        dashboardWidgets = AdminDashboardContentSupport.buildDashboard(newDashboardActions());
        setMainContent(dashboardWidgets.root());
        dashboardReady = true;

        updateStatistics();
        loadTodayAttendanceRealtime();

        // Optional auto-refresh every 15 seconds:
        //Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
        //    this::loadTodayAttendanceRealtime, 15, 15, TimeUnit.SECONDS);
    }

    private javafx.scene.layout.HBox createBodyLayout() {
        javafx.scene.layout.HBox bodyLayout = new javafx.scene.layout.HBox();
        bodyLayout.setStyle("-fx-background-color: transparent;");
        bodyLayout.getStyleClass().add("admin-dashboard-body");

        javafx.scene.layout.VBox sidebar = createSidebar();
        contentPane = new javafx.scene.layout.StackPane();
        contentPane.setStyle("-fx-background-color: transparent;");
        contentPane.getStyleClass().add("admin-content-pane");

        loadDashboardContent();

        bodyLayout.getChildren().addAll(sidebar, contentPane);
        javafx.scene.layout.HBox.setHgrow(contentPane, javafx.scene.layout.Priority.ALWAYS);
        return bodyLayout;
    }

    private javafx.scene.layout.VBox createSidebar() {
        return AdminDashboardSidebarSupport.createSidebar(new AdminDashboardSidebarSupport.SidebarActions() {
            @Override
            public void showDashboard() {
                loadDashboardContent();
            }

            @Override
            public void showAttendance() {
                AttendanceView view = new AttendanceView();
                setMainContent(view.getRoot());
            }

            @Override
            public void showGenerateReport() {
                generateReport reportView = new generateReport();
                setMainContent(reportView.getRoot());
            }

            @Override
            public void showDailyReport() {
                showDailyReportView();
            }

            @Override
            public void showMonthlyReport() {
                showMonthlyReportView();
            }

            @Override
            public void showChildren() {
                setMainContent(new ChildrenView());
            }

            @Override
            public void showStaff() {
                setMainContent(new StaffManagementView());
            }

            @Override
            public void showTeachers() {
                setMainContent(new TeacherManagementView());
            }

            @Override
            public void showBillingLedger() {
                setMainContent(new BillingLedgerView());
            }

            @Override
            public void showBillingPolicy() {
                setMainContent(new BillingPolicyView());
            }

            @Override
            public void showCasualTransit() {
                setMainContent(new CasualTransitView());
            }

            @Override
            public void logout(javafx.stage.Stage currentStage) {
                shutdownReader();

                LoginView loginView = new LoginView();
                javafx.stage.Stage loginStage = new javafx.stage.Stage();
                try {
                    loginView.start(loginStage);
                } catch (Exception ex) {
                    logError("failed to open LoginView", ex);
                }
                currentStage.close();
            }
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

    private void setMainContent(javafx.scene.Node node) {
        contentPane.getChildren().setAll(node);
    }

    private void startNFCReader() {
        AdminDashboardNfcLifecycleSupport.ReaderSession session = AdminDashboardNfcLifecycleSupport.restartReader(reader, nfcReaderThread, AdminDashboard::logError);
        reader = session.reader;
        nfcReaderThread = session.thread;
    }

    public static void updateLiveScans() {
        refreshDashboardFromFirestoreAsync();
    }

    public static void updateStatistics() {
        refreshDashboardFromFirestoreAsync();
    }

    private static void refreshDashboardFromFirestoreAsync() {
        AdminDashboardContentSupport.refreshDashboardFromFirestoreAsync(dashboardWidgets, newDashboardActions());
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

    public static void updateDashboardData() {
        Platform.runLater(() -> {
            if (dashboardWidgets != null) {
                refreshDashboardFromFirestoreAsync();
            }
        });
    }

    private static AdminDashboardContentSupport.DashboardActions newDashboardActions() {
        return new AdminDashboardContentSupport.DashboardActions() {
            @Override
            public javafx.scene.image.Image loadImage(String fileName) {
                return AdminDashboardUtilitySupport.loadSafe(AdminDashboard.class, fileName, AdminDashboard::logError);
            }

            @Override
            public void openAgeReviewLedger() {
                BillingLedgerView view = new BillingLedgerView();
                view.showAgeReviewView();
                if (instance != null) {
                    instance.setMainContent(view);
                }
            }

            @Override
            public void openOvertimeReviewLedger() {
                BillingLedgerView view = new BillingLedgerView();
                view.showOvertimeReviewView();
                if (instance != null) {
                    instance.setMainContent(view);
                }
            }

            @Override
            public void logError(String context, Exception error) {
                AdminDashboard.logError(context, error);
            }
        };
    }
    
    public static void showRegisterForm(String tagId) {
        AdminDashboardUtilitySupport.showRegisterForm(tagId, AdminDashboard::logError);
    }

    //UNTUK CHECK IN CHECK OUT
    public static void handleNfcAttendance(String nfcUid) {
        AdminDashboardUtilitySupport.handleNfcAttendance(nfcUid, AdminDashboard::logError);
    }

    public static void showToast(javafx.stage.Stage owner, String message) {
        AdminDashboardUtilitySupport.showToast(owner, message);
    }

    private void closeApplication() {
        shutdownReader();
        Platform.exit();
    }

    private static void shutdownReader() {
        System.out.println("👋 Closing application, releasing Serial Port...");
        AdminDashboardNfcLifecycleSupport.shutdownReader(reader, nfcReaderThread, AdminDashboard::logError);
        reader = null;
        nfcReaderThread = null;
    }
    
    @Override
    public void stop() {
    }

    // ✅ Shared function so both dashboard + pie chart use identical attendance logic
    public static int[] getTodayStats() throws Exception {
        return AdminDashboardUtilitySupport.getTodayStats();
    }
}