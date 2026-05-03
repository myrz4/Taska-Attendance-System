package nfc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

@SuppressWarnings("unused")
final class BillingPolicyUiSupport {
    private BillingPolicyUiSupport() {}

    static FlowPane createWrapRow(Node... nodes) {
        return createWrapRow(8, 8, nodes);
    }

    static FlowPane createWrapRow(double hgap, double vgap, Node... nodes) {
        FlowPane row = new FlowPane();
        row.setHgap(hgap);
        row.setVgap(vgap);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("app-chip-strip");
        row.getChildren().addAll(nodes);
        return row;
    }

    static void configureActionButtons(Button... buttons) {
        configureActionButtons(150, buttons);
    }

    static void configureActionButtons(double maxWidth, Button... buttons) {
        for (Button button : buttons) {
            AppThemeSupport.styleToolbarButtons(button);
            button.setWrapText(true);
            button.setMinHeight(32);
            button.setMaxWidth(maxWidth);
        }
    }

    static File chooseSaveFile(Window owner, String title, String extensionLabel, String extensionPattern, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionLabel, extensionPattern));
        chooser.setInitialFileName(initialFileName);
        return chooser.showSaveDialog(owner);
    }

    static void writeTextFile(File file, String content) throws IOException {
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.print(content == null ? "" : content);
        }
    }

    static void showInfo(Window owner, String header, String message) {
        AppThemeSupport.showInfo(owner, header, message);
    }

    static void showError(Window owner, String header, Exception ex) {
        AppThemeSupport.showException(owner, header, ex);
    }
}