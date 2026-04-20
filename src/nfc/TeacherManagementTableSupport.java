package nfc;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

@SuppressWarnings("unused")
final class TeacherManagementTableSupport {
    private static final TableColumn<Map<String, Object>, String> RECORD_ID_COL = new TableColumn<>("Record ID");

    private TeacherManagementTableSupport() {}

    static TableBundle setupTable(
        TableView<Map<String, Object>> table,
        Consumer<Map<String, Object>> onEdit,
        Consumer<Map<String, Object>> onDelete
    ) {
        TableColumn<Map<String, Object>, Integer> noCol = new TableColumn<>("No");
        TableColumn<Map<String, Object>, String> avatarCol = imageCol("Avatar", "image");
        TableColumn<Map<String, Object>, String> nameCol = textCol("Full Name", row -> Objects.toString(row.get("name"), ""));
        TableColumn<Map<String, Object>, String> usernameCol = textCol("Username", row -> Objects.toString(row.get("username"), ""));
        TableColumn<Map<String, Object>, String> emailCol = textCol("Email", row -> Objects.toString(row.get("email"), ""));
        TableColumn<Map<String, Object>, String> phoneCol = textCol("Phone", row -> Objects.toString(row.get("phone"), ""));
        TableColumn<Map<String, Object>, String> salaryCol = textCol("Base Salary (RM)", row -> formatMoney(row.get("salaryBaseSen")));
        TableColumn<Map<String, Object>, String> overtimeCol = textCol("Overtime Rates", TeacherManagementTableSupport::overtimeSummary);
        TableColumn<Map<String, Object>, String> statusCol = textCol("Status", TeacherManagementTableSupport::statusText);
        TableColumn<Map<String, Object>, Void> actionCol = actionCol(onEdit, onDelete);

        noCol.setCellFactory(col -> new TableCell<Map<String, Object>, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                }
                setAlignment(Pos.CENTER);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #181818;");
            }
        });

        RECORD_ID_COL.setCellValueFactory(d -> new SimpleStringProperty(Objects.toString(d.getValue().get("id"), "")));
        RECORD_ID_COL.setCellFactory(tc -> copyCell(row -> Objects.toString(row.get("id"), ""), Pos.CENTER_LEFT));
        RECORD_ID_COL.setVisible(false);

        noCol.setPrefWidth(56);
        avatarCol.setPrefWidth(84);
        nameCol.setPrefWidth(220);
        usernameCol.setPrefWidth(160);
        emailCol.setPrefWidth(210);
        phoneCol.setPrefWidth(150);
        salaryCol.setPrefWidth(130);
        overtimeCol.setPrefWidth(220);
        statusCol.setPrefWidth(94);
        RECORD_ID_COL.setPrefWidth(180);
        actionCol.setPrefWidth(148);

        table.getColumns().clear();
        table.getColumns().setAll(Arrays.<TableColumn<Map<String, Object>, ?>>asList(
            noCol,
            avatarCol,
            nameCol,
            usernameCol,
            emailCol,
            phoneCol,
            salaryCol,
            overtimeCol,
            statusCol,
            RECORD_ID_COL,
            actionCol
        ));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        SummaryTableSupport.configureSummaryTable(
            table,
            "No teachers found.",
            TeacherManagementTableSupport::rowSummary,
            row -> Objects.toString(row.get("id"), ""),
            TeacherManagementTableSupport::asJson,
            onEdit
        );

        return new TableBundle(nameCol, List.of(RECORD_ID_COL));
    }

    private static TableColumn<Map<String, Object>, Void> actionCol(
        Consumer<Map<String, Object>> onEdit,
        Consumer<Map<String, Object>> onDelete
    ) {
        TableColumn<Map<String, Object>, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setCellFactory(col -> new ActionButtonsTableCell<>(
            ActionButtonsTableCell.ActionSpec.normal("Edit", onEdit),
            ActionButtonsTableCell.ActionSpec.destructive("Delete", onDelete)
        ));
        return actionCol;
    }

    private static TableColumn<Map<String, Object>, String> imageCol(String title, String key) {
        TableColumn<Map<String, Object>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new SimpleStringProperty(Objects.toString(d.getValue().get(key), "")));
        column.setCellFactory(tc -> new TableCell<Map<String, Object>, String>() {
            private final ImageView iv = new ImageView();

            {
                iv.setFitWidth(42);
                iv.setFitHeight(42);
                iv.setPreserveRatio(true);
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty || url == null || url.trim().isEmpty()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                try {
                    Image img = ImageCache.loadCachedOrRemote(url.trim(), 42, 42);
                    iv.setImage(img);
                    setGraphic(iv);
                    setText(null);
                } catch (Exception ex) {
                    setGraphic(null);
                    setText("");
                }
            }
        });
        column.setStyle("-fx-alignment: CENTER;");
        return column;
    }

    private static TableColumn<Map<String, Object>, String> textCol(
        String title,
        Function<Map<String, Object>, String> valueProvider
    ) {
        TableColumn<Map<String, Object>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(d -> new SimpleStringProperty(valueProvider.apply(d.getValue())));
        column.setCellFactory(tc -> copyCell(valueProvider, Pos.CENTER_LEFT));
        column.setStyle("-fx-alignment: CENTER;");
        return column;
    }

    private static CopyableTableCell<Map<String, Object>> copyCell(
        Function<Map<String, Object>, String> valueProvider,
        Pos alignment
    ) {
        return new CopyableTableCell<>(
            valueProvider,
            SummaryTableSupport::displayText,
            TeacherManagementTableSupport::rowSummary,
            row -> Objects.toString(row.get("id"), ""),
            row -> SummaryTableSupport.toPrettyJson(asJson(row)),
            alignment,
            false
        );
    }

    private static String overtimeSummary(Map<String, Object> row) {
        return "5:30=" + shortMoney(row.get("salaryOvertimeAfter530Sen"))
            + " | 8pm=" + shortMoney(row.get("salaryOvertime8to12Sen"))
            + " | 12am=" + shortMoney(row.get("salaryOvertime12to7Sen"));
    }

    private static String statusText(Map<String, Object> row) {
        String fallback = Boolean.TRUE.equals(row.get("salaryActive")) ? "Active" : "Inactive";
        return SummaryTableSupport.resolveStatus(row, fallback);
    }

    private static String shortMoney(Object rawSen) {
        if (!(rawSen instanceof Number)) {
            return "-";
        }
        double rm = ((Number) rawSen).doubleValue() / 100.0;
        if (Math.floor(rm) == rm) {
            return String.valueOf((int) rm);
        }
        return String.format(Locale.US, "%.2f", rm);
    }

    private static String formatMoney(Object rawSen) {
        if (!(rawSen instanceof Number)) {
            return "-";
        }
        double rm = ((Number) rawSen).doubleValue() / 100.0;
        return String.format(Locale.US, "RM %.2f", rm);
    }

    private static LinkedHashMap<String, Object> asJson(Map<String, Object> row) {
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();
        json.put("recordId", Objects.toString(row.get("id"), ""));
        json.put("name", Objects.toString(row.get("name"), ""));
        json.put("username", Objects.toString(row.get("username"), ""));
        json.put("email", Objects.toString(row.get("email"), ""));
        json.put("phone", Objects.toString(row.get("phone"), ""));
        json.put("baseSalary", formatMoney(row.get("salaryBaseSen")));
        json.put("overtimeRates", overtimeSummary(row));
        json.put("status", statusText(row));
        json.put("image", Objects.toString(row.get("image"), ""));
        return json;
    }

    private static String rowSummary(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        return String.join("\n",
            "Teacher: " + SummaryTableSupport.displayText(Objects.toString(row.get("name"), "")),
            "Record ID: " + SummaryTableSupport.displayText(Objects.toString(row.get("id"), "")),
            "Username: " + SummaryTableSupport.displayText(Objects.toString(row.get("username"), "")),
            "Email: " + SummaryTableSupport.displayText(Objects.toString(row.get("email"), "")),
            "Phone: " + SummaryTableSupport.displayText(Objects.toString(row.get("phone"), "")),
            "Base Salary: " + SummaryTableSupport.displayText(formatMoney(row.get("salaryBaseSen"))),
            "Overtime Rates: " + SummaryTableSupport.displayText(overtimeSummary(row)),
            "Status: " + SummaryTableSupport.displayText(statusText(row))
        );
    }

    static final class TableBundle {
        final TableColumn<Map<String, Object>, String> nameCol;
        final List<TableColumn<Map<String, Object>, ?>> optionalColumns;

        TableBundle(
            TableColumn<Map<String, Object>, String> nameCol,
            List<TableColumn<Map<String, Object>, ?>> optionalColumns
        ) {
            this.nameCol = nameCol;
            this.optionalColumns = optionalColumns;
        }
    }
}
