package com.group2.flightres.service;

import com.group2.flightres.model.BookingRecord;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Signs and verifies boarding-pass verification URLs using HMAC-SHA256 and constant-time comparison.
 */
public class BoardingPassVerificationService {

    public static final String ENV_QR_SECRET = "SKY_ROYAL_QR_SECRET";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final int RANDOM_SECRET_BYTES = 32;

    public enum Status {
        VALID,
        CANCELLED,
        INVALID
    }

    public record VerifyResult(
            Status status,
            String pnr,
            String flightNumber,
            String origin,
            String destination,
            String departureDate,
            String departureTime,
            String arrivalTime,
            String seatLabel,
            String maskedPassengerName
    ) {
        public static VerifyResult invalid() {
            return new VerifyResult(Status.INVALID, null, null, null, null, null, null, null, null, null);
        }
    }

    private final BookingService bookingService;
    private final byte[] secretBytes;

    public BoardingPassVerificationService(BookingService bookingService) {
        this(bookingService, loadOrCreateDefaultSecret());
    }

    public BoardingPassVerificationService(BookingService bookingService, Path secretFilePath) {
        this(bookingService, loadOrCreateSecret(null, secretFilePath));
    }

    public BoardingPassVerificationService(BookingService bookingService, byte[] secretBytes) {
        this.bookingService = Objects.requireNonNull(bookingService, "bookingService must not be null");
        if (secretBytes == null || secretBytes.length == 0) {
            throw new IllegalArgumentException("QR signing secret must not be empty");
        }
        this.secretBytes = secretBytes.clone();
    }

    public static String canonicalPayload(String pnr, long flightId, long bookingId) {
        String cleanPnr = pnr == null ? "" : pnr.trim().toUpperCase(Locale.ROOT);
        return "v1|" + cleanPnr + "|" + flightId + "|" + bookingId;
    }

    public String sign(String pnr, long flightId, long bookingId) {
        String payload = canonicalPayload(pnr, flightId, bookingId);
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALG));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 signing unavailable", e);
        }
    }

    public String buildVerificationUrl(String baseUrl, BookingRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        String cleanBase = (baseUrl == null || baseUrl.isBlank())
                ? "http://localhost:8080"
                : baseUrl.trim().replaceAll("/+$", "");
        String pnr = record.getBookingReference().trim().toUpperCase(Locale.ROOT);
        long flightId = record.getFlightId() != null ? record.getFlightId() : 0L;
        String sig = sign(pnr, flightId, record.getBookingId());
        return cleanBase + "/verify/" + pnr + "?sig=" + sig;
    }

    public VerifyResult verify(String pnr, String signature) {
        if (pnr == null || pnr.isBlank() || signature == null || signature.isBlank()) {
            return VerifyResult.invalid();
        }
        String normalizedPnr = pnr.trim().toUpperCase(Locale.ROOT);
        Optional<BookingRecord> recordOpt = bookingService.getBookingByReference(normalizedPnr);
        if (recordOpt.isEmpty()) {
            return VerifyResult.invalid();
        }
        BookingRecord record = recordOpt.get();
        long flightId = record.getFlightId() != null ? record.getFlightId() : 0L;
        String expectedSig = sign(record.getBookingReference(), flightId, record.getBookingId());

        byte[] expectedBytes = expectedSig.getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = signature.trim().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            return VerifyResult.invalid();
        }

        String bookingStatus = record.getStatus() != null ? record.getStatus().trim().toUpperCase(Locale.ROOT) : "";
        Status state = ("CANCELLED".equals(bookingStatus) || "REFUNDED".equals(bookingStatus))
                ? Status.CANCELLED
                : Status.VALID;

        return new VerifyResult(
                state,
                record.getBookingReference(),
                record.getFlightNumber(),
                record.getOrigin(),
                record.getDestination(),
                record.getDepartureDate(),
                record.getDepartureTime(),
                record.getArrivalTime(),
                record.getSeatLabel(),
                maskPassengerName(record.getPassengerName())
        );
    }

    public static String maskPassengerName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "—";
        }
        String[] tokens = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            int cp = t.codePointAt(0);
            sb.appendCodePoint(cp).append("*****");
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private static byte[] loadOrCreateDefaultSecret() {
        String envSecret = System.getenv(ENV_QR_SECRET);
        Path defaultFile = Path.of(System.getProperty("user.home"), ".sky-royal", "qr-secret");
        return loadOrCreateSecret(envSecret, defaultFile);
    }

    static byte[] loadOrCreateSecret(String envOverride, Path secretFile) {
        if (envOverride != null && !envOverride.isBlank()) {
            return envOverride.trim().getBytes(StandardCharsets.UTF_8);
        }
        if (secretFile == null) {
            byte[] random = new byte[RANDOM_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        synchronized (BoardingPassVerificationService.class) {
            try {
                if (Files.exists(secretFile)) {
                    String existing = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
                    if (!existing.isEmpty()) {
                        return decodeStoredSecret(existing);
                    }
                }
                Path parent = secretFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                byte[] fresh = new byte[RANDOM_SECRET_BYTES];
                new SecureRandom().nextBytes(fresh);
                String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(fresh);
                try {
                    Files.writeString(secretFile, encoded + System.lineSeparator(), StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE_NEW);
                    return fresh;
                } catch (FileAlreadyExistsException race) {
                    String concurrent = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
                    if (!concurrent.isEmpty()) {
                        return decodeStoredSecret(concurrent);
                    }
                    return fresh;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not initialize QR secret outside repository", e);
            }
        }
    }

    private static byte[] decodeStoredSecret(String stored) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(stored);
            if (decoded.length >= 16) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return stored.getBytes(StandardCharsets.UTF_8);
    }
}
