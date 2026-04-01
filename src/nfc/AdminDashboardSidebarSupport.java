package nfc;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

final class AdminDashboardSidebarSupport {
    private AdminDashboardSidebarSupport() {
    }

    interface SidebarActions {
        void showDashboard();
        void showAttendance();
        void showGenerateReport();
        void showDailyReport();
        void showMonthlyReport();
        void showChildren();
        void showStaff();
        void showTeachers();
        void showBillingLedger();
        void showBillingPolicy();
        void showCasualTransit();
        void logout(Stage currentStage);
    }

    static VBox createSidebar(SidebarActions actions) {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setMaxWidth(240);
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setStyle("-fx-background-color: #30af4a;");

        VBox profileBox = AdminDashboardSidebarProfileSupport.createProfileBox();
        ImageView profileView = (ImageView) profileBox.getChildren().get(0);
        Label nameLabel = (Label) ((javafx.scene.layout.HBox) profileBox.getChildren().get(1)).getChildren().get(0);

        Button btnDashboard = createMainNavButton("Dashboard");
        Button btnAttendance = createMainNavButton("Attendance");

        VBox attendanceSubMenu = new VBox(10);
        attendanceSubMenu.setPadding(new Insets(0, 0, 0, 0));
        attendanceSubMenu.setAlignment(Pos.CENTER);

        Button btnGenerateReport = createReportButton("Generate Report", 38, 170);
        Button btnDaily = createReportButton("Daily", 34, 150);
        Button btnMonthly = createReportButton("Monthly", 34, 150);
        attendanceSubMenu.getChildren().addAll(btnGenerateReport, btnDaily, btnMonthly);

        Button btnChildren = createMainNavButton("Children & Parents");
        Button btnStaff = createMainNavButton("Admins");
        Button btnTeachers = createMainNavButton("Teachers");
        Button btnBillingPolicy = createMainNavButton("Billing Policy");
        Button btnBillingLedger = createMainNavButton("Billing Ledger");
        Button btnCasualTransit = createMainNavButton("Casual Transit");
        Button btnLogout = createNavButton("Logout");
        btnLogout.setStyle("-fx-background-color: red;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        btnLogout.setMinHeight(30);
        btnLogout.setPrefHeight(Region.USE_COMPUTED_SIZE);
        btnLogout.setMaxWidth(Double.MAX_VALUE);
        btnLogout.setWrapText(true);
        btnLogout.setTextOverrun(OverrunStyle.CLIP);

        btnDashboard.setOnAction(e -> actions.showDashboard());
        btnAttendance.setOnAction(e -> actions.showAttendance());
        btnGenerateReport.setOnAction(e -> actions.showGenerateReport());
        btnDaily.setOnAction(e -> actions.showDailyReport());
        btnMonthly.setOnAction(e -> actions.showMonthlyReport());
        btnChildren.setOnAction(e -> actions.showChildren());
        btnStaff.setOnAction(e -> actions.showStaff());
        btnTeachers.setOnAction(e -> actions.showTeachers());
        btnBillingPolicy.setOnAction(e -> actions.showBillingPolicy());
        btnBillingLedger.setOnAction(e -> actions.showBillingLedger());
        btnCasualTransit.setOnAction(e -> actions.showCasualTransit());
        btnLogout.setOnAction(e -> actions.logout((Stage) btnLogout.getScene().getWindow()));

        if (UserSession.isTeacher() && !UserSession.isAdmin()) {
            hide(btnChildren);
            hide(btnStaff);
            hide(btnTeachers);
            hide(btnBillingPolicy);
            hide(btnBillingLedger);
            hide(btnCasualTransit);
            hide(btnGenerateReport);
            hide(btnDaily);
            hide(btnMonthly);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
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
            btnCasualTransit,
            spacer,
            btnLogout
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

    private static Button createNavButton(String title) {
        Button button = new Button(title);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private static Button createMainNavButton(String title) {
        Button button = createNavButton(title);
        button.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        button.setMinHeight(28);
        button.setPrefHeight(Region.USE_COMPUTED_SIZE);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(true);
        button.setTextOverrun(OverrunStyle.CLIP);
        return button;
    }

    private static Button createReportButton(String title, double prefHeight, double maxWidth) {
        Button button = createNavButton(title);
        button.setStyle("-fx-background-color: #FFF4B4;-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
        button.setPrefHeight(prefHeight);
        button.setMaxWidth(maxWidth);
        return button;
    }

    private static void hide(Button button) {
        button.setVisible(false);
        button.setManaged(false);
    }

    private static void applySidebarResponsiveness(
        VBox sidebar,
        ImageView profilePic,
        Labeled nameLabel,
        Button... buttons
    ) {
        sidebar.setFillWidth(true);
        sidebar.setSpacing(20);
        sidebar.setPadding(new Insets(20));

        profilePic.setFitWidth(100);
        profilePic.setFitHeight(130);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        sidebar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null || newScene.getWindow() == null) {
                return;
            }

            newScene.getWindow().heightProperty().addListener((o, oh, nh) -> {
                double h = nh.doubleValue();
                boolean compact = h < 900;
                boolean ultraCompact = h < 800;
                boolean extreme = h < 740;

                double btnH = extreme ? 46 : (ultraCompact ? 50 : (compact ? 54 : 60));
                double spacing = extreme ? 6 : (ultraCompact ? 8 : (compact ? 12 : 18));
                double pad = extreme ? 6 : (ultraCompact ? 10 : (compact ? 12 : 18));
                double fsMain = extreme ? 13 : (ultraCompact ? 14 : (compact ? 15 : 16));
                double padV = extreme ? 10 : (ultraCompact ? 12 : (compact ? 12 : 14));
                double padH = 18;

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

                for (Button button : buttons) {
                    button.setMinHeight(28);
                    button.setPrefHeight(btnH);
                    button.setMaxWidth(Double.MAX_VALUE);
                    button.setWrapText(true);
                    button.setTextAlignment(TextAlignment.CENTER);
                    button.setTextOverrun(OverrunStyle.CLIP);
                    button.setStyle(
                        "-fx-background-color: #FFCB3C;"
                            + "-fx-font-weight: bold;"
                            + "-fx-background-radius: 28px;"
                            + "-fx-font-size: " + fsMain + "px;"
                            + "-fx-padding: " + padV + " " + padH + ";"
                    );
                }
            });
        });
    }
}