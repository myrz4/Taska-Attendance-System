package nfc;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

@SuppressWarnings("unused")
final class CRUDParentChildPickerSupport {
    private CRUDParentChildPickerSupport() {
    }

    static ChildPickerState attachChildPicker(
        List<CRUDParentDialogSupport.ChildOption> allChildren,
        Set<String> initialChildIds,
        TextField childrenSummaryTf,
        Button selectChildrenBtn
    ) {
        ObservableList<CRUDParentDialogSupport.ChildOption> allChildrenObs = FXCollections.observableArrayList(allChildren);
        FilteredList<CRUDParentDialogSupport.ChildOption> filteredChildren = new FilteredList<>(allChildrenObs, child -> true);
        Map<CRUDParentDialogSupport.ChildOption, BooleanProperty> selectedProps = new LinkedHashMap<>();
        Set<String> initialIds = initialChildIds == null ? Collections.emptySet() : initialChildIds;
        BooleanProperty hasSelectedChild = new SimpleBooleanProperty(false);

        Runnable refreshSelection = () -> {
            List<CRUDParentDialogSupport.ChildOption> selected = selectedProps.entrySet().stream()
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(CRUDParentDialogSupport.ChildOption::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

            hasSelectedChild.set(!selected.isEmpty());
            childrenSummaryTf.setText(selected.isEmpty()
                ? ""
                : selected.stream().map(CRUDParentDialogSupport.ChildOption::getName).collect(Collectors.joining(", ")));
        };

        for (CRUDParentDialogSupport.ChildOption child : allChildrenObs) {
            boolean selected = initialIds.contains(child.getId());
            selectedProps.put(child, trackedProperty(selected, refreshSelection));
        }
        refreshSelection.run();

        TextField searchTf = new TextField();
        searchTf.setPromptText("Search children...");
        AppThemeSupport.styleControls(searchTf);
        searchTf.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase();
            filteredChildren.setPredicate(child -> query.isEmpty()
                || child.getName().toLowerCase().contains(query)
                || child.getId().toLowerCase().contains(query));
        });

        ListView<CRUDParentDialogSupport.ChildOption> childrenList = new ListView<>(filteredChildren);
        childrenList.setPrefHeight(260);
        childrenList.setPrefWidth(360);
        AppThemeSupport.styleControls(childrenList);
        childrenList.setCellFactory(listView -> new CheckBoxListCell<CRUDParentDialogSupport.ChildOption>(
            item -> selectedProps.computeIfAbsent(item, ignored -> trackedProperty(false, refreshSelection))
        ) {
            @Override
            public void updateItem(CRUDParentDialogSupport.ChildOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        Button applyChildrenBtn = new Button("Apply");
        Button clearChildrenBtn = new Button("Clear");
        AppThemeSupport.stylePrimaryButtons(applyChildrenBtn);
        AppThemeSupport.styleGhostButtons(clearChildrenBtn);
        HBox popupActions = new HBox(10, applyChildrenBtn, clearChildrenBtn);

        VBox popupContent = new VBox(10,
            new Label("Select Children (multiple):"),
            searchTf,
            childrenList,
            popupActions
        );
        popupContent.setPadding(new Insets(12));
        popupContent.getStyleClass().addAll("app-card", "app-editor-card");

        CustomMenuItem popupItem = new CustomMenuItem(popupContent);
        popupItem.setHideOnClick(false);
        ContextMenu childrenMenu = new ContextMenu(popupItem);
        childrenMenu.setAutoHide(true);

        selectChildrenBtn.setOnAction(event -> {
            if (childrenMenu.isShowing()) {
                childrenMenu.hide();
            } else {
                childrenMenu.show(selectChildrenBtn, Side.BOTTOM, 0, 0);
            }
        });
        childrenSummaryTf.setOnMouseClicked(event -> {
            if (!childrenMenu.isShowing()) {
                childrenMenu.show(childrenSummaryTf, Side.BOTTOM, 0, 0);
            }
        });
        applyChildrenBtn.setOnAction(event -> childrenMenu.hide());
        clearChildrenBtn.setOnAction(event -> {
            selectedProps.values().forEach(property -> property.set(false));
            refreshSelection.run();
        });

        return new ChildPickerState(selectedProps, hasSelectedChild);
    }

    private static BooleanProperty trackedProperty(boolean initialValue, Runnable refreshSelection) {
        BooleanProperty property = new SimpleBooleanProperty(initialValue);
        property.addListener((obs, oldValue, newValue) -> refreshSelection.run());
        return property;
    }

    static final class ChildPickerState {
        private final Map<CRUDParentDialogSupport.ChildOption, BooleanProperty> selectedProps;
        private final BooleanProperty hasSelectedChild;

        ChildPickerState(
            Map<CRUDParentDialogSupport.ChildOption, BooleanProperty> selectedProps,
            BooleanProperty hasSelectedChild
        ) {
            this.selectedProps = selectedProps;
            this.hasSelectedChild = hasSelectedChild;
        }

        BooleanProperty hasSelectedChildProperty() {
            return hasSelectedChild;
        }

        List<CRUDParentDialogSupport.ChildOption> selectedChildren() {
            return selectedProps.entrySet().stream()
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(CRUDParentDialogSupport.ChildOption::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        }
    }
}