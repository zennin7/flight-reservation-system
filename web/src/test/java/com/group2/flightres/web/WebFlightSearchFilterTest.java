package com.group2.flightres.web;

import com.group2.flightres.config.AppInfo;
import com.group2.flightres.dao.AirportDao;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.Airport;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.CancellationPolicy;
import com.group2.flightres.service.FlightSalesPolicy;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFlightSearchFilterTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final ZoneId ZRH = ZoneId.of("Europe/Zurich");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-23T08:00:00Z"), ZoneOffset.UTC);

    private WebApplication webApp;

    private static List<FlightSearchResult> sampleDayFlights() {
        return List.of(
                new FlightSearchResult(
                        101L, "SR 300", "JNB", "O. R. Tambo International", "Johannesburg", SAST,
                        "CPT", "Cape Town International", "Cape Town", SAST,
                        Instant.parse("2026-11-25T03:30:00Z"), Instant.parse("2026-11-25T05:40:00Z"),
                        new BigDecimal("950.00"), "Airbus A320", 28, 24, 4
                ),
                new FlightSearchResult(
                        102L, "SR 301", "JNB", "O. R. Tambo International", "Johannesburg", SAST,
                        "CPT", "Cape Town International", "Cape Town", SAST,
                        Instant.parse("2026-11-25T04:30:00Z"), Instant.parse("2026-11-25T06:40:00Z"),
                        new BigDecimal("1200.00"), "Airbus A320", 34, 30, 4
                ),
                new FlightSearchResult(
                        103L, "SR 305", "JNB", "O. R. Tambo International", "Johannesburg", SAST,
                        "CPT", "Cape Town International", "Cape Town", SAST,
                        Instant.parse("2026-11-25T11:15:00Z"), Instant.parse("2026-11-25T13:20:00Z"),
                        new BigDecimal("1380.00"), "Airbus A320", 6, 6, 0
                ),
                new FlightSearchResult(
                        104L, "SR 309", "JNB", "O. R. Tambo International", "Johannesburg", SAST,
                        "CPT", "Cape Town International", "Cape Town", SAST,
                        Instant.parse("2026-11-25T17:45:00Z"), Instant.parse("2026-11-25T19:55:00Z"),
                        new BigDecimal("1600.00"), "Airbus A320", 0, 0, 0
                )
        );
    }

    private static List<FlightSearchResult> bulk105Flights() {
        List<FlightSearchResult> list = new ArrayList<>(105);
        Instant baseDep = Instant.parse("2026-11-26T04:00:00Z");
        for (int i = 1; i <= 105; i++) {
            Instant dep = baseDep.plusSeconds(i * 300L);
            Instant arr = dep.plusSeconds(7500L);
            list.add(new FlightSearchResult(
                    2000L + i, "SR " + (400 + i),
                    "JNB", "O. R. Tambo International", "Johannesburg", SAST,
                    "CPT", "Cape Town International", "Cape Town", SAST,
                    dep, arr,
                    new BigDecimal(1000 + i), "Airbus A320", 20, 16, 4
            ));
        }
        return list;
    }

    private String startTestServer() {
        List<Airport> airports = List.of(
                new Airport("JNB", "O. R. Tambo International", "Johannesburg", "South Africa", SAST),
                new Airport("CPT", "Cape Town International", "Cape Town", "South Africa", SAST),
                new Airport("DUR", "King Shaka International", "Durban", "South Africa", SAST)
        );
        AirportDao airportDao = () -> airports;
        List<FlightSearchResult> populated = sampleDayFlights();
        List<FlightSearchResult> bulk = bulk105Flights();

        FlightDao flightDao = new FlightDao() {
            @Override
            public List<FlightSearchResult> searchFlights(String origin, String destination, LocalDate departureDate) {
                if (LocalDate.of(2026, 11, 25).equals(departureDate)) {
                    return populated;
                }
                if (LocalDate.of(2026, 11, 26).equals(departureDate)) {
                    return bulk;
                }
                return List.of();
            }

            @Override
            public Optional<FlightSearchResult> findById(long flightId) {
                return populated.stream().filter(f -> f.getFlightId() == flightId).findFirst();
            }

            @Override
            public Optional<LocalDate> findNextAvailableDate(String origin, String destination, LocalDate fromDate, Instant minDep) {
                if (fromDate != null && !fromDate.isAfter(LocalDate.of(2026, 11, 28))) {
                    return Optional.of(LocalDate.of(2026, 11, 28));
                }
                return Optional.empty();
            }
        };

        SeatDao seatDao = new SeatDao() {
            @Override public List<FlightSeat> getSeatMapForFlight(long flightId) { return List.of(); }
            @Override public boolean releaseSeat(long flightSeatId) { return false; }
            @Override public boolean bookSeat(long flightSeatId) { return false; }
            @Override public int releaseExpiredHolds() { return 0; }
            @Override public Optional<FlightSeat> findSeatById(long flightSeatId) { return Optional.empty(); }
        };

        PricingService pricingService = new PricingService(seatDao, FIXED_CLOCK) {
            @Override
            public Map<Long, PriceQuote> quoteBatch(List<Long> flightIds, SeatClass seatClass, Clock clk) {
                Map<Long, PriceQuote> out = new LinkedHashMap<>();
                BigDecimal classMult = seatClass == SeatClass.FIRST ? new BigDecimal("3.50") : new BigDecimal("1.00");
                for (Long fid : flightIds) {
                    BigDecimal base = BigDecimal.valueOf(1200);
                    if (fid == 101L) base = new BigDecimal("950.00");
                    else if (fid == 102L) base = new BigDecimal("1200.00");
                    else if (fid == 103L) base = new BigDecimal("1380.00");
                    else if (fid == 104L) base = new BigDecimal("1600.00");
                    else if (fid >= 2000L) base = BigDecimal.valueOf(1000 + (fid - 2000));
                    BigDecimal demandMult = (fid == 102L) ? new BigDecimal("1.07") : new BigDecimal("1.00");
                    out.put(fid, PriceQuote.calculate(base, classMult, demandMult, new BigDecimal("1.00")));
                }
                return out;
            }
        };

        BookingDao bookingDao = new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext context) { throw new UnsupportedOperationException(); }
            @Override public Optional<BookingRecord> findByReference(String bookingRef) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
        };

        UserDao userDao = new UserDao() {
            @Override public Optional<User> findByUsername(String username) { return Optional.empty(); }
            @Override public Optional<User> findByUsernameOrEmail(String identifier) { return Optional.empty(); }
        };

        FlightService flightService = new FlightService(flightDao, airportDao, pricingService, FIXED_CLOCK);
        SeatMapService seatMapService = new SeatMapService(seatDao, flightDao, FIXED_CLOCK);
        BookingService bookingService = new BookingService(bookingDao, flightDao, FIXED_CLOCK);
        AuthService authService = new AuthService(userDao);

        webApp = new WebApplication(flightService, seatMapService, bookingService, authService);
        webApp.start(0);
        return "http://localhost:" + webApp.getApp().port();
    }

    @AfterEach
    void stopServer() {
        if (webApp != null) {
            webApp.stop();
        }
    }

    @Test
    @DisplayName("FlightSearchFilter resolves airport-local departure slots, LOW_SEATS_THRESHOLD badges, filters, and 100+ pagination")
    void unitFilterSlotsBadgesAndPagination() {
        Instant instant0430Utc = Instant.parse("2026-11-25T04:30:00Z");
        assertEquals(FlightSearchFilter.SLOT_BEFORE_6, FlightSearchFilter.resolveDepartureSlot(instant0430Utc, ZRH));
        assertEquals(FlightSearchFilter.SLOT_6_TO_12, FlightSearchFilter.resolveDepartureSlot(instant0430Utc, SAST));
        assertEquals(FlightSearchFilter.SLOT_12_TO_18,
                FlightSearchFilter.resolveDepartureSlot(Instant.parse("2026-11-25T11:15:00Z"), SAST));
        assertEquals(FlightSearchFilter.SLOT_AFTER_18,
                FlightSearchFilter.resolveDepartureSlot(Instant.parse("2026-11-25T17:45:00Z"), SAST));

        List<FlightSearchFilter.FlightCardView> cards = FlightSearchFilter.buildCardViews(
                sampleDayFlights(),
                Map.of(102L, PriceQuote.calculate(new BigDecimal("1200"), BigDecimal.ONE, new BigDecimal("1.07"), BigDecimal.ONE)),
                Map.of(102L, PriceQuote.calculate(new BigDecimal("1200"), new BigDecimal("3.50"), new BigDecimal("1.07"), BigDecimal.ONE)),
                "ECONOMY",
                new CancellationPolicy(FIXED_CLOCK)
        );
        assertEquals(4, cards.size());
        assertEquals("is-good", cards.get(1).getSeatBadgeClass());
        assertEquals("34 seats left", cards.get(1).getSeatBadgeText());
        assertTrue(FlightSalesPolicy.LOW_SEATS_THRESHOLD == 10);
        assertEquals("is-low", cards.get(2).getSeatBadgeClass());
        assertEquals("Only 6 left", cards.get(2).getSeatBadgeText());
        assertEquals("is-soldout", cards.get(3).getSeatBadgeClass());
        assertEquals("Sold out", cards.get(3).getSeatBadgeText());

        FlightSearchFilter.FilterResult cabinFiltered = FlightSearchFilter.filterAndPaginate(
                cards, Set.of(), null, Set.of("FIRST"), 1, FlightSearchFilter.DEFAULT_PAGE_SIZE);
        assertEquals(2, cabinFiltered.getTotalMatched());

        List<FlightSearchFilter.FlightCardView> bulkCards = FlightSearchFilter.buildCardViews(
                bulk105Flights(), Map.of(), Map.of(), "ECONOMY", new CancellationPolicy(FIXED_CLOCK));
        FlightSearchFilter.FilterResult page6 = FlightSearchFilter.filterAndPaginate(
                bulkCards, Set.of(), null, Set.of(), 6, FlightSearchFilter.DEFAULT_PAGE_SIZE);
        assertEquals(105, page6.getTotalMatched());
        assertEquals(6, page6.getTotalPages());
        assertEquals(101, page6.getPageStart());
        assertEquals(105, page6.getPageEnd());
        assertEquals(5, page6.getPageCards().size());
    }

    @Test
    @DisplayName("/flights renders board-2 workspace, policy details, filters, empty/error states, and 100+ pagination")
    void liveFlightsSearchFiltersAndStates() throws Exception {
        String baseUrl = startTestServer();
        HttpClient http = HttpClient.newHttpClient();

        // 1. Default populated results
        HttpResponse<String> popResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=Johannesburg+%28JNB%29&destination=Cape+Town+%28CPT%29&date=2026-11-25&cabin=ECONOMY"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, popResp.statusCode());
        String html = popResp.body();
        assertTrue(html.contains("Flights from Johannesburg to Cape Town"));
        assertTrue(html.contains("Showing 4 flights"));
        assertTrue(html.contains(AppInfo.NAME + " · SR 301"));
        assertTrue(html.contains("34 seats left"));
        assertTrue(html.contains("Only 6 left"));
        assertTrue(html.contains("R 1,284"));
        assertTrue(html.contains("R 3,325"));
        assertTrue(html.contains("Economy from"));

        HttpResponse<String> firstCabinResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=Johannesburg+%28JNB%29&destination=Cape+Town+%28CPT%29&date=2026-11-25&cabin=FIRST"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, firstCabinResp.statusCode());
        assertTrue(firstCabinResp.body().contains("First from"));
        assertTrue(firstCabinResp.body().contains("R 4,494"));
        assertTrue(html.contains("Flight information"));
        assertTrue(html.contains("Date details"));
        assertTrue(html.contains("Baggage rules"));
        assertTrue(html.contains("Cancellation rules"));
        assertTrue(html.contains("23 kg checked"));
        assertTrue(html.contains("Closes 30 min before"));
        assertTrue(html.contains("60 min before"));
        assertTrue(html.contains("Until 24 hours before departure"));
        assertTrue(html.contains("100% full refund"));
        assertTrue(html.contains(">Select flight</a>"));
        assertTrue(html.contains("data-server-filtered=\"false\""));
        assertFalse(html.contains("Instant EFT"));

        // 2. Server-side departure + cabin + maxPrice filters
        HttpResponse<String> filteredResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=JNB&destination=CPT&date=2026-11-25&dep=6to12&maxPrice=1300&cabinFilter=FIRST"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, filteredResp.statusCode());
        String fHtml = filteredResp.body();
        assertTrue(fHtml.contains("Showing 1 flight"));
        assertTrue(fHtml.contains("SR 301"));
        assertFalse(fHtml.contains("SR 305"));
        assertTrue(fHtml.contains("data-server-filtered=\"true\""));

        // 3. Filter empty state (unfiltered > 0, filtered == 0)
        HttpResponse<String> filterEmptyResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=JNB&destination=CPT&date=2026-11-25&dep=12to18&cabinFilter=FIRST"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, filterEmptyResp.statusCode());
        String feHtml = filterEmptyResp.body();
        assertTrue(feHtml.contains("Showing 0 flights"));
        assertTrue(feHtml.contains("No flights match your active filters"));

        // 4. Empty date state with next available date
        HttpResponse<String> emptyDateResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=JNB&destination=CPT&date=2026-11-27"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, emptyDateResp.statusCode());
        String edHtml = emptyDateResp.body();
        assertTrue(edHtml.contains("No scheduled flights for this date"));
        assertTrue(edHtml.contains("Next available: 2026-11-28"));

        // 5. Validation error state (origin == destination)
        HttpResponse<String> errResp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=Johannesburg+%28JNB%29&destination=Johannesburg+%28JNB%29&date=2026-11-25"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, errResp.statusCode());
        assertTrue(errResp.body().contains("Search error:"));

        // 6. 105-flight pagination state
        HttpResponse<String> page1Resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=JNB&destination=CPT&date=2026-11-26&page=1"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, page1Resp.statusCode());
        String p1Html = page1Resp.body();
        assertTrue(p1Html.contains("Showing 1\u201320 of 105 flights"));
        assertTrue(p1Html.contains("Page 1 of 6"));

        HttpResponse<String> page6Resp = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights?origin=JNB&destination=CPT&date=2026-11-26&page=6"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, page6Resp.statusCode());
        String p6Html = page6Resp.body();
        assertTrue(p6Html.contains("Showing 101\u2013105 of 105 flights"));
        assertTrue(p6Html.contains("Page 6 of 6"));
    }
}
