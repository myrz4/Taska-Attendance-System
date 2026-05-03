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

@SuppressWarnings("unused")
final class CasualTransitDialogSupport {
    private CasualTransitDialogSupport() {}

    static Optional<Map<String, String>> showCreateVisitDialog(Window owner) {
        Dialog<Map<String, String>> dialog = baseDialog(owner, "New Casual Transit Visit", "Create a walk-in visit for an unregistered child");
        ButtonType createButton = new ButtonType("Create Visit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);
        AppThemeSupport.styleDialogButtons(dialog, createButton);

        TextField childNameField = new TextField();
        ComboBox<String> transitTypeField = new ComboBox<>();
        transitTypeField.setItems(FXCollections.observableArrayList("1 Hour", "1 Day", "1 Week"));
        transitTypeField.setValue("1 Day");
        ComboBox<String> rateTypeField = new ComboBox<>();
        rateTypeField.setItems(FXCollections.observableArrayList("Non-staff", "Staff"));
        rateTypeField.setValue("Non-staff");
        TextField guardianNameField = new TextField();
        TextField guardianPhoneField = new TextField();
        TextField guardianRelationshipField = new TextField();
        TextArea notesArea = notesArea(3, "");
        AppThemeSupport.styleControls(
            childNameField,
            transitTypeField,
            rateTypeField,
            guardianNameField,
            guardianPhoneField,
            guardianRelationshipField,
            notesArea
        );

        GridPane grid = formGrid();
        grid.addRow(0, new Label("Child Name"), childNameField);
        grid.addRow(1, new Label("Transit Type"), transitTypeField);
        grid.addRow(2, new Label("Rate Type"), rateTypeField);
        grid.addRow(3, new Label("Guardian Name"), guardianNameField);
        grid.addRow(4, new Label("Guardian Phone"), guardianPhoneField);
        grid.addRow(5, new Label("Relationship"), guardianRelationshipField);
        grid.addRow(6, new Label("Notes"), notesArea);
        grow(childNameField, transitTypeField, rateTypeField, guardianNameField, guardianPhoneField, guardianRelationshipField, notesArea);
        dialog.getDialogPane().setContent(
            AppThemeSupport.createDialogContent(
                AppThemeSupport.createFormSection(
                    "Visit details",
                    "Capture the child, guardian, and rate type for this casual transit visit.",
                    grid
                )
            )
        );

        dialog.setResultConverter(button -> {
            if (button != createButton) return null;
            Map<String, String> values = new LinkedHashMap<>();
            values.put("childName", childNameField.getText());
            values.put("transitType", transitTypeField.getValue());
            values.put("staffType", rateTypeField.getValue());
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
        AppThemeSupport.styleDialogButtons(dialog, checkoutButtonType);

        TextField amountField = new TextField();
        amountField.setPromptText("Optional manual override in RM. Leave blank for auto pricing.");
        ComboBox<String> paymentMethodField = new ComboBox<>();
        paymentMethodField.setItems(FXCollections.observableArrayList("Cash", "Transfer"));
        paymentMethodField.setValue("Cash");
        TextArea notesArea = notesArea(3, selected.notes());
        AppThemeSupport.styleControls(amountField, paymentMethodField, notesArea);

        GridPane grid = formGrid();
        grid.addRow(0, new Label("Child"), new Label(selected.childName()));
        grid.addRow(1, new Label("Guardian"), new Label(selected.guardianSummary()));
        grid.addRow(2, new Label("Manual Override (RM)"), amountField);
        grid.addRow(3, new Label("Payment Method"), paymentMethodField);
        grid.addRow(4, new Label("Notes"), notesArea);
        grow(amountField, paymentMethodField, notesArea);
        dialog.getDialogPane().setContent(
            AppThemeSupport.createDialogContent(
                AppThemeSupport.createFormSection(
                    "Checkout details",
                    "Confirm payment details and capture any notes before closing this visit.",
                    grid
                )
            )
        );

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
        AppThemeSupport.styleDialogButtons(dialog, saveButton);

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
        reasonField.setPromptText("Required reason");
        AppThemeSupport.styleControls(
            childNameField,
            guardianNameField,
            guardianPhoneField,
            guardianRelationshipField,
            checkInField,
            checkOutField,
            amountField,
            paymentMethodField,
            notesArea,
            reasonField
        );

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
        Label reasonHelper = new Label("Reason is required so other admins can understand why this visit was edited.");
        reasonHelper.getStyleClass().add("app-required-text");
        dialog.getDialogPane().setContent(
            AppThemeSupport.createDialogContent(
                AppThemeSupport.createFormSection(
                    "Visit updates",
                    "Adjust visit timing, guardian details, and payment information.",
                    grid,
                    reasonHelper
                )
            )
        );

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
        boolean dangerAction = title != null && title.toLowerCase().contains("cancel");
        AppThemeSupport.styleDialogButtons(dialog, actionType, dangerAction ? new ButtonType[] {actionType} : new ButtonType[0]);

        TextArea notesArea = notesArea(3, initialNotes);
        TextField reasonField = new TextField();
        reasonField.setPromptText("Required reason");
        AppThemeSupport.styleControls(notesArea, reasonField);
        GridPane grid = formGrid();
        grid.addRow(0, new Label("Notes"), notesArea);
        grid.addRow(1, new Label("Reason"), reasonField);
        grow(notesArea, reasonField);
        dialog.getDialogPane().setContent(
            AppThemeSupport.createDialogContent(
                AppThemeSupport.createFormSection(
                    "Review action",
                    "Explain why this visit is being reopened or canceled so the audit trail stays clear.",
                    grid
                )
            )
        );

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
        if (owner != null) {
            dialog.initOwner(owner);
        }
        AppThemeSupport.prepareDialog(
            dialog,
            title,
            header,
            title != null && title.toLowerCase().contains("cancel") ? AppThemeSupport.Tone.DANGER : AppThemeSupport.Tone.INFO
        );
        dialog.getDialogPane().setPrefWidth(560);
        return dialog;
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("app-form-grid");
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