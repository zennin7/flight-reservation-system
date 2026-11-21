package com.group2.flightres.service;

import com.group2.flightres.dao.BookingDao;
import com.group2.flightres.model.BookingContext;
import com.group2.flightres.model.BookingRecord;
import com.group2.flightres.model.SeatClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardingPassVerificationServiceTest {

    private static BookingRecord makeRecord(long bookingId, String pnr, long flightId, String status) {
        BookingRecord rec = new BookingRecord();
        rec.setBookingId(bookingId);
        rec.setBookingReference(pnr);
        rec.setFlightId(flightId);
        rec.setFlightNumber("GRP 204");
        rec.setOrigin("Johannesburg (JNB)");
        rec.setDestination("Cape Town (CPT)");
        rec.setDepartureDate("2026-11-14");
        rec.setDepartureTime("06:30");
        rec.setArrivalTime("08:40");
        rec.setSeatLabel("6A");
        rec.setSeatClass(SeatClass.ECONOMY);
        rec.setPassengerName("Naledi Sithole");
        rec.setStatus(status);
        return rec;
    }

    @Test
    @DisplayName("Validates canonical signature, rejects tampered sig/PNR/flightId, and handles CANCELLED state")
    void verifiesValidTamperedAndCancelledPasses() {
        Map<String, BookingRecord> db = new ConcurrentHashMap<>();
        BookingRecord active = makeRecord(17L, "K7QX2M", 4L, "CONFIRMED");
        BookingRecord cancelled = makeRecord(18L, "CAN99Z", 4L, "CANCELLED");
        db.put("K7QX2M", active);
        db.put("CAN99Z", cancelled);

        BookingService bookingService = new BookingService(new BookingDao() {
            @Override
            public BookingRecord createBooking(BookingContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<BookingRecord> findByReference(String bookingRef) {
                return Optional.ofNullable(db.get(bookingRef));
            }

            @Override
            public List<BookingRecord> findByUserId(long userId) {
                return List.of();
            }
        });

        byte[] secret = "unit-test-qr-secret-32-bytes-key!".getBytes(StandardCharsets.UTF_8);
        BoardingPassVerificationService svc = new BoardingPassVerificationService(bookingService, secret);

        String url = svc.buildVerificationUrl("https://skyroyal.example/", active);
        assertTrue(url.startsWith("https://skyroyal.example/verify/K7QX2M?sig="));
        String validSig = url.substring(url.indexOf("sig=") + 4);

        var ok = svc.verify("K7QX2M", validSig);
        assertEquals(BoardingPassVerificationService.Status.VALID, ok.status());
        assertEquals("N***** S*****", ok.maskedPassengerName());

        // Tampered signature
        String badSig = (validSig.startsWith("A") ? "B" : "A") + validSig.substring(1);
        assertEquals(BoardingPassVerificationService.Status.INVALID, svc.verify("K7QX2M", badSig).status());

        // Unknown PNR
        assertEquals(BoardingPassVerificationService.Status.INVALID, svc.verify("NOPE99", validSig).status());

        // Signed with swapped flightId must fail for real flightId=4
        String wrongFlightSig = svc.sign("K7QX2M", 99L, 17L);
        assertEquals(BoardingPassVerificationService.Status.INVALID, svc.verify("K7QX2M", wrongFlightSig).status());

        // Signed with swapped PNR must fail
        String wrongPnrSig = svc.sign("OTHER1", 4L, 17L);
        assertEquals(BoardingPassVerificationService.Status.INVALID, svc.verify("K7QX2M", wrongPnrSig).status());

        // Cancelled pass with valid sig -> CANCELLED
        String cancelSig = svc.sign("CAN99Z", 4L, 18L);
        assertEquals(BoardingPassVerificationService.Status.CANCELLED, svc.verify("CAN99Z", cancelSig).status());
    }

    @Test
    @DisplayName("Persists generated random secret outside repo and reloads consistently")
    void persistsAndReloadsSecretFileConsistently(@TempDir Path tempDir) {
        Path secretPath = tempDir.resolve("qr-secret");
        byte[] first = BoardingPassVerificationService.loadOrCreateSecret(null, secretPath);
        assertEquals(32, first.length);
        assertTrue(Files.exists(secretPath));
        byte[] second = BoardingPassVerificationService.loadOrCreateSecret(null, secretPath);
        assertArrayEquals(first, second);
    }

    @Test
    @DisplayName("Source guard: BoardingPassVerificationService enforces constant-time MessageDigest.isEqual comparison")
    void sourceGuardConstantTimeComparison() throws Exception {
        Path src = Path.of("src/main/java/com/group2/flightres/service/BoardingPassVerificationService.java");
        if (!Files.exists(src)) {
            src = Path.of("core/src/main/java/com/group2/flightres/service/BoardingPassVerificationService.java");
        }
        String code = Files.readString(src, StandardCharsets.UTF_8);
        assertTrue(code.contains("MessageDigest.isEqual("),
                "Verification service MUST compare signatures using constant-time MessageDigest.isEqual");
    }
}
