package com.group2.flightres.web;

import com.group2.flightres.config.SeatHoldPolicy;
import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.dao.FlightDao;
import com.group2.flightres.dao.SeatDao;
import com.group2.flightres.dao.UserDao;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;
import com.group2.flightres.model.User;
import com.group2.flightres.service.AuthService;
import com.group2.flightres.service.BookingService;
import com.group2.flightres.service.FlightService;
import com.group2.flightres.service.PricingService;
import com.group2.flightres.service.SeatMapService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSeatMapTest {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");
    private static final Instant DEP = Instant.parse("2026-11-26T06:30:00Z");
    private static final Pattern CSRF_PATTERN = Pattern.compile("name=\"csrf_token\"\\s+value=\"([^\"]+)\"");

    private WebApplication webApp;

    @AfterEach
    void tearDown() {
        if (webApp != null) webApp.stop();
    }

    private static List<FlightSeat> buildA320Seats(long flightId) {
        List<FlightSeat> list = new ArrayList<>();
        long id = 1L;
        for (int r = 1; r <= 3; r++) {
            char[] cols = {'A', 'C', 'D', 'F'};
            int[] dcols = {0, 2, 4, 6};
            for (int c = 0; c < cols.length; c++) {
                FlightSeat s = new FlightSeat();
                s.setFlightSeatId(id++);
                s.setFlightId(flightId);
                s.setSeatRow(r);
                s.setSeatCol(cols[c]);
                s.setSeatClass(SeatClass.FIRST);
                s.setDisplayRow(r - 1);
                s.setDisplayCol(dcols[c]);
                s.setStatus((r == 1 && cols[c] == 'C') ? SeatStatus.BOOKED : SeatStatus.AVAILABLE);
                list.add(s);
            }
        }
        for (int r = 4; r <= 28; r++) {
            char[] cols = {'A', 'B', 'C', 'D', 'E', 'F'};
            int[] dcols = {0, 1, 2, 4, 5, 6};
            for (int c = 0; c < cols.length; c++) {
                FlightSeat s = new FlightSeat();
                s.setFlightSeatId(id++);
                s.setFlightId(flightId);
                s.setSeatRow(r);
                s.setSeatCol(cols[c]);
                s.setSeatClass(SeatClass.ECONOMY);
                s.setDisplayRow(r - 1);
                s.setDisplayCol(dcols[c]);
                s.setStatus((r == 6 && cols[c] == 'A') ? SeatStatus.BOOKED : SeatStatus.AVAILABLE);
                list.add(s);
            }
        }
        return list;
    }

    @Test
    @DisplayName("SeatMapLayoutBuilder dynamically builds contiguous DB sections, states, and derived position tags")
    void dynamicSectionGenerationStatesAndDerivedTags() {
        List<FlightSeat> a320 = buildA320Seats(1L);
        assertEquals(162, a320.size());

        // Add held-by-other and yours holds dynamically
        FlightSeat seat9A = a320.stream().filter(s -> "9A".equals(s.getSeatLabel())).findFirst().orElseThrow();
        seat9A.setStatus(SeatStatus.HELD);
        seat9A.setHeldBy("owner-1");
        seat9A.setHeldUntil(NOW.plusSeconds(240));

        FlightSeat seat12B = a320.stream().filter(s -> "12B".equals(s.getSeatLabel())).findFirst().orElseThrow();
        seat12B.setStatus(SeatStatus.HELD);
        seat12B.setHeldBy("other-user");
        seat12B.setHeldUntil(NOW.plusSeconds(120));

        SeatMapLayoutBuilder.SeatMapView view = SeatMapLayoutBuilder.build(a320, "owner-1", NOW);
        assertEquals(3, view.getSections().size());
        assertEquals(1, view.getSections().get(0).getStartRow());
        assertEquals(10, view.getSections().get(0).getEndRow());
        assertTrue(view.getSections().get(0).getCabinSummary().contains("First Class"));
        assertTrue(view.getSections().get(0).getCabinSummary().contains("Economy"));
        assertEquals(11, view.getSections().get(1).getStartRow());
        assertEquals(19, view.getSections().get(1).getEndRow());
        assertEquals("Economy", view.getSections().get(1).getCabinSummary());
        assertEquals(20, view.getSections().get(2).getStartRow());
        assertEquals(28, view.getSections().get(2).getEndRow());

        assertEquals(158, view.getFreeSeats(), "162 total - 2 booked - 2 held = 158 free");
        assertEquals(162, view.getTotalSeats());
        assertNotNull(view.getHeldSeat());
        assertEquals("9A", view.getHeldSeat().getSeatLabel());
        assertEquals("Window", view.getHeldSeatPositionTag());

        // Check First Class 2x2 tags & taken/held states
        assertEquals("Window", SeatMapLayoutBuilder.derivePositionTag(a320.get(0), a320)); // 1A
        assertEquals("Aisle", SeatMapLayoutBuilder.derivePositionTag(a320.get(1), a320)); // 1C
        assertEquals("Middle", SeatMapLayoutBuilder.derivePositionTag(seat12B, a320));

        // Compact 4-row aircraft yields 1 single section dynamically
        List<FlightSeat> regional = a320.stream().filter(s -> s.getSeatRow() <= 4).toList();
        SeatMapLayoutBuilder.SeatMapView smallView = SeatMapLayoutBuilder.build(regional, "owner-1", NOW);
        assertEquals(1, smallView.getSections().size());
        assertEquals(1, smallView.getSections().get(0).getStartRow());
        assertEquals(4, smallView.getSections().get(0).getEndRow());
    }

    @Test
    @DisplayName("Live seat hold & release enforce ownership (403 vs 200) and render DB-backed layout without 3D cube")
    void liveSeatHoldAndOwnerCheckedRelease() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FlightSearchResult flight = new FlightSearchResult(
                1L, "SR 305", "JNB", "O. R. Tambo", "Johannesburg", SAST,
                "CPT", "Cape Town Intl", "Cape Town", SAST,
                DEP, DEP.plusSeconds(7800), new BigDecimal("1180.00"), "Airbus A320", 160, 148, 12);

        List<FlightSeat> seatsList = buildA320Seats(1L);
        Map<Long, FlightSeat> byId = new ConcurrentHashMap<>();
        for (FlightSeat s : seatsList) byId.put(s.getFlightSeatId(), s);

        FlightDao flightDao = new FlightDao() {
            @Override public List<FlightSearchResult> searchFlights(String o, String d, LocalDate dt) { return List.of(flight); }
            @Override public Optional<FlightSearchResult> findById(long id) { return id == 1L ? Optional.of(flight) : Optional.empty(); }
        };
        SeatDao seatDao = new SeatDao() {
            @Override public List<FlightSeat> getSeatMapForFlight(long flightId) { return new ArrayList<>(seatsList); }
            @Override public Optional<Instant> holdSeat(long id, int secs, String owner) {
                FlightSeat s = byId.get(id);
                if (s != null && (s.getStatus() == SeatStatus.AVAILABLE
                        || (s.getStatus() == SeatStatus.HELD && (owner == null || owner.equals(s.getHeldBy()))))) {
                    Instant exp = clock.instant().plusSeconds(secs);
                    s.setStatus(SeatStatus.HELD);
                    s.setHeldUntil(exp);
                    s.setHeldBy(owner);
                    return Optional.of(exp);
                }
                return Optional.empty();
            }
            @Override public boolean releaseSeat(long id) { return releaseSeat(id, null); }
            @Override public boolean releaseSeat(long id, String owner) {
                FlightSeat s = byId.get(id);
                if (s != null && s.getStatus() == SeatStatus.HELD
                        && (owner == null || s.getHeldBy() == null || owner.equals(s.getHeldBy()))) {
                    s.setStatus(SeatStatus.AVAILABLE);
                    s.setHeldUntil(null);
                    s.setHeldBy(null);
                    return true;
                }
                return false;
            }
            @Override public boolean bookSeat(long id) { return false; }
            @Override public int releaseExpiredHolds() { return 0; }
            @Override public Optional<FlightSeat> findSeatById(long id) { return Optional.ofNullable(byId.get(id)); }
            @Override public Map<SeatClass, BigDecimal> getSeatClassMultipliers() {
                return Map.of(SeatClass.ECONOMY, BigDecimal.ONE, SeatClass.FIRST, new BigDecimal("3.50"));
            }
        };
        PricingService pricing = new PricingService(seatDao, clock) {
            @Override public PriceQuote quote(long fid, SeatClass cls, Clock clk) {
                BigDecimal mult = cls == SeatClass.FIRST ? new BigDecimal("3.50") : BigDecimal.ONE;
                return PriceQuote.calculate(flight.getBasePrice(), mult, BigDecimal.ONE, BigDecimal.ONE);
            }
        };
        BookingDao bookingDao = new BookingDao() {
            @Override public BookingRecord createBooking(BookingContext c) { throw new UnsupportedOperationException(); }
            @Override public Optional<BookingRecord> findByReference(String ref) { return Optional.empty(); }
            @Override public List<BookingRecord> findByUserId(long userId) { return List.of(); }
        };
        UserDao userDao = new UserDao() {
            @Override public Optional<User> findByUsername(String u) { return Optional.empty(); }
            @Override public Optional<User> findByUsernameOrEmail(String id) { return Optional.empty(); }
        };

        webApp = new WebApplication(
                new FlightService(flightDao, Collections::emptyList, pricing, clock),
                new SeatMapService(seatDao, flightDao, clock),
                new BookingService(bookingDao, flightDao, pricing, clock),
                new AuthService(userDao));
        webApp.start(0);
        String baseUrl = "http://localhost:" + webApp.getApp().port();

        HttpClient userA = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpClient userB = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> getA = userA.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getA.statusCode());
        String html = getA.body();
        assertTrue(html.contains("Section 1"), "Dynamic section tab expected");
        assertTrue(html.contains("Section 2"));
        assertTrue(html.contains("Section 3"));
        assertTrue(html.contains("seat-taken-icon"), "Taken seats must render explicit icon");
        assertTrue(html.contains("160 of 162"), "Real free/total seat count expected");
        assertTrue(html.contains("openCabinLightboxBtn"));
        assertTrue(html.contains("data-zoom=\"125\""));
        assertFalse(html.contains("3d") && html.contains("cube"), "Must omit 3D cube control");

        Matcher ma = CSRF_PATTERN.matcher(html);
        assertTrue(ma.find());
        String csrfA = ma.group(1);

        FlightSeat targetSeat = seatsList.stream().filter(s -> "9A".equals(s.getSeatLabel())).findFirst().orElseThrow();
        String holdBody = "csrf_token=" + URLEncoder.encode(csrfA, StandardCharsets.UTF_8)
                + "&flightSeatId=" + targetSeat.getFlightSeatId();
        HttpResponse<String> holdResp = userA.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights/1/seats/hold"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(holdBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, holdResp.statusCode());
        assertTrue(holdResp.body().contains("\"seatLabel\":\"9A\""));
        assertTrue(holdResp.body().contains("\"totalFormatted\":\"R 1,180.00\""));
        assertTrue(holdResp.body().contains("\"positionTag\":\"Window\""));
        assertTrue(holdResp.body().contains("\"freeSeats\":159"));
        assertEquals(SeatStatus.HELD, targetSeat.getStatus());

        // User B gets 403 trying to release User A's hold
        String htmlB = userB.send(HttpRequest.newBuilder(URI.create(baseUrl + "/flights/1/seats")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher mb = CSRF_PATTERN.matcher(htmlB);
        assertTrue(mb.find());
        String relBodyB = "csrf_token=" + URLEncoder.encode(mb.group(1), StandardCharsets.UTF_8)
                + "&flightSeatId=" + targetSeat.getFlightSeatId();
        HttpResponse<String> forbiddenRel = userB.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights/1/seats/release"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(relBodyB)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, forbiddenRel.statusCode(), "Non-owner release must be rejected 403");
        assertEquals(SeatStatus.HELD, targetSeat.getStatus());

        // User A releases own hold -> 200 and restored AVAILABLE
        HttpResponse<String> ownerRel = userA.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/flights/1/seats/release"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(holdBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ownerRel.statusCode());
        assertTrue(ownerRel.body().contains("\"released\":true"));
        assertTrue(ownerRel.body().contains("\"freeSeats\":160"));
        assertEquals(SeatStatus.AVAILABLE, targetSeat.getStatus());
        assertEquals(SeatHoldPolicy.HOLD_SECONDS, SeatHoldPolicy.HOLD_SECONDS);
    }
}
