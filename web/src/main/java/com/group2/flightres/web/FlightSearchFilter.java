package com.group2.flightres.web;

import com.group2.flightres.config.BaggagePolicy;
import com.group2.flightres.config.BoardingPolicy;
import com.group2.flightres.model.FlightSearchResult;
import com.group2.flightres.model.PriceQuote;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.service.CancellationPolicy;
import com.group2.flightres.service.FlightSalesPolicy;
import com.group2.flightres.util.FlightTimes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prepares flight search result view models, policy metadata, server-side filtering,
 * and pagination for the Flight Search page.
 */
public final class FlightSearchFilter {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private static final BigDecimal DEFAULT_FIRST_MULTIPLIER = new BigDecimal("3.50");

    public static final String SLOT_BEFORE_6 = "before6";
    public static final String SLOT_6_TO_12 = "6to12";
    public static final String SLOT_12_TO_18 = "12to18";
    public static final String SLOT_AFTER_18 = "after18";

    private FlightSearchFilter() {}

    public static final class FlightCardView {
        private final FlightSearchResult flight;
        private final BigDecimal economyPrice;
        private final BigDecimal firstPrice;
        private final BigDecimal displayPrice;
        private final String displayCabinLabel;
        private final String displayPriceFormatted;
        private final int displaySeatsLeft;
        private final boolean lowSeats;
        private final boolean soldOut;
        private final String seatBadgeText;
        private final String seatBadgeClass;
        private final String depSlot;
        private final String aircraftLayout;
        private final String departureDateFull;
        private final String arrivalDateFull;
        private final String checkinOpensText;
        private final String bagDropCloseText;
        private final int boardingCloseMins;
        private final int salesCloseMins;
        private final int checkedBagKg;
        private final String gateNote;
        private final long freeCancelHours;
        private final String cancellationSummary;

        public FlightCardView(FlightSearchResult flight, BigDecimal economyPrice, BigDecimal firstPrice,
                              BigDecimal displayPrice, String displayCabinLabel, String displayPriceFormatted,
                              int displaySeatsLeft, boolean lowSeats, boolean soldOut,
                              String seatBadgeText, String seatBadgeClass, String depSlot, String aircraftLayout,
                              String departureDateFull, String arrivalDateFull,
                              String checkinOpensText, String bagDropCloseText,
                              int boardingCloseMins, int salesCloseMins, int checkedBagKg,
                              String gateNote, long freeCancelHours, String cancellationSummary) {
            this.flight = flight;
            this.economyPrice = economyPrice;
            this.firstPrice = firstPrice;
            this.displayPrice = displayPrice;
            this.displayCabinLabel = displayCabinLabel;
            this.displayPriceFormatted = displayPriceFormatted;
            this.displaySeatsLeft = displaySeatsLeft;
            this.lowSeats = lowSeats;
            this.soldOut = soldOut;
            this.seatBadgeText = seatBadgeText;
            this.seatBadgeClass = seatBadgeClass;
            this.depSlot = depSlot;
            this.aircraftLayout = aircraftLayout;
            this.departureDateFull = departureDateFull;
            this.arrivalDateFull = arrivalDateFull;
            this.checkinOpensText = checkinOpensText;
            this.bagDropCloseText = bagDropCloseText;
            this.boardingCloseMins = boardingCloseMins;
            this.salesCloseMins = salesCloseMins;
            this.checkedBagKg = checkedBagKg;
            this.gateNote = gateNote;
            this.freeCancelHours = freeCancelHours;
            this.cancellationSummary = cancellationSummary;
        }

