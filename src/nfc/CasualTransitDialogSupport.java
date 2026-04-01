package nfc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

final class CasualTransitDialogSupport {
    private CasualTransitDialogSupport() {}

    static Optional<Map<String, String>> showCreateVisitDialog(Window owner) {
        Dialog<Map<String, String>> dialog = baseDialog(owner, "New Casual Transit Visit", "Create a walk-in visit for an unregistered child");
        ButtonType createButton = new ButtonType("Create Visit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);

        TextField childNameField = new TextField();
        TextField guardianNameField = new TextField();
        TextField guardianPhoneField = new TextField();
        TextField guardianRelationshipField = new TextField();
        TextArea notesArea = notesArea(3, "");

        GridPane grid = formGrid();
        grid.addRow(0, new Label("Child Name"), childNameField);
        grid.addRow(1, new Label("Guardian Name"), guardianNameField);
        grid.addRow(2, new Label("Guardian Phone"), guardianPhoneField);
        grid.addRow(3, new Label("Relationship"), guardianRelationshipField);
        grid.addRow(4, new Label("Notes"), notesArea);
        grow(childNameField, guardianNameField, guardianPhoneField, guardianRelationshipField, notesArea);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != createButton) return null;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("childName", childNameField.getText());
            values.put("guardianName", guardianNameField.getText());
            values.put("guardianPhone", guardianPhoneField.getText());
            values.put("guardianRelationship", guardianRelationshipField.getText());
            values.put("notes", notesArea.getText());
            return values;
        });

        return dialog.showAndWait();
    }

    static Optional<Map<String, String>> showCheckoutDialog(Window owner, CasualTransitView.VisitRow selected) {
        Dialog<Map<String, String>> dialog = baseDialog(owner, "Checkout Casual Transit Visit", "Record pickup payment and close this visit");
        ButtonType checkoutButtonType = new ButtonType("Checkout", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(checkoutButtonType, ButtonType.CANCEL);

        TextField amountField = new TextField();
        amountField.setPromptText("Amount in RM, example 25.00");
        ComboBox<String> paymentMethodField = new ComboBox<>();
        paymentMethodField.setItems(FXCollections.observableArrayList("Cash", "Transfer"));
        paymentMethodField.setValue("Cash");
        TextArea notesArea = notesArea(3, selected.notes());

        GridPane grid = formGrid();
        grid.addRow(0, new Label("Child"), new Label(selected.childName()));
        grid.addRow(1, new Label("Guardian"), new Label(selected.guardianSummary()));
        grid.addRow(2, new Label("Amount (RM)"), amountField);
        grid.addRow(3, new Label("Payment Method"), paymentMethodField);
        grid.addRow(4, new Label("Notes"), notesArea);
        grow(amountField, paymentMethodField, notesArea);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != checkoutButtonType) return null;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("amount", amountField.getText());
            values.put("paymentMethod", paymentMethodField.getValue());
            values.put("notes", notesArea.getText());
            return values;
        });

        return dialog.showAndWait();
    }

    static Optional<Map<String, String>> showEditVisitDialog(Window owner, CasualTransitView.VisitRow selected) {
        Dialog<Map<String, String>> dialog = baseDialog(owner, "Edit Casual Transit Visit", "Update visit details and keep an audit reason");
        ButtonType saveButton = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField childNameField = new TextField(selected.childName());
        TextField guardianNameField = new TextField(selected.guardianName());
        TextField guardianPhoneField = new TextField(selected.guardianPhone());
        TextField guardianRelationshipField = new TextField(selected.guardianRelationship());
        TextField checkInField = new TextField(selected.checkInInputValue());
        TextField checkOutField = new TextField(selected.checkOutInputValue());
        TextField amountField = new TextField(selected.amountInputValue());
        ComboBox<String> paymentMethodField = new ComboBox<>();
        paymentMethodField.setItems(FXCollections.observableArrayList("", "Cash", "Transfer"));
        paymentMethodField.setValue(selected.paymentMethod());
        TextArea notesArea = notesArea(3, selected.notes());
        TextField reasonField = new TextField();

        GridPane grid = formGrid();
        grid.addRow(0, new Label("Child Name"), childNameField);
        grid.addRow(1, new Label("Guardian Name"), guardianNameField);
        grid.addRow(2, new Label("Guardian Phone"), guardianPhoneField);
        grid.addRow(3, new Label("Relationship"), guardianRelationshipField);
        grid.addRow(4, new Label("Check In"), checkInField);
        grid.addRow(5, new Label("Check Out"), checkOutField);
        grid.addRow(6, new Label("Amount (RM)"), amountField);
        grid.addRow(7, new Label("Payment Method"), paymentMethodField);
        grid.addRow(8, new Label("Notes"), notesArea);
        grid.addRow(9, new Label("Reason"), reasonField);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != saveButton) return null;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("childName", childNameField.getText());
            values.put("guardianName", guardianNameField.getText());
            values.put("guardianPhone", guardianPhoneField.getText());
            values.put("guardianRelationship", guardianRelationshipField.getText());
            values.put("checkInAt", checkInField.getText());
            values.put("checkOutAt", checkOutField.getText());
            values.put("amount", amountField.getText());
            values.put("paymentMethod", paymentMethodField.getValue());
            values.put("notes", notesArea.getText());
            values.put("reason", reasonField.getText());
            return values;
        });

        return dialog.showAndWait();
    }

    static Optional<Map<String, String>> showReopenDialog(Window owner, CasualTransitView.VisitRow selected) {
        return showNotesReasonDialog(
            owner,
            "Reopen Casual Transit Visit",
            "Reopen this paid visit and clear its checkout/payment details",
            "Reopen Visit",
            selected.notes()
        );
    }

    static Optional<Map<String, String>> showCancelDialog(Window owner, CasualTransitView.VisitRow selected) {
        return showNotesReasonDialog(
            owner,
            "Cancel Casual Transit Visit",
            "Cancel this visit and mark its payment state as void",
            "Cancel Visit",
            selected.notes()
        );
    }

    private static Optional<Map<String, String>> showNotesReasonDialog(Window owner, String title, String header, String actionLabel, String initialNotes) {
        Dialog<Map<String, String>> dialog = baseDialog(owner, title, header);
        ButtonType actionType = new ButtonType(actionLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(actionType, ButtonType.CANCEL);

        TextArea notesArea = notesArea(3, initialNotes);
        TextField reasonField = new TextField();
        GridPane grid = formGrid();
        grid.addRow(0, new Label("Notes"), notesArea);
        grid.addRow(1, new Label("Reason"), reasonField);
        grow(notesArea, reasonField);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != actionType) return null;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("notes", notesArea.getText());
            values.put("reason", reasonField.getText());
            return values;
        });

        return dialog.showAndWait();
    }

    private static Dialog<Map<String, String>> baseDialog(Window owner, String title, String header) {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog;
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        return grid;
    }

    private static TextArea notesArea(int rowCount, String value) {
        TextArea notesArea = new TextArea(value);
        notesArea.setPrefRowCount(rowCount);
        return notesArea;
    }

    private static void grow(javafx.scene.Node... nodes) {
        for (javafx.scene.Node node : nodes) {
            GridPane.setHgrow(node, Priority.ALWAYS);
        }
    }
}