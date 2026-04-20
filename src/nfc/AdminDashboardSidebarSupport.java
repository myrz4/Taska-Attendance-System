package nfc;

import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;

final class AdminDashboardSidebarSupport {
    private static final double EXPANDED_WIDTH = 248;
    private static final double COLLAPSED_WIDTH = 96;
    private static final double EXPANDED_PROFILE_SIZE = 112;
    private static final double COLLAPSED_PROFILE_SIZE = 58;

    private AdminDashboardSidebarSupport() {
    }

    static {
        java.util.function.Function<SidebarActions, VBox> keepSidebar = AdminDashboardSidebarSupport::createSidebar;
        java.util.Objects.requireNonNull(keepSidebar);
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
        VBox sidebar = new VBox(14);
        sidebar.getStyleClass().add("admin-sidebar");
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setPadding(new Insets(18));
        setSidebarWidth(sidebar, EXPANDED_WIDTH);

        VBox profileBox = AdminDashboardSidebarProfileSupport.createProfileBox();
        profileBox.getStyleClass().add("sidebar-profile-box");
        ImageView profileView = (ImageView) profileBox.getChildren().get(0);
        HBox welcomeBox = (HBox) profileBox.getChildren().get(1);
        Label nameLabel = (Label) welcomeBox.getChildren().get(0);
        ImageView beeIcon = (ImageView) welcomeBox.getChildren().get(1);

        Button toggleButton = new Button();
        toggleButton.getStyleClass().add("sidebar-toggle-button");
        toggleButton.setFocusTraversable(false);
        HBox toggleRow = new HBox(toggleButton);
        toggleRow.setAlignment(Pos.CENTER_RIGHT);

        Button btnDashboard = createMainNavButton("Dashboard", SidebarIconKind.DASHBOARD);
        Button btnAttendance = createMainNavButton("Attendance", SidebarIconKind.ATTENDANCE);
        Button btnGenerateReport = createSubNavButton("Generate Report", SidebarIconKind.REPORT);
        Button btnDaily = createSubNavButton("Daily", SidebarIconKind.DAILY);
        Button btnMonthly = createSubNavButton("Monthly", SidebarIconKind.MONTHLY);
        Button btnChildren = createMainNavButton("Children & Parents", SidebarIconKind.CHILDREN);
        Button btnStaff = createMainNavButton("Admins", SidebarIconKind.ADMINS);
        Button btnTeachers = createMainNavButton("Teachers", SidebarIconKind.TEACHERS);
        Button btnBillingLedger = createMainNavButton("Billing Ledger", SidebarIconKind.BILLING_LEDGER);
        Button btnBillingPolicy = createMainNavButton("Billing Policy", SidebarIconKind.BILLING_POLICY);
        Button btnCasualTransit = createMainNavButton("Casual Transit", SidebarIconKind.CASUAL_TRANSIT);
        Button btnLogout = createLogoutButton("Logout", SidebarIconKind.LOGOUT);

        VBox attendanceSubMenu = new VBox(8, btnGenerateReport, btnDaily, btnMonthly);
        attendanceSubMenu.getStyleClass().add("sidebar-submenu");
        attendanceSubMenu.setAlignment(Pos.CENTER);

        btnDashboard.setOnAction(e -> actions.showDashboard());
        btnAttendance.setOnAction(e -> actions.showAttendance());
        btnGenerateReport.setOnAction(e -> actions.showGenerateReport());
        btnDaily.setOnAction(e -> actions.showDailyReport());
        btnMonthly.setOnAction(e -> actions.showMonthlyReport());
        btnChildren.setOnAction(e -> actions.showChildren());
        btnStaff.setOnAction(e -> actions.showStaff());
        btnTeachers.setOnAction(e -> actions.showTeachers());
        btnBillingLedger.setOnAction(e -> actions.showBillingLedger());
        btnBillingPolicy.setOnAction(e -> actions.showBillingPolicy());
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

        List<SidebarButtonState> navButtons = List.of(
            new SidebarButtonState(btnDashboard, "Dashboard", SidebarIconKind.DASHBOARD, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnAttendance, "Attendance", SidebarIconKind.ATTENDANCE, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnGenerateReport, "Generate Report", SidebarIconKind.REPORT, SidebarButtonVariant.SUB),
            new SidebarButtonState(btnDaily, "Daily", SidebarIconKind.DAILY, SidebarButtonVariant.SUB),
            new SidebarButtonState(btnMonthly, "Monthly", SidebarIconKind.MONTHLY, SidebarButtonVariant.SUB),
            new SidebarButtonState(btnChildren, "Children & Parents", SidebarIconKind.CHILDREN, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnStaff, "Admins", SidebarIconKind.ADMINS, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnTeachers, "Teachers", SidebarIconKind.TEACHERS, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnBillingLedger, "Billing Ledger", SidebarIconKind.BILLING_LEDGER, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnBillingPolicy, "Billing Policy", SidebarIconKind.BILLING_POLICY, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnCasualTransit, "Casual Transit", SidebarIconKind.CASUAL_TRANSIT, SidebarButtonVariant.MAIN),
            new SidebarButtonState(btnLogout, "Logout", SidebarIconKind.LOGOUT, SidebarButtonVariant.LOGOUT)
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().addAll(
            toggleRow,
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

        boolean initiallyCollapsed = SidebarStateManager.isCollapsed();
        applySidebarState(
            sidebar,
            initiallyCollapsed,
            profileBox,
            profileView,
            welcomeBox,
            nameLabel,
            beeIcon,
            toggleButton,
            attendanceSubMenu,
            navButtons
        );

        toggleButton.setOnAction(event -> {
            boolean collapsed = !Boolean.TRUE.equals(sidebar.getProperties().get("collapsed"));
            SidebarStateManager.setCollapsed(collapsed);
            applySidebarState(
                sidebar,
                collapsed,
                profileBox,
                profileView,
                welcomeBox,
                nameLabel,
                beeIcon,
                toggleButton,
                attendanceSubMenu,
                navButtons
            );
        });

        return sidebar;
    }

    private static Button createMainNavButton(String title, SidebarIconKind iconKind) {
        Button button = createNavButton(title, iconKind, SidebarButtonVariant.MAIN);
        button.getStyleClass().add("sidebar-nav-button");
        return button;
    }

    private static Button createSubNavButton(String title, SidebarIconKind iconKind) {
        Button button = createNavButton(title, iconKind, SidebarButtonVariant.SUB);
        button.getStyleClass().add("sidebar-sub-button");
        return button;
    }

    private static Button createLogoutButton(String title, SidebarIconKind iconKind) {
        Button button = createNavButton(title, iconKind, SidebarButtonVariant.LOGOUT);
        button.getStyleClass().add("sidebar-logout-button");
        return button;
    }

    private static Button createNavButton(String title, SidebarIconKind iconKind, SidebarButtonVariant variant) {
        Button button = new Button();
        button.setTooltip(new Tooltip(title));
        button.setFocusTraversable(false);
        button.setTextOverrun(OverrunStyle.CLIP);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setText(null);
        button.setGraphic(createExpandedGraphic(button, title, iconKind, variant));
        return button;
    }

    private static void applySidebarState(
        VBox sidebar,
        boolean collapsed,
        VBox profileBox,
        ImageView profileView,
        HBox welcomeBox,
        Label nameLabel,
        ImageView beeIcon,
        Button toggleButton,
        VBox attendanceSubMenu,
        List<SidebarButtonState> navButtons
    ) {
        sidebar.getProperties().put("collapsed", collapsed);
        if (collapsed) {
            if (!sidebar.getStyleClass().contains("admin-sidebar-collapsed")) {
                sidebar.getStyleClass().add("admin-sidebar-collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("admin-sidebar-collapsed");
        }

        setSidebarWidth(sidebar, collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH);
        profileView.setFitWidth(collapsed ? COLLAPSED_PROFILE_SIZE : EXPANDED_PROFILE_SIZE);
        profileView.setFitHeight(collapsed ? COLLAPSED_PROFILE_SIZE : EXPANDED_PROFILE_SIZE);
        profileBox.setSpacing(collapsed ? 6 : 12);

        welcomeBox.setManaged(!collapsed);
        welcomeBox.setVisible(!collapsed);
        nameLabel.setManaged(!collapsed);
        nameLabel.setVisible(!collapsed);
        beeIcon.setManaged(!collapsed);
        beeIcon.setVisible(!collapsed);

        toggleButton.setText(collapsed ? ">>" : "<<");
        toggleButton.setTooltip(new Tooltip(collapsed ? "Expand sidebar" : "Collapse sidebar"));
        toggleButton.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_RIGHT);
        attendanceSubMenu.setAlignment(Pos.CENTER);

        for (SidebarButtonState state : navButtons) {
            updateButtonState(state, collapsed);
        }
    }

    private static void updateButtonState(SidebarButtonState state, boolean collapsed) {
        Button button = state.button;
        button.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        button.setWrapText(!collapsed);
        button.setGraphic(
            collapsed
                ? createCollapsedGraphic(state.iconKind, state.variant)
                : createExpandedGraphic(button, state.expandedLabel, state.iconKind, state.variant)
        );
    }

    private static Node createExpandedGraphic(
        Button button,
        String title,
        SidebarIconKind iconKind,
        SidebarButtonVariant variant
    ) {
        Label label = new Label(title);
        label.getStyleClass().addAll("sidebar-button-label", variant.labelStyleClass);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane iconShell = createIconShell(iconKind, variant);
        HBox content = new HBox(10, label, spacer, iconShell);
        content.getStyleClass().add("sidebar-button-content");
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMouseTransparent(true);
        content.prefWidthProperty().bind(button.widthProperty().subtract(30));
        return content;
    }

    private static Node createCollapsedGraphic(SidebarIconKind iconKind, SidebarButtonVariant variant) {
        StackPane iconShell = createIconShell(iconKind, variant);
        iconShell.setMouseTransparent(true);
        return iconShell;
    }

    private static StackPane createIconShell(SidebarIconKind iconKind, SidebarButtonVariant variant) {
        StackPane shell = new StackPane(createIconGraphic(iconKind, variant.iconColor));
        shell.getStyleClass().addAll("sidebar-button-icon-shell", variant.shellStyleClass);
        shell.setMinSize(26, 26);
        shell.setPrefSize(26, 26);
        shell.setMaxSize(26, 26);
        return shell;
    }

    private static Node createIconGraphic(SidebarIconKind iconKind, Color strokeColor) {
        switch (iconKind) {
            case DASHBOARD:
                return dashboardIcon(strokeColor);
            case ATTENDANCE:
                return attendanceIcon(strokeColor);
            case REPORT:
                return reportIcon(strokeColor);
            case DAILY:
                return dailyIcon(strokeColor);
            case MONTHLY:
                return monthlyIcon(strokeColor);
            case CHILDREN:
                return childrenIcon(strokeColor);
            case ADMINS:
                return adminsIcon(strokeColor);
            case TEACHERS:
                return teachersIcon(strokeColor);
            case BILLING_LEDGER:
                return billingLedgerIcon(strokeColor);
            case BILLING_POLICY:
                return billingPolicyIcon(strokeColor);
            case CASUAL_TRANSIT:
                return casualTransitIcon(strokeColor);
            case LOGOUT:
                return logoutIcon(strokeColor);
            default:
                return dashboardIcon(strokeColor);
        }
    }

    private static Group dashboardIcon(Color color) {
        Polygon roof = outlinePolygon(color, 3, 9, 9, 4, 15, 9);
        Rectangle body = outlineRect(5, 9, 8, 7, color);
        Rectangle door = outlineRect(8, 11, 2, 5, color);
        return new Group(roof, body, door);
    }

    private static Group attendanceIcon(Color color) {
        Rectangle board = outlineRect(4, 4, 10, 11, color);
        Rectangle clip = outlineRect(7, 2.5, 4, 3, color);
        Polyline check = outlinePolyline(color, 6, 11, 8, 13, 12, 9);
        return new Group(board, clip, check);
    }

    private static Group reportIcon(Color color) {
        Polygon page = outlinePolygon(color, 5, 2.5, 11, 2.5, 14, 5.5, 14, 15.5, 5, 15.5);
        Line foldVertical = line(11, 2.5, 11, 5.5, color);
        Line foldHorizontal = line(11, 5.5, 14, 5.5, color);
        Line row1 = line(7, 8, 12, 8, color);
        Line row2 = line(7, 10.5, 12, 10.5, color);
        Line row3 = line(7, 13, 10.5, 13, color);
        return new Group(page, foldVertical, foldHorizontal, row1, row2, row3);
    }

    private static Group dailyIcon(Color color) {
        Rectangle calendar = outlineRect(3, 4, 12, 11, color);
        Line header = line(3, 7, 15, 7, color);
        Line ringLeft = line(6, 2.5, 6, 5.5, color);
        Line ringRight = line(12, 2.5, 12, 5.5, color);
        Rectangle day = filledRect(7, 10, 4, 4, color);
        return new Group(calendar, header, ringLeft, ringRight, day);
    }

    private static Group monthlyIcon(Color color) {
        Rectangle calendar = outlineRect(3, 4, 12, 11, color);
        Line header = line(3, 7, 15, 7, color);
        Line ringLeft = line(6, 2.5, 6, 5.5, color);
        Line ringRight = line(12, 2.5, 12, 5.5, color);
        Rectangle day1 = filledRect(5.5, 9.5, 2.5, 2.5, color);
        Rectangle day2 = filledRect(9.5, 9.5, 2.5, 2.5, color);
        Rectangle day3 = filledRect(5.5, 13, 2.5, 2.5, color);
        Rectangle day4 = filledRect(9.5, 13, 2.5, 2.5, color);
        return new Group(calendar, header, ringLeft, ringRight, day1, day2, day3, day4);
    }

    private static Group childrenIcon(Color color) {
        Circle leftHead = outlineCircle(6, 6.5, 2.1, color);
        Circle rightHead = outlineCircle(11.5, 6.1, 1.7, color);
        Arc leftBody = outlineArc(6, 13.1, 4.1, 3.0, color);
        Arc rightBody = outlineArc(11.5, 12.4, 3.0, 2.4, color);
        return new Group(leftHead, rightHead, leftBody, rightBody);
    }

    private static Group adminsIcon(Color color) {
        Polygon shield = outlinePolygon(color, 9, 2.5, 14, 4.5, 13, 10.5, 9, 15.5, 5, 10.5, 4, 4.5);
        Line vertical = line(9, 5.5, 9, 10.5, color);
        Line horizontal = line(6.8, 8, 11.2, 8, color);
        return new Group(shield, vertical, horizontal);
    }

    private static Group teachersIcon(Color color) {
        Rectangle leftPage = outlineRect(3.5, 4.5, 5.5, 8.5, color);
        Rectangle rightPage = outlineRect(9, 4.5, 5.5, 8.5, color);
        Line spine = line(9, 4.5, 9, 13, color);
        Line bookmark = line(12, 4.5, 12, 10.5, color);
        return new Group(leftPage, rightPage, spine, bookmark);
    }

    private static Group billingLedgerIcon(Color color) {
        Polygon receipt = outlinePolygon(color, 4, 2.5, 14, 2.5, 14, 13, 12.5, 12, 11, 13, 9.5, 12, 8, 13, 6.5, 12, 5, 13, 4, 12);
        Line row1 = line(6, 6, 12, 6, color);
        Line row2 = line(6, 8.8, 12, 8.8, color);
        Line row3 = line(6, 11.6, 10, 11.6, color);
        return new Group(receipt, row1, row2, row3);
    }

    private static Group billingPolicyIcon(Color color) {
        Line pole = line(9, 3, 9, 13, color);
        Line beam = line(5, 6, 13, 6, color);
        Line leftRopeA = line(5, 6, 3.5, 9, color);
        Line leftRopeB = line(5, 6, 6.5, 9, color);
        Line rightRopeA = line(13, 6, 11.5, 9, color);
        Line rightRopeB = line(13, 6, 14.5, 9, color);
        Line leftBowl = line(2.5, 9, 7.5, 9, color);
        Line rightBowl = line(10.5, 9, 15.5, 9, color);
        Line base = line(6, 15, 12, 15, color);
        return new Group(pole, beam, leftRopeA, leftRopeB, rightRopeA, rightRopeB, leftBowl, rightBowl, base);
    }

    private static Group casualTransitIcon(Color color) {
        Polygon roof = outlinePolygon(color, 6, 8, 8, 5.5, 12, 5.5, 14, 8);
        Rectangle body = outlineRect(4, 8, 10, 4, color);
        Circle wheelLeft = outlineCircle(7, 13, 1.4, color);
        Circle wheelRight = outlineCircle(11, 13, 1.4, color);
        return new Group(roof, body, wheelLeft, wheelRight);
    }

    private static Group logoutIcon(Color color) {
        Rectangle door = outlineRect(10.5, 3, 4, 12, color);
        Line exitLine = line(9, 9, 3.5, 9, color);
        Polyline arrow = outlinePolyline(color, 6, 6.5, 3.5, 9, 6, 11.5);
        return new Group(door, exitLine, arrow);
    }

    private static Rectangle outlineRect(double x, double y, double width, double height, Color color) {
        Rectangle rect = new Rectangle(x, y, width, height);
        rect.setArcWidth(2);
        rect.setArcHeight(2);
        return outlineShape(rect, color);
    }

    private static Rectangle filledRect(double x, double y, double width, double height, Color color) {
        Rectangle rect = new Rectangle(x, y, width, height);
        rect.setArcWidth(2);
        rect.setArcHeight(2);
        rect.setFill(color);
        return rect;
    }

    private static Circle outlineCircle(double centerX, double centerY, double radius, Color color) {
        Circle circle = new Circle(centerX, centerY, radius);
        return outlineShape(circle, color);
    }

    private static Arc outlineArc(double centerX, double centerY, double radiusX, double radiusY, Color color) {
        Arc arc = new Arc(centerX, centerY, radiusX, radiusY, 0, 180);
        arc.setType(ArcType.OPEN);
        return outlineShape(arc, color);
    }

    private static Line line(double startX, double startY, double endX, double endY, Color color) {
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(color);
        line.setStrokeWidth(1.8);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        return line;
    }

    private static Polygon outlinePolygon(Color color, double... points) {
        Polygon polygon = new Polygon(points);
        return outlineShape(polygon, color);
    }

    private static Polyline outlinePolyline(Color color, double... points) {
        Polyline polyline = new Polyline(points);
        polyline.setFill(Color.TRANSPARENT);
        polyline.setStroke(color);
        polyline.setStrokeWidth(1.8);
        polyline.setStrokeLineCap(StrokeLineCap.ROUND);
        polyline.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return polyline;
    }

    private static <T extends Shape> T outlineShape(T shape, Color color) {
        shape.setFill(Color.TRANSPARENT);
        shape.setStroke(color);
        shape.setStrokeWidth(1.8);
        shape.setStrokeLineCap(StrokeLineCap.ROUND);
        shape.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return shape;
    }

    private static void setSidebarWidth(VBox sidebar, double width) {
        sidebar.setPrefWidth(width);
        sidebar.setMinWidth(width);
        sidebar.setMaxWidth(width);
    }

    private static void hide(Button button) {
        button.setVisible(false);
        button.setManaged(false);
    }

    private static final class SidebarButtonState {
        final Button button;
        final String expandedLabel;
        final SidebarIconKind iconKind;
        final SidebarButtonVariant variant;

        SidebarButtonState(Button button, String expandedLabel, SidebarIconKind iconKind, SidebarButtonVariant variant) {
            this.button = button;
            this.expandedLabel = expandedLabel;
            this.iconKind = iconKind;
            this.variant = variant;
        }
    }

    private enum SidebarButtonVariant {
        MAIN(Color.web("#243017"), "sidebar-button-label-main", "sidebar-button-icon-shell-main"),
        SUB(Color.web("#5a4300"), "sidebar-button-label-sub", "sidebar-button-icon-shell-sub"),
        LOGOUT(Color.WHITE, "sidebar-button-label-logout", "sidebar-button-icon-shell-logout");

        final Color iconColor;
        final String labelStyleClass;
        final String shellStyleClass;

        SidebarButtonVariant(Color iconColor, String labelStyleClass, String shellStyleClass) {
            this.iconColor = iconColor;
            this.labelStyleClass = labelStyleClass;
            this.shellStyleClass = shellStyleClass;
        }
    }

    private enum SidebarIconKind {
        DASHBOARD,
        ATTENDANCE,
        REPORT,
        DAILY,
        MONTHLY,
        CHILDREN,
        ADMINS,
        TEACHERS,
        BILLING_LEDGER,
        BILLING_POLICY,
        CASUAL_TRANSIT,
        LOGOUT
    }
}
