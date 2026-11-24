package com.group2.flightres.controller;

import com.group2.flightres.component.GlassTopBar;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.Route;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.util.Motion;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Sky Royal 01 Home screen.
 */
public class HomeController implements WizardStepController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @FXML private GlassTopBar topBar;
    @FXML private ComboBox<Airport> fromCombo;
    @FXML private ComboBox<Airport> toCombo;
    @FXML private DatePicker departureDatePicker;
    @FXML private DatePicker returnDatePicker;
    @FXML private ComboBox<String> passengersCombo;
    @FXML private Button searchFlightsButton;

    private final FlightService flightService;
    private BookingContext bookingContext;
    private Runnable onNext;

    public HomeController() {
        this(new FlightService());
    }

    public HomeController(FlightService flightService) {
        this.flightService = flightService != null ? flightService : new FlightService();
    }

    @Override
    public void init(BookingContext context, Runnable onNext) {
        this.bookingContext = context;
        this.onNext = onNext;
        if (topBar != null) {
            topBar.bind(context);
        }
    }

    @FXML
    public void initialize() {
        List<Airport> airports = flightService.listAirports();

        StringConverter<Airport> airportConverter = new StringConverter<>() {
            @Override
            public String toString(Airport airport) {
                return airport != null ? airport.city() + " (" + airport.code() + ")" : "";
            }

            @Override
            public Airport fromString(String string) {
                return null;
            }
        };

        if (fromCombo != null) {
            fromCombo.setConverter(airportConverter);
            fromCombo.getItems().setAll(airports);
        }

        if (toCombo != null) {
            toCombo.setConverter(airportConverter);
        }

        if (fromCombo != null && toCombo != null) {
            fromCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    List<Airport> filteredTo = airports.stream()
                            .filter(a -> !a.equals(newVal))
                            .toList();
                    Airport currentTo = toCombo.getValue();
                    toCombo.getItems().setAll(filteredTo);
                    if (filteredTo.contains(currentTo)) {
                        toCombo.setValue(currentTo);
                    } else if (!filteredTo.isEmpty()) {
                        toCombo.setValue(filteredTo.get(0));
                    }
                }
            });
        }

        if (departureDatePicker != null) {
            departureDatePicker.setDayCellFactory(picker -> new DateCell() {
                @Override
                public void updateItem(LocalDate item, boolean empty) {
                    super.updateItem(item, empty);
                    ZoneId zone = (fromCombo != null && fromCombo.getValue() != null)
                            ? fromCombo.getValue().zoneId()
                            : ZoneId.of("Africa/Johannesburg");
                    LocalDate today = LocalDate.now(zone);
                    if (item != null && item.isBefore(today)) {
                        setDisable(true);
                        setStyle("-fx-opacity: 0.4;");
                    }
                }
            });
        }

        // Initial selection from defaultRoute
        Optional<Route> defRoute = flightService.defaultRoute();
        if (defRoute.isPresent()) {
            Airport origin = defRoute.get().origin();
            Airport dest = defRoute.get().destination();
            if (fromCombo != null) {
                fromCombo.setValue(origin);
            }
            if (toCombo != null) {
                toCombo.getItems().setAll(airports.stream().filter(a -> !a.equals(origin)).toList());
                toCombo.setValue(dest);
            }
            if (departureDatePicker != null) {
                LocalDate initialDate = flightService.nextAvailableDate(defRoute.get())
                        .orElseGet(() -> LocalDate.now(origin.zoneId()).plusDays(1));
                departureDatePicker.setValue(initialDate);
            }
        } else if (airports.size() >= 2) {
            Airport origin = airports.get(0);
            Airport dest = airports.get(1);
            if (fromCombo != null) fromCombo.setValue(origin);
            if (toCombo != null) {
                toCombo.getItems().setAll(airports.stream().filter(a -> !a.equals(origin)).toList());
                toCombo.setValue(dest);
            }
            if (departureDatePicker != null) {
                departureDatePicker.setValue(LocalDate.now(origin.zoneId()).plusDays(1));
            }
        }

        if (passengersCombo != null) {
            passengersCombo.getItems().addAll("1 Adult", "2 Adults", "3 Adults", "4 Adults");
            passengersCombo.setValue("1 Adult");
        }

        if (searchFlightsButton != null) {
            searchFlightsButton.setOnAction(e -> handleSearch());
            Motion.attachHoverLift(searchFlightsButton);
        }
    }

    @FXML
    private void handleSearch() {
        logger.info("Search Flights clicked from Home screen.");
        if (bookingContext != null) {
            if (fromCombo != null && fromCombo.getValue() != null) {
                Airport from = fromCombo.getValue();
                bookingContext.setOrigin(from.city() + " (" + from.code() + ")");
                bookingContext.setOriginCity(from.city());
            }
            if (toCombo != null && toCombo.getValue() != null) {
                Airport to = toCombo.getValue();
                bookingContext.setDestination(to.city() + " (" + to.code() + ")");
                bookingContext.setDestinationCity(to.city());
            }
            if (departureDatePicker != null && departureDatePicker.getValue() != null) {
                bookingContext.setDepartureDate(departureDatePicker.getValue().toString());
            }
        }
        if (onNext != null) {
            onNext.run();
        }
    }
}
