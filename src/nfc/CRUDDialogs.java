package nfc;

import java.util.function.Consumer;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

/**
 * CRUDDialogs (Firestore Version)
 * Replaces MySQL logic with Firebase Firestore operations.
 */
public class CRUDDialogs {

    // ------------------ Child Dialog ------------------
    public static void showChildDialog(ChildrenView.Child existing,
                                       boolean isNew,
                                       Runnable onSave) {
        CRUDChildDialogSupport.showChildDialog(existing, isNew, onSave);
    }

    // ------------------ Parent Dialog ------------------
    public static void showParentDialog(ParentsPane.ParentRecord existing,
                                        boolean isNew,
                                        Runnable onSave) {
        CRUDParentDialogSupport.showParentDialog(existing, isNew, onSave);
    }

    // ------------------ Generic Action Buttons ------------------
    public static <T> Callback<TableColumn<T, Void>, TableCell<T, Void>> createActionCell(
            Consumer<T> onEdit,
            Consumer<T> onDelete
    ) {
        return col -> new TableCell<T, Void>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn  = new Button("Delete");

            {
                editBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                delBtn.setStyle("-fx-background-color: #FFCB3C;-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #222; -fx-background-radius: 28px;");
                editBtn.setOnAction(e -> onEdit.accept(getCurrentItem()));
                delBtn.setOnAction(e -> onDelete.accept(getCurrentItem()));
                setGraphic(new HBox(5, editBtn, delBtn));
            }

            private T getCurrentItem() {
                return getTableView().getItems().get(getIndex());
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : getGraphic());
            }
        };
    }

    // ------------------ Staff Dialog (Firestore) ------------------
    public static void showStaffDialog(StaffManagementView.Admin existing,
                                       boolean isNew,
                                       Runnable onSave) {
        CRUDStaffDialogSupport.showStaffDialog(existing, isNew, onSave);
    }

    public static void showDeleteAdminDialog(
            StaffManagementView.Admin admin,
            Runnable onDeleteSuccess
    ) {
        CRUDStaffDialogSupport.showDeleteAdminDialog(admin, onDeleteSuccess);
    }
}