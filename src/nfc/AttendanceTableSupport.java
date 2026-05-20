package nfc;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Window;

@SuppressWarnings("unused")
final class AttendanceTableSupport {
    private AttendanceTableSupport() {}

    @SuppressWarnings("unused")
    static void configureTable(
        TableView<AttendanceRecord> table,
        Consumer<AttendanceRecord> showAuditAction,
        Supplier<Window> windowSupplier
    ) {
        table.setEditable(true);

        TableColumn<AttendanceRecord, Boolean> selectCol = new TableColumn<>("Select");
        selectCol.setPrefWidth(78);
        selectCol.setEditable(true);
        selectCol.setSortable(false);
        selectCol.setReorderable(false);
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));

        TableColumn<AttendanceRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setPrefWidth(120);
        nameCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<AttendanceRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(120);
        statusCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatusLabel()));

        TableColumn<AttendanceRecord, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        reasonCol.setMinWidth(120);
        reasonCol.setCellValueFactory(data -> data.getValue().reasonProperty());

        TableColumn<AttendanceRecord, String> inCol = new TableColumn<>("Check-In");
        inCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        inCol.setPrefWidth(95);
        inCol.setCellValueFactory(data -> data.getValue().checkInTimeProperty());

        TableColumn<AttendanceRecord, String> outCol = new TableColumn<>("Check-Out");
        outCol.setPrefWidth(95);
        outCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        outCol.setCellValueFactory(data -> data.getValue().checkOutTimeProperty());

        TableColumn<AttendanceRecord, String> manualInCol = new TableColumn<>("Manual In");
        manualInCol.setMinWidth(96);
        manualInCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        manualInCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getManualCheckInLabel()));

        TableColumn<AttendanceRecord, String> manualOutCol = new TableColumn<>("Manual Out");
        manualOutCol.setMinWidth(102);
        manualOutCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        manualOutCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getManualCheckOutLabel()));

        TableColumn<AttendanceRecord, String> checkoutByCol = new TableColumn<>("Check-Out By");
        checkoutByCol.setMinWidth(150);
        checkoutByCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        checkoutByCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCheckoutHandledByLabel()));

        TableColumn<AttendanceRecord, String> correctionCol = new TableColumn<>("Correction Reason");
        correctionCol.setMinWidth(180);
        correctionCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        correctionCol.setCellValueFactory(data -> data.getValue().manualEditReasonProperty());

        TableColumn<AttendanceRecord, Void> auditCol = new TableColumn<>("Audit");
        auditCol.setMinWidth(96);
        auditCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        auditCol.setCellFactory(param -> new TableCell<AttendanceRecord, Void>() {
            private final Button auditBtn = new Button("View");

            {
                auditBtn.setOnAction(event -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    showAuditAction.accept(record);
                });
            }

            @Override
            public void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : auditBtn);
            }
        });

        TableColumn<AttendanceRecord, Void> uploadCol = new TableColumn<>("Upload Letter");
        uploadCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        uploadCol.setCellFactory(param -> new TableCell<AttendanceRecord, Void>() {
            private final Button uploadBtn = new Button("Upload");

            {
                uploadBtn.setOnAction(event -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    FileChooser fileChooser = new FileChooser();
                    File selectedFile = fileChooser.showOpenDialog(windowSupplier.get());
                    if (selectedFile != null) {
                        record.setReasonLetterFile(selectedFile);
                    }
                });
            }

            @Override
            public void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : uploadBtn);
            }
        });

        TableColumn<AttendanceRecord, Void> viewCol = new TableColumn<>("View Letter");
        viewCol.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #222;");
        viewCol.setCellFactory(param -> new TableCell<AttendanceRecord, Void>() {
            private final Button viewBtn = new Button("View");

            {
                viewBtn.setOnAction(event -> {
                    AttendanceRecord record = getTableView().getItems().get(getIndex());
                    File reasonLetterFile = record.getReasonLetterFile();
                    if (reasonLetterFile != null && reasonLetterFile.exists()) {
                        try {
                            Desktop.getDesktop().open(reasonLetterFile);
                        } catch (IOException ex) {
                            new Alert(Alert.AlertType.ERROR, "Unable to open file: " + ex.getMessage()).showAndWait();
                        }
                    } else {
                        new Alert(Alert.AlertType.WARNING, "No reason letter available to view.").showAndWait();
                    }
                });
            }

            @Override
            public void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        table.getColumns().setAll(Arrays.asList(
            selectCol,
            nameCol,
            statusCol,
            inCol,
            outCol,
            manualInCol,
            manualOutCol,
            checkoutByCol,
            correctionCol,
            auditCol,
            reasonCol,
            uploadCol,
            viewCol
        ));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(tv -> new TableRow<AttendanceRecord>() {
            @Override
            protected void updateItem(AttendanceRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.hasCheckOut()) {
                    setStyle("-fx-background-color: #cfeecb; -fx-text-fill: #1f5a25;");
                } else if (item.hasCheckIn()) {
                    setStyle("-fx-background-color: #e5f7df; -fx-text-fill: #2f7a33;");
                } else if (item.isAdminCorrected()) {
                    setStyle("-fx-background-color: #fff4d6; -fx-text-fill: #7a5c00;");
                } else {
                    setStyle("-fx-background-color: #ffebee; -fx-text-fill: #c62828;");
                }
            }
        });

        for (TableColumn<AttendanceRecord, ?> column : table.getColumns()) {
            column.setStyle(column.getStyle() == null ? "-fx-alignment: CENTER;" : column.getStyle());
        }
        table.getColumns().forEach(column -> column.setStyle(column.getStyle()));
    }
}