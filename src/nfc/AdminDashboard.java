package nfc;

import javafx.application.Platform;

public class AdminDashboard extends javafx.application.Application {
    private static volatile boolean realtimeModeNoticeShown = false;
    private static final String PAGE_DASHBOARD = "dashboard";
    private static final String PAGE_ATTENDANCE = "attendance";
    private static final String PAGE_GENERATE_REPORT = "generate-report";
    private static final String PAGE_DAILY_REPORT = "daily-report";
    private static final String PAGE_MONTHLY_REPORT = "monthly-report";
    private static final String PAGE_CHILDREN = "children";
    private static final String PAGE_STAFF = "staff";
    private static final String PAGE_TEACHERS = "teachers";
    private static final String PAGE_BILLING_LEDGER = "billing-ledger";
    private static final String PAGE_BILLING_POLICY = "billing-policy";
    private static final String PAGE_CASUAL_TRANSIT = "casual-transit";

    private static AdminDashboard instance;
    private static AdminDashboardContentSupport.DashboardWidgets dashboardWidgets;
    private static boolean dashboardReady = false;
    private javafx.scene.layout.StackPane contentPane;
    private String activePageKey;
    private AttendanceView attendanceView;
    private generateReport generateReportView;
    private dailyReport dailyReportView;
    private monthlyReport monthlyReportView;
    private ChildrenView childrenView;
    private StaffManagementView staffManagementView;
    private TeacherManagementView teacherManagementView;
    private BillingLedgerView billingLedgerView;
    private BillingPolicyView billingPolicyView;
    private CasualTransitView casualTransitView;
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
        dashboardWidgets = null;
        dashboardReady = false;
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
        AdminDashboardWindowSupport.applyScreenSizedLaunch(primaryStage);

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
        if (dashboardWidgets == null) {
            dashboardWidgets = AdminDashboardContentSupport.buildDashboard(newDashboardActions());
        }
        setMainContent(PAGE_DASHBOARD, dashboardWidgets.root());
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
                setMainContent(PAGE_ATTENDANCE, getAttendanceView().getRoot());
            }

            @Override
            public void showGenerateReport() {
                setMainContent(PAGE_GENERATE_REPORT, getGenerateReportView().getRoot());
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
                setMainContent(PAGE_CHILDREN, getChildrenView());
            }

            @Override
            public void showStaff() {
                setMainContent(PAGE_STAFF, getStaffManagementView());
            }

            @Override
            public void showTeachers() {
                setMainContent(PAGE_TEACHERS, getTeacherManagementView());
            }

            @Override
            public void showBillingLedger() {
                setMainContent(PAGE_BILLING_LEDGER, getBillingLedgerView());
            }

            @Override
            public void showBillingPolicy() {
                setMainContent(PAGE_BILLING_POLICY, getBillingPolicyView());
            }

            @Override
            public void showCasualTransit() {
                setMainContent(PAGE_CASUAL_TRANSIT, getCasualTransitView());
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
        setMainContent(PAGE_DAILY_REPORT, getDailyReportView().getRoot());
    }

    public void showMonthlyReportView() {
        setMainContent(PAGE_MONTHLY_REPORT, getMonthlyReportView().getRoot());
    }

    private void setMainContent(String pageKey, javafx.scene.Node node) {
        if (PAGE_ATTENDANCE.equals(activePageKey) && !PAGE_ATTENDANCE.equals(pageKey) && attendanceView != null) {
            attendanceView.onHide();
        }
        contentPane.getChildren().setAll(node);
        activePageKey = pageKey;
        if (PAGE_ATTENDANCE.equals(pageKey) && attendanceView != null) {
            attendanceView.onShow();
        }
        if (PAGE_BILLING_LEDGER.equals(pageKey) && billingLedgerView != null) {
            billingLedgerView.onShow();
        }
    }

    private AttendanceView getAttendanceView() {
        if (attendanceView == null) {
            attendanceView = new AttendanceView();
        }
        return attendanceView;
    }

    private generateReport getGenerateReportView() {
        if (generateReportView == null) {
            generateReportView = new generateReport();
        }
        return generateReportView;
    }

    private dailyReport getDailyReportView() {
        if (dailyReportView == null) {
            dailyReportView = new dailyReport();
        }
        return dailyReportView;
    }

    private monthlyReport getMonthlyReportView() {
        if (monthlyReportView == null) {
            monthlyReportView = new monthlyReport();
        }
        return monthlyReportView;
    }

    private ChildrenView getChildrenView() {
        if (childrenView == null) {
            childrenView = new ChildrenView();
        }
        return childrenView;
    }

    private StaffManagementView getStaffManagementView() {
        if (staffManagementView == null) {
            staffManagementView = new StaffManagementView();
        }
        return staffManagementView;
    }

    private TeacherManagementView getTeacherManagementView() {
        if (teacherManagementView == null) {
            teacherManagementView = new TeacherManagementView();
        }
        return teacherManagementView;
    }

    private BillingLedgerView getBillingLedgerView() {
        if (billingLedgerView == null) {
            billingLedgerView = new BillingLedgerView();
        }
        return billingLedgerView;
    }

    private BillingPolicyView getBillingPolicyView() {
        if (billingPolicyView == null) {
            billingPolicyView = new BillingPolicyView();
        }
        return billingPolicyView;
    }

    private CasualTransitView getCasualTransitView() {
        if (casualTransitView == null) {
            casualTransitView = new CasualTransitView();
        }
        return casualTransitView;
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

    public static void refreshAttendancePageIfVisible() {
        AdminDashboard current = instance;
        if (current == null) {
            return;
        }

        Platform.runLater(() -> {
            if (!PAGE_ATTENDANCE.equals(current.activePageKey)) {
                return;
            }

            if (current.attendanceView != null) {
                current.attendanceView.onHide();
            }
            current.attendanceView = new AttendanceView();
            current.setMainContent(PAGE_ATTENDANCE, current.attendanceView.getRoot());
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
                AdminDashboard current = instance;
                if (current != null) {
                    BillingLedgerView view = current.getBillingLedgerView();
                    view.showAgeReviewView();
                    current.setMainContent(PAGE_BILLING_LEDGER, view);
                }
            }

            @Override
            public void openOvertimeReviewLedger() {
                AdminDashboard current = instance;
                if (current != null) {
                    BillingLedgerView view = current.getBillingLedgerView();
                    view.showOvertimeReviewView();
                    current.setMainContent(PAGE_BILLING_LEDGER, view);
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