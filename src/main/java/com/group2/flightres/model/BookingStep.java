package com.group2.flightres.model;

public enum BookingStep {
    SEARCH(0, "Search", "/com/group2/flightres/fxml/flight-search.fxml"),
    SEAT_SELECTION(1, "Select Seat", "/com/group2/flightres/fxml/seat-map.fxml"),
    CHECKOUT(2, "Checkout", "/com/group2/flightres/fxml/checkout.fxml"),
    CONFIRMATION(3, "Confirmed", "/com/group2/flightres/fxml/booking-confirmation.fxml");

    private final int order;
    private final String displayName;
    private final String fxmlPath;

    BookingStep(int order, String displayName, String fxmlPath) {
        this.order = order;
        this.displayName = displayName;
        this.fxmlPath = fxmlPath;
    }

    public int getOrder() { return order; }
    public String getDisplayName() { return displayName; }
    public String getFxmlPath() { return fxmlPath; }

    public BookingStep next() {
        BookingStep[] values = values();
        return (order + 1 < values.length) ? values[order + 1] : this;
    }
}