        public FlightSearchResult getFlight() { return flight; }
        public BigDecimal getEconomyPrice() { return economyPrice; }
        public BigDecimal getFirstPrice() { return firstPrice; }
        public BigDecimal getDisplayPrice() { return displayPrice; }
        public String getDisplayCabinLabel() { return displayCabinLabel; }
        public String getDisplayPriceFormatted() { return displayPriceFormatted; }
        public int getDisplaySeatsLeft() { return displaySeatsLeft; }
        public boolean isLowSeats() { return lowSeats; }
        public boolean isSoldOut() { return soldOut; }
        public String getSeatBadgeText() { return seatBadgeText; }
        public String getSeatBadgeClass() { return seatBadgeClass; }
        public String getDepSlot() { return depSlot; }
        public String getAircraftLayout() { return aircraftLayout; }
        public String getDepartureDateFull() { return departureDateFull; }
        public String getArrivalDateFull() { return arrivalDateFull; }
        public String getCheckinOpensText() { return checkinOpensText; }
        public String getBagDropCloseText() { return bagDropCloseText; }
        public int getBoardingCloseMins() { return boardingCloseMins; }
        public int getSalesCloseMins() { return salesCloseMins; }
        public int getCheckedBagKg() { return checkedBagKg; }
        public String getGateNote() { return gateNote; }
        public long getFreeCancelHours() { return freeCancelHours; }
        public String getCancellationSummary() { return cancellationSummary; }
    }

    public static final class FilterResult {
        private final List<FlightCardView> pageCards;
        private final int totalUnfiltered;
        private final int totalMatched;
        private final int page;
        private final int pageSize;
        private final int totalPages;
        private final int pageStart;
        private final int pageEnd;
        private final BigDecimal minPrice;
        private final BigDecimal maxPriceBound;
        private final BigDecimal activeMaxPrice;
        private final BigDecimal minEconomyPrice;
        private final BigDecimal minFirstPrice;

        public FilterResult(List<FlightCardView> pageCards, int totalUnfiltered, int totalMatched,
                            int page, int pageSize, int totalPages, int pageStart, int pageEnd,
                            BigDecimal minPrice, BigDecimal maxPriceBound, BigDecimal activeMaxPrice,
                            BigDecimal minEconomyPrice, BigDecimal minFirstPrice) {
            this.pageCards = pageCards;
            this.totalUnfiltered = totalUnfiltered;
            this.totalMatched = totalMatched;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages = totalPages;
            this.pageStart = pageStart;
            this.pageEnd = pageEnd;
            this.minPrice = minPrice;
            this.maxPriceBound = maxPriceBound;
            this.activeMaxPrice = activeMaxPrice;
            this.minEconomyPrice = minEconomyPrice;
            this.minFirstPrice = minFirstPrice;
        }

        public List<FlightCardView> getPageCards() { return pageCards; }
        public int getTotalUnfiltered() { return totalUnfiltered; }
        public int getTotalMatched() { return totalMatched; }
        public int getPage() { return page; }
        public int getPageSize() { return pageSize; }
        public int getTotalPages() { return totalPages; }
        public int getPageStart() { return pageStart; }
        public int getPageEnd() { return pageEnd; }
        public BigDecimal getMinPrice() { return minPrice; }
        public BigDecimal getMaxPriceBound() { return maxPriceBound; }
        public BigDecimal getActiveMaxPrice() { return activeMaxPrice; }
        public BigDecimal getMinEconomyPrice() { return minEconomyPrice; }
        public BigDecimal getMinFirstPrice() { return minFirstPrice; }
        public String getMinPriceFormatted() { return formatPriceCompact(minPrice); }
        public String getMaxPriceBoundFormatted() { return formatPriceCompact(maxPriceBound); }
        public String getActiveMaxPriceFormatted() { return formatPriceCompact(activeMaxPrice); }
        public String getMinEconomyPriceFormatted() { return formatPriceCompact(minEconomyPrice); }
        public String getMinFirstPriceFormatted() { return formatPriceCompact(minFirstPrice); }
        public boolean isPaginated() { return totalPages > 1; }
    }

    public static List<FlightCardView> buildCardViews(List<FlightSearchResult> flights,
                                                      Map<Long, PriceQuote> economyQuotes,
                                                      Map<Long, PriceQuote> firstQuotes,
                                                      String selectedCabin,
                                                      CancellationPolicy cancellationPolicy) {
        if (flights == null || flights.isEmpty()) {
            return List.of();
        }
        boolean preferFirst = SeatClass.FIRST.name().equalsIgnoreCase(selectedCabin);
        CancellationPolicy policy = cancellationPolicy != null ? cancellationPolicy : new CancellationPolicy();

        List<FlightCardView> cards = new ArrayList<>(flights.size());
        for (FlightSearchResult f : flights) {
            long fid = f.getFlightId() != null ? f.getFlightId() : -1L;
            PriceQuote econQ = economyQuotes != null ? economyQuotes.get(fid) : null;
            PriceQuote firstQ = firstQuotes != null ? firstQuotes.get(fid) : null;

            BigDecimal fallbackBase = f.getBasePrice() != null ? f.getBasePrice() : BigDecimal.ZERO;
            BigDecimal econPrice = econQ != null ? econQ.total() : fallbackBase;
            BigDecimal firstPrice = firstQ != null ? firstQ.total()
                    : fallbackBase.multiply(DEFAULT_FIRST_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);

            BigDecimal displayPrice = preferFirst ? firstPrice : econPrice;
            String cabinLabel = preferFirst ? "First from" : "Economy from";
            String priceFormatted = formatPriceCompact(displayPrice);

            int seatsLeft = Math.max(0, f.getAvailableSeats());
            boolean soldOut = seatsLeft <= 0;
            boolean lowSeats = !soldOut && seatsLeft <= FlightSalesPolicy.LOW_SEATS_THRESHOLD;
            String badgeText = soldOut ? "Sold out"
                    : (lowSeats ? ("Only " + seatsLeft + " left") : (seatsLeft + " seats left"));
            String badgeClass = soldOut ? "is-soldout" : (lowSeats ? "is-low" : "is-good");

            ZoneId origZone = f.getOriginZone() != null ? f.getOriginZone() : ZoneId.of("Africa/Johannesburg");
            ZoneId destZone = f.getDestZone() != null ? f.getDestZone() : origZone;
            String slot = resolveDepartureSlot(f.getDeparture(), origZone);

            String depDateStr = FlightTimes.formatLocalDate(f.getDeparture(), origZone);
            String depTimeStr = FlightTimes.formatLocalTime(f.getDeparture(), origZone);
            String depTz = FlightTimes.zoneAbbreviation(origZone, f.getDeparture());
            String depFull = depDateStr + " · " + depTimeStr + (depTz.isEmpty() ? "" : " " + depTz);

            String arrDateStr = FlightTimes.formatLocalDate(f.getArrival(), destZone);
            String arrTimeStr = FlightTimes.formatLocalTime(f.getArrival(), destZone);
            String arrTz = FlightTimes.zoneAbbreviation(destZone, f.getArrival());
            String arrFull = arrDateStr + " · " + arrTimeStr + (arrTz.isEmpty() ? "" : " " + arrTz);

            String checkinOpens = BoardingPolicy.formatCheckinOpens(depDateStr, depTimeStr);
            String bagDropClose = BoardingPolicy.formatBagDropTime(depTimeStr, f.getOriginCode());
            String cancelSummary = policy.evaluate(f.getDeparture(), "CONFIRMED", displayPrice).reason();

            cards.add(new FlightCardView(
                    f, econPrice, firstPrice, displayPrice, cabinLabel, priceFormatted,
                    seatsLeft, lowSeats, soldOut, badgeText, badgeClass,
                    slot, "3-3 layout", depFull, arrFull, checkinOpens, bagDropClose,
                    BoardingPolicy.BOARDING_MINUTES_BEFORE,
                    BoardingPolicy.SALES_CLOSE_MINUTES_BEFORE,
                    BaggagePolicy.CHECKED_BAGGAGE_KG,
                    BoardingPolicy.GATE_NOTE,
                    CancellationPolicy.MINIMUM_NOTICE.toHours(),
                    cancelSummary
            ));
        }
        return cards;
    }

    public static String resolveDepartureSlot(Instant departure, ZoneId zone) {
        if (departure == null || zone == null) {
            return SLOT_6_TO_12;
        }
        int hour = departure.atZone(zone).getHour();
        if (hour < 6) return SLOT_BEFORE_6;
        if (hour < 12) return SLOT_6_TO_12;
        if (hour < 18) return SLOT_12_TO_18;
        return SLOT_AFTER_18;
    }

    public static FilterResult filterAndPaginate(List<FlightCardView> allCards,
                                                 Set<String> depSlots,
                                                 BigDecimal requestedMaxPrice,
                                                 Set<String> cabins,
                                                 int requestedPage,
                                                 int pageSize) {
        List<FlightCardView> safeAll = allCards != null ? allCards : List.of();
        int effectiveSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;

        if (safeAll.isEmpty()) {
            return new FilterResult(List.of(), 0, 0, 1, effectiveSize, 1, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal minEcon = safeAll.stream().map(FlightCardView::getEconomyPrice)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal minFirst = safeAll.stream().map(FlightCardView::getFirstPrice)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal minDisplay = safeAll.stream().map(FlightCardView::getDisplayPrice)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal maxDisplay = safeAll.stream().map(FlightCardView::getDisplayPrice)
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal sliderMin = minDisplay.setScale(0, RoundingMode.FLOOR);
        BigDecimal sliderMax = maxDisplay.max(minFirst).setScale(0, RoundingMode.CEILING);

        BigDecimal activeMax = (requestedMaxPrice != null && requestedMaxPrice.compareTo(BigDecimal.ZERO) > 0)
                ? requestedMaxPrice.min(sliderMax).max(sliderMin)
                : sliderMax;

        boolean hasSlotFilter = depSlots != null && !depSlots.isEmpty();
        boolean hasCabinFilter = cabins != null && !cabins.isEmpty();

        List<FlightCardView> matched = new ArrayList<>();
        for (FlightCardView c : safeAll) {
            if (hasSlotFilter && !depSlots.contains(c.getDepSlot())) {
                continue;
            }
            if (c.getDisplayPrice().compareTo(activeMax) > 0) {
                continue;
            }
            if (hasCabinFilter) {
                boolean wantEcon = cabins.contains(SeatClass.ECONOMY.name());
                boolean wantFirst = cabins.contains(SeatClass.FIRST.name());
                boolean okEcon = wantEcon && c.getFlight().getAvailableEconomy() > 0;
                boolean okFirst = wantFirst && c.getFlight().getAvailableFirst() > 0;
                if (!okEcon && !okFirst) {
                    continue;
                }
            }
            matched.add(c);
        }

        int totalMatched = matched.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalMatched / (double) effectiveSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int fromIdx = (page - 1) * effectiveSize;
        int toIdx = Math.min(totalMatched, fromIdx + effectiveSize);
        List<FlightCardView> pageCards = fromIdx < toIdx ? matched.subList(fromIdx, toIdx) : List.of();
        int startDisp = totalMatched == 0 ? 0 : fromIdx + 1;
        int endDisp = totalMatched == 0 ? 0 : toIdx;

        return new FilterResult(
                pageCards, safeAll.size(), totalMatched,
                page, effectiveSize, totalPages, startDisp, endDisp,
                sliderMin, sliderMax, activeMax, minEcon, minFirst
        );
    }

    public static String formatPriceCompact(BigDecimal amount) {
        if (amount == null) {
            return "R 0";
        }
        DecimalFormatSymbols syms = DecimalFormatSymbols.getInstance(Locale.US);
        BigDecimal stripped = amount.stripTrailingZeros();
        String pattern = stripped.scale() <= 0 ? "#,##0" : "#,##0.00";
        DecimalFormat df = new DecimalFormat(pattern, syms);
        return "R " + df.format(amount);
    }
}
