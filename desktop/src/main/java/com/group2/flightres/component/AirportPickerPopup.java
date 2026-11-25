package com.group2.flightres.component;

import com.group2.flightres.model.Airport;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Styled dark-glass scrollable airport picker popup with type-to-filter and keyboard navigation
 * (Up/Down arrows, Enter to select, Escape to dismiss). Matches the web airport picker.
 */
public class AirportPickerPopup {

    private final Popup popup = new Popup();
    private final TextField searchField = new TextField();
    private final ListView<Airport> listView = new ListView<>();
    private final ObservableList<Airport> filteredItems = FXCollections.observableArrayList();
    private List<Airport> allAirports = List.of();
    private Consumer<Airport> onSelect;

    public AirportPickerPopup() {
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        searchField.setPromptText("Search city, airport, or IATA code...");
        searchField.getStyleClass().add("airport-picker-search");

        listView.setItems(filteredItems);
        listView.getStyleClass().add("airport-picker-list");
        listView.setPrefHeight(260);
        listView.setMaxHeight(260);
        listView.setCellFactory(lv -> new AirportCell());

        VBox container = new VBox(8, searchField, listView);
        container.getStyleClass().add("airport-picker-popup");
        container.setPrefWidth(340);
        container.setPadding(new Insets(10));

        popup.getContent().add(container);

        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter(newText));

        // Keyboard navigation while typing in searchField
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                moveSelection(1);
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                moveSelection(-1);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                commitSelection();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                commitSelection();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1 && listView.getSelectionModel().getSelectedItem() != null) {
                commitSelection();
            }
        });
    }

    public void show(Node anchor, List<Airport> airports, Airport currentSelection, Consumer<Airport> onSelect) {
        if (anchor == null || anchor.getScene() == null || anchor.getScene().getWindow() == null) {
            return;
        }
        this.allAirports = airports != null ? List.copyOf(airports) : List.of();
        this.onSelect = onSelect;
        searchField.clear();
        applyFilter("");
        if (currentSelection != null && filteredItems.contains(currentSelection)) {
            listView.getSelectionModel().select(currentSelection);
            listView.scrollTo(currentSelection);
        } else if (!filteredItems.isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }

        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        Window window = anchor.getScene().getWindow();
        if (bounds != null) {
            popup.show(window, bounds.getMinX(), bounds.getMaxY() + 6);
        } else {
            popup.show(window);
        }
        searchField.requestFocus();
    }

    public void hide() {
        popup.hide();
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public TextField getSearchField() {
        return searchField;
    }

    public ListView<Airport> getListView() {
        return listView;
    }

    public void applyFilter(String query) {
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        List<Airport> matches = allAirports.stream()
                .filter(a -> q.isEmpty()
                        || a.code().toLowerCase(Locale.ROOT).contains(q)
                        || a.city().toLowerCase(Locale.ROOT).contains(q)
                        || a.name().toLowerCase(Locale.ROOT).contains(q)
                        || (a.country() != null && a.country().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
        filteredItems.setAll(matches);
        if (!filteredItems.isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }
    }

    private void moveSelection(int delta) {
        if (filteredItems.isEmpty()) return;
        int current = listView.getSelectionModel().getSelectedIndex();
        int next = Math.max(0, Math.min(filteredItems.size() - 1, current + delta));
        listView.getSelectionModel().select(next);
        listView.scrollTo(next);
    }

    public void commitSelection() {
        Airport selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null && !filteredItems.isEmpty()) {
            selected = filteredItems.get(0);
        }
        popup.hide();
        if (selected != null && onSelect != null) {
            onSelect.accept(selected);
        }
    }

    private static final class AirportCell extends ListCell<Airport> {
        private final HBox row = new HBox(10);
        private final VBox textCol = new VBox(2);
        private final Label cityLabel = new Label();
        private final Label nameLabel = new Label();
        private final Label codeBadge = new Label();

        AirportCell() {
            cityLabel.getStyleClass().add("airport-picker-city");
            nameLabel.getStyleClass().add("airport-picker-name");
            codeBadge.getStyleClass().add("airport-picker-code");
            textCol.getChildren().addAll(cityLabel, nameLabel);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(textCol, spacer, codeBadge);
            row.setPadding(new Insets(6, 8, 6, 8));
        }

        @Override
        protected void updateItem(Airport airport, boolean empty) {
            super.updateItem(airport, empty);
            if (empty || airport == null) {
                setText(null);
                setGraphic(null);
            } else {
                cityLabel.setText(airport.city());
                nameLabel.setText(airport.name());
                codeBadge.setText(airport.code());
                setText(null);
                setGraphic(row);
            }
        }
    }
}
