package nfc;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

@SuppressWarnings("all")
final class AdminDashboardLayoutSupport {
    private AdminDashboardLayoutSupport() {
    }

    @SuppressWarnings("unused")
    static AdminDashboardContentSupport.DashboardWidgets buildDashboard(AdminDashboardContentSupport.DashboardActions actions) {
        HBox dashboardHeader = new HBox(18);
        dashboardHeader.setAlignment(Pos.CENTER_LEFT);
        dashboardHeader.setPrefHeight(70);
        dashboardHeader.setMaxWidth(Double.MAX_VALUE);
        dashboardHeader.setStyle(
            "-fx-background-color: #2e8b57, #FECF4D;"
                + "-fx-background-insets: 0, 0 0 3 0;"
                + "-fx-background-radius: 0, 0;"
        );

        ImageView honeyPot = new ImageView(actions.loadImage("hive2.png"));
        honeyPot.setFitWidth(54);
        honeyPot.setFitHeight(54);

        Label dashboardTitle = new Label("DASHBOARD");
        dashboardTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 44));
        dashboardTitle.setTextFill(Color.web("#181818"));
        dashboardHeader.getChildren().addAll(honeyPot, dashboardTitle);

        VBox dashboardBody = new VBox(20);
        dashboardBody.setPadding(new Insets(20));
        dashboardBody.setAlignment(Pos.TOP_LEFT);
        dashboardBody.setStyle("-fx-background-color: linear-gradient(to bottom right, #86d67f 0%, #76cc6e 100%);");

        Label dateLabel = new Label();
        dateLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label clockLabel = new Label();
        clockLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        startClock(dateLabel, clockLabel);

        Label scannedToday = new Label();
        scannedToday.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 20px; -fx-background-radius: 50px;");
        Label notScanned = new Label();
        notScanned.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-padding: 20px; -fx-background-radius: 50px;");
        Label billingAgeReview = new Label("Age Review: 0 fam / 0 inv");
        billingAgeReview.setStyle("-fx-background-color: #ffdda8; -fx-text-fill: #7a4200; -fx-padding: 20px; -fx-background-radius: 50px;");
        Label billingOvertimeReview = new Label("OT Review: 0 fam / 0 inv");
        billingOvertimeReview.setStyle("-fx-background-color: #fff2a8; -fx-text-fill: #7a5a00; -fx-padding: 20px; -fx-background-radius: 50px;");
        configureBillingBadge(
            billingAgeReview,
            "Open Billing Ledger with age-review invoices first.",
            actions::openAgeReviewLedger
        );
        configureBillingBadge(
            billingOvertimeReview,
            "Open Billing Ledger with overtime-review invoices first.",
            actions::openOvertimeReviewLedger
        );

        HBox topRow = new HBox(28, scannedToday, notScanned, billingAgeReview, billingOvertimeReview);
        topRow.setAlignment(Pos.CENTER);

        Label scanLabel = new Label("Live Scans:");
        scanLabel.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));
        HBox statsBox = new HBox(30, scanLabel, new Region(), dateLabel, clockLabel);
        HBox.setHgrow(statsBox.getChildren().get(1), Priority.ALWAYS);
        statsBox.setAlignment(Pos.TOP_CENTER);

        ListView<String> liveIns = new ListView<>();
        liveIns.setPrefHeight(300);
        liveIns.setPrefWidth(300);
        ListView<String> liveOuts = new ListView<>();
        liveOuts.setPrefHeight(300);
        liveOuts.setPrefWidth(300);
        configureLiveScanList(liveIns);
        configureLiveScanList(liveOuts);

        Label checkInTitle = new Label("Check-Ins:");
        Label checkOutTitle = new Label("Check-Outs:");
        checkInTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));
        checkOutTitle.setFont(Font.font("Impact", FontWeight.EXTRA_BOLD, 20));

        VBox checkInList = new VBox(6);
        VBox checkOutList = new VBox(6);
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

        ImageView beeIcon = new ImageView(actions.loadImage("bee-speaker.png"));
        beeIcon.setFitWidth(64);
        beeIcon.setFitHeight(64);

        Label announcementText = new Label(
            "Dear Taska Zurah Team,\n"
                + "Here’s what’s coming up this week:\n"
                + "• Monday: Staff meeting at 8:00 AM in the Teachers’ Lounge (Room 2)\n"
                + "Please check the Staff Docs section for updated duty rosters and activity guides."
        );
        announcementText.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        announcementText.setStyle("-fx-text-fill: #181818;");
        announcementText.setWrapText(true);

        HBox announcementBox = new HBox(18, beeIcon, announcementText);
        announcementBox.setPadding(new Insets(24, 24, 24, 24));
        announcementBox.setBackground(new Background(new BackgroundFill(
            Color.web("#fff4b4"), new CornerRadii(18), Insets.EMPTY
        )));
        announcementBox.setMaxWidth(Double.MAX_VALUE);
        announcementBox.setAlignment(Pos.CENTER_LEFT);

        VBox announcementArea = new VBox(10, announcementTitle, announcementBox);
        announcementArea.setPadding(new Insets(10, 0, 0, 0));

        Region flexibleSpacer = new Region();
        VBox.setVgrow(flexibleSpacer, Priority.ALWAYS);
        dashboardBody.getChildren().addAll(
            topRow,
            statsBox,
            new Separator(),
            livePane,
            new Separator(),
            flexibleSpacer,
            announcementArea
        );

        BorderPane dashboardLayout = new BorderPane();
        dashboardLayout.setTop(dashboardHeader);
        dashboardLayout.setCenter(dashboardBody);

        return new AdminDashboardContentSupport.DashboardWidgets(
            dashboardLayout,
            scannedToday,
            notScanned,
            billingAgeReview,
            billingOvertimeReview,
            checkInList,
            checkOutList
        );
    }

    private static void startClock(Label dateLabel, Label clockLabel) {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            clockLabel.setText("Time: " + java.time.LocalTime.now().withNano(0));
            dateLabel.setText("Date: " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private static void configureBillingBadge(Label label, String tooltipText, Runnable action) {
        label.setCursor(Cursor.HAND);
        label.setTooltip(new Tooltip(tooltipText));
        label.setOnMouseClicked(event -> action.run());
    }

    private static void configureLiveScanList(ListView<String> listView) {
        Font checkFont = Font.font("JetBrains Mono", FontWeight.NORMAL, 16);
        listView.setCellFactory(list -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                setFont(checkFont);
                setStyle("-fx-text-fill: #181818;");
            }
        });
    }
}