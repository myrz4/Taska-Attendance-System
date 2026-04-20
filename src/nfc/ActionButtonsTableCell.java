package nfc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;

final class ActionButtonsTableCell<S> extends TableCell<S, Void> {
    private final List<ActionSpec<S>> specs;
    private final List<Button> buttons = new ArrayList<>();
    private final HBox actionsBox = new HBox(6);

    @SafeVarargs
    ActionButtonsTableCell(ActionSpec<S>... specs) {
        this.specs = Arrays.asList(specs);
        actionsBox.setAlignment(Pos.CENTER);
        getStyleClass().add("summary-action-cell");

        for (ActionSpec<S> spec : this.specs) {
            Button button = new Button(spec.label);
            button.getStyleClass().add("admin-action-btn");
            if (spec.destructive) {
                button.getStyleClass().add("admin-action-btn-danger");
            }
            button.setFocusTraversable(false);
            button.setOnAction(event -> spec.handler.accept(currentRowItem()));
            buttons.add(button);
        }
    }

    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || currentRowItem() == null) {
            setGraphic(null);
            return;
        }

        actionsBox.getChildren().clear();
        S rowItem = currentRowItem();
        for (int index = 0; index < specs.size(); index += 1) {
            ActionSpec<S> spec = specs.get(index);
            Button button = buttons.get(index);
            boolean visible = spec.visibleWhen == null || spec.visibleWhen.test(rowItem);
            button.setManaged(visible);
            button.setVisible(visible);
            if (visible) {
                actionsBox.getChildren().add(button);
            }
        }

        setAlignment(Pos.CENTER);
        setGraphic(actionsBox);
    }

    private S currentRowItem() {
        return getTableRow() == null ? null : getTableRow().getItem();
    }

    static final class ActionSpec<S> {
        final String label;
        final Consumer<S> handler;
        final Predicate<S> visibleWhen;
        final boolean destructive;

        private ActionSpec(String label, Consumer<S> handler, Predicate<S> visibleWhen, boolean destructive) {
            this.label = Objects.requireNonNull(label);
            this.handler = Objects.requireNonNull(handler);
            this.visibleWhen = visibleWhen;
            this.destructive = destructive;
        }

        static <S> ActionSpec<S> normal(String label, Consumer<S> handler) {
            return new ActionSpec<>(label, handler, row -> true, false);
        }

        static <S> ActionSpec<S> normal(String label, Predicate<S> visibleWhen, Consumer<S> handler) {
            return new ActionSpec<>(label, handler, visibleWhen, false);
        }

        static <S> ActionSpec<S> destructive(String label, Consumer<S> handler) {
            return new ActionSpec<>(label, handler, row -> true, true);
        }

        static <S> ActionSpec<S> destructive(String label, Predicate<S> visibleWhen, Consumer<S> handler) {
            return new ActionSpec<>(label, handler, visibleWhen, true);
        }
    }
}