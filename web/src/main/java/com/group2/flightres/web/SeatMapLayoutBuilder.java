package com.group2.flightres.web;

import com.group2.flightres.model.FlightSeat;
import com.group2.flightres.model.SeatClass;
import com.group2.flightres.model.SeatStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives interactive seat map sections, column alignment, states, and layout tags
 * directly from database seat records without hard-coding aircraft row ranges.
 */
public final class SeatMapLayoutBuilder {

    public enum VisualState {
        AVAILABLE,
        FIRST,
        TAKEN,
        HELD_OTHER,
        YOURS
    }

    public static final class CellView {
        private final String colLetter;
        private final FlightSeat seat;
        private final VisualState state;
        private final String positionTag;
        private final String cabinLabel;
        private final boolean selectable;
        private final String ariaLabel;
        private final String cssClass;

        public CellView(String colLetter, FlightSeat seat, VisualState state,
                        String positionTag, String cabinLabel, boolean selectable,
                        String ariaLabel, String cssClass) {
            this.colLetter = colLetter;
            this.seat = seat;
            this.state = state;
            this.positionTag = positionTag;
            this.cabinLabel = cabinLabel;
            this.selectable = selectable;
            this.ariaLabel = ariaLabel;
            this.cssClass = cssClass;
        }

        public String getColLetter() { return colLetter; }
        public FlightSeat getSeat() { return seat; }
        public boolean isEmpty() { return seat == null; }
        public VisualState getState() { return state; }
        public String getPositionTag() { return positionTag; }
        public String getCabinLabel() { return cabinLabel; }
        public boolean isSelectable() { return selectable; }
        public String getAriaLabel() { return ariaLabel; }
        public String getCssClass() { return cssClass; }
    }

    public static final class LetterRowBand {
        private final String letter;
        private final int letterIndex;
        private final List<CellView> cells;

        public LetterRowBand(String letter, int letterIndex, List<CellView> cells) {
            this.letter = letter;
            this.letterIndex = letterIndex;
            this.cells = cells;
        }

        public String getLetter() { return letter; }
        public int getLetterIndex() { return letterIndex; }
        public List<CellView> getCells() { return cells; }
    }

    public static final class SectionView {
        private final int index;
        private final String tabLabel;
        private final String title;
        private final String cabinSummary;
        private final SeatClass primaryCabin;
        private final String primaryCabinPhoto;
        private final int startRow;
        private final int endRow;
        private final List<Integer> rowNumbers;
        private final List<LetterRowBand> topBands;
        private final List<LetterRowBand> bottomBands;
        private final int svgX;
        private final int svgWidth;

        public SectionView(int index, String tabLabel, String title, String cabinSummary,
                           SeatClass primaryCabin, String primaryCabinPhoto,
                           int startRow, int endRow, List<Integer> rowNumbers,
                           List<LetterRowBand> topBands, List<LetterRowBand> bottomBands,
                           int svgX, int svgWidth) {
            this.index = index;
            this.tabLabel = tabLabel;
            this.title = title;
            this.cabinSummary = cabinSummary;
            this.primaryCabin = primaryCabin;
            this.primaryCabinPhoto = primaryCabinPhoto;
            this.startRow = startRow;
            this.endRow = endRow;
            this.rowNumbers = rowNumbers;
            this.topBands = topBands;
            this.bottomBands = bottomBands;
            this.svgX = svgX;
            this.svgWidth = svgWidth;
        }

        public int getIndex() { return index; }
        public String getTabLabel() { return tabLabel; }
        public String getTitle() { return title; }
        public String getCabinSummary() { return cabinSummary; }
        public SeatClass getPrimaryCabin() { return primaryCabin; }
        public String getPrimaryCabinPhoto() { return primaryCabinPhoto; }
        public int getStartRow() { return startRow; }
        public int getEndRow() { return endRow; }
        public List<Integer> getRowNumbers() { return rowNumbers; }
        public List<LetterRowBand> getTopBands() { return topBands; }
        public List<LetterRowBand> getBottomBands() { return bottomBands; }
        public int getSvgX() { return svgX; }
        public int getSvgWidth() { return svgWidth; }
        public String getGridColsTemplate() {
            return "22px repeat(" + Math.max(1, rowNumbers.size()) + ", minmax(38px, 44px))";
        }
    }

    public static final class SeatMapView {
        private final List<SectionView> sections;
        private final int activeSectionIndex;
        private final int freeSeats;
        private final int totalSeats;
        private final FlightSeat heldSeat;
        private final String heldSeatPositionTag;
        private final int heldSeatSectionIndex;

        public SeatMapView(List<SectionView> sections, int activeSectionIndex,
                           int freeSeats, int totalSeats, FlightSeat heldSeat,
                           String heldSeatPositionTag, int heldSeatSectionIndex) {
            this.sections = sections;
            this.activeSectionIndex = activeSectionIndex;
            this.freeSeats = freeSeats;
            this.totalSeats = totalSeats;
            this.heldSeat = heldSeat;
            this.heldSeatPositionTag = heldSeatPositionTag;
            this.heldSeatSectionIndex = heldSeatSectionIndex;
        }

        public List<SectionView> getSections() { return sections; }
        public int getActiveSectionIndex() { return activeSectionIndex; }
        public int getFreeSeats() { return freeSeats; }
        public int getTotalSeats() { return totalSeats; }
        public FlightSeat getHeldSeat() { return heldSeat; }
        public String getHeldSeatPositionTag() { return heldSeatPositionTag; }
        public int getHeldSeatSectionIndex() { return heldSeatSectionIndex; }
        public SectionView getActiveSection() {
            if (sections.isEmpty()) return null;
            int idx = Math.max(0, Math.min(activeSectionIndex, sections.size() - 1));
            return sections.get(idx);
        }
    }

    private SeatMapLayoutBuilder() {
    }

    public static SeatMapView build(List<FlightSeat> seats, String currentHoldOwner, Instant now) {
        Instant clockNow = now != null ? now : Instant.now();
        if (seats == null || seats.isEmpty()) {
            return new SeatMapView(List.of(), 0, 0, 0, null, "", 0);
        }

        TreeMap<Integer, List<FlightSeat>> rowsMap = new TreeMap<>();
        TreeSet<Character> allColsSet = new TreeSet<>();
        int freeCount = 0;
        FlightSeat heldSeat = null;

        for (FlightSeat s : seats) {
            if (s == null) continue;
            rowsMap.computeIfAbsent(s.getSeatRow(), k -> new ArrayList<>()).add(s);
            allColsSet.add(Character.toUpperCase(s.getSeatCol()));
            VisualState st = classify(s, currentHoldOwner, clockNow);
            if (st == VisualState.AVAILABLE || st == VisualState.FIRST) {
                freeCount++;
            } else if (st == VisualState.YOURS && heldSeat == null) {
                heldSeat = s;
            }
        }

        for (List<FlightSeat> list : rowsMap.values()) {
            list.sort(Comparator.comparingInt(FlightSeat::getDisplayCol)
                    .thenComparingInt(fs -> Character.toUpperCase(fs.getSeatCol())));
        }

        List<Character> allCols = new ArrayList<>(allColsSet);
        int aisleSplitIdx = resolveAisleSplit(seats, allCols);
        List<Character> topCols = allCols.subList(0, aisleSplitIdx);
        List<Character> bottomCols = allCols.subList(aisleSplitIdx, allCols.size());

        List<Integer> distinctRows = new ArrayList<>(rowsMap.keySet());
        int totalRows = distinctRows.size();
        int sectionCount = totalRows <= 10 ? 1 : Math.min(4, (totalRows + 9) / 10);

        List<SectionView> sections = new ArrayList<>(sectionCount);
        int activeSectionIndex = 0;
        int heldSeatSectionIndex = 0;
        String heldSeatPosTag = "";

        int baseChunk = totalRows / sectionCount;
        int rem = totalRows % sectionCount;
        int offset = 0;

        // Cabin SVG span inside viewBox 0 0 800 130 (nose at ~70, tail cabin end at ~565)
        final double cabinSvgStart = 72.0;
        final double cabinSvgSpan = 490.0;

        for (int secIdx = 0; secIdx < sectionCount; secIdx++) {
            int size = baseChunk + (secIdx < rem ? 1 : 0);
            List<Integer> secRows = distinctRows.subList(offset, offset + size);
            double startFrac = (double) offset / totalRows;
            double widthFrac = (double) size / totalRows;
            offset += size;

            Set<SeatClass> classesInSec = new LinkedHashSet<>();
            for (int r : secRows) {
                for (FlightSeat s : rowsMap.get(r)) {
                    if (s.getSeatClass() != null) classesInSec.add(s.getSeatClass());
                }
            }

            boolean hasFirst = classesInSec.contains(SeatClass.FIRST);
            boolean hasEcon = classesInSec.contains(SeatClass.ECONOMY);
            String cabinSummary;
            if (hasFirst && hasEcon) {
                cabinSummary = "First Class & Economy";
            } else if (hasFirst) {
                cabinSummary = "First Class";
            } else {
                cabinSummary = "Economy";
            }
            SeatClass primaryCabin = hasFirst && !hasEcon ? SeatClass.FIRST : SeatClass.ECONOMY;
            if (heldSeat != null && secRows.contains(heldSeat.getSeatRow())) {
                heldSeatSectionIndex = secIdx;
                activeSectionIndex = secIdx;
                primaryCabin = heldSeat.getSeatClass() != null ? heldSeat.getSeatClass() : primaryCabin;
            }

            int startRow = secRows.get(0);
            int endRow = secRows.get(secRows.size() - 1);
            String tabLabel = "Section " + (secIdx + 1);
            String rowRangeLabel = startRow == endRow ? ("row " + startRow) : ("rows " + startRow + " to " + endRow);
            String secTitle = tabLabel + " · " + cabinSummary + " · " + rowRangeLabel;

            List<LetterRowBand> topBands = buildLetterBands(topCols, 0, secRows, rowsMap, currentHoldOwner, clockNow, topCols, bottomCols);
            List<LetterRowBand> bottomBands = buildLetterBands(bottomCols, topCols.size(), secRows, rowsMap, currentHoldOwner, clockNow, topCols, bottomCols);

            if (heldSeat != null && secRows.contains(heldSeat.getSeatRow())) {
                heldSeatPosTag = derivePositionTag(Character.toUpperCase(heldSeat.getSeatCol()), topCols, bottomCols);
            }

            int svgX = (int) Math.round(cabinSvgStart + startFrac * cabinSvgSpan);
            int svgW = Math.max(42, (int) Math.round(widthFrac * cabinSvgSpan));

            sections.add(new SectionView(
                    secIdx,
                    tabLabel,
                    secTitle,
                    cabinSummary,
                    primaryCabin,
                    WebBackdropResolver.sectionCabinPhoto(secIdx + 1),
                    startRow,
                    endRow,
                    List.copyOf(secRows),
                    topBands,
                    bottomBands,
                    svgX,
                    svgW
            ));
        }

        return new SeatMapView(sections, activeSectionIndex, freeCount, seats.size(), heldSeat, heldSeatPosTag, heldSeatSectionIndex);
    }

    private static List<LetterRowBand> buildLetterBands(
            List<Character> bandCols,
            int letterOffset,
            List<Integer> secRows,
            Map<Integer, List<FlightSeat>> rowsMap,
            String currentHoldOwner,
            Instant clockNow,
            List<Character> topCols,
            List<Character> bottomCols
    ) {
        List<LetterRowBand> bands = new ArrayList<>(bandCols.size());
        for (int idx = 0; idx < bandCols.size(); idx++) {
            char col = bandCols.get(idx);
            List<CellView> cells = new ArrayList<>(secRows.size());
            for (int r : secRows) {
                FlightSeat match = null;
                for (FlightSeat candidate : rowsMap.get(r)) {
                    if (Character.toUpperCase(candidate.getSeatCol()) == col) {
                        match = candidate;
                        break;
                    }
                }
                if (match == null) {
                    cells.add(new CellView(String.valueOf(col), null, null, "", "", false, "", "seat-slot-empty"));
                    continue;
                }
                VisualState st = classify(match, currentHoldOwner, clockNow);
                String posTag = derivePositionTag(col, topCols, bottomCols);
                String cabinLabel = match.getSeatClass() == SeatClass.FIRST ? "First Class" : "Economy";
                boolean selectable = st == VisualState.AVAILABLE || st == VisualState.FIRST || st == VisualState.YOURS;
                String statusWord = switch (st) {
                    case AVAILABLE -> "Available";
                    case FIRST -> "First Class available";
                    case TAKEN -> "Taken";
                    case HELD_OTHER -> "Held by another traveller";
                    case YOURS -> "Held by you";
                };
                String css = switch (st) {
                    case AVAILABLE -> "seat-cell is-available";
                    case FIRST -> "seat-cell is-available is-first";
                    case TAKEN -> "seat-cell is-taken";
                    case HELD_OTHER -> "seat-cell is-held-other";
                    case YOURS -> "seat-cell is-yours";
                };
                String aria = "Seat " + match.getSeatLabel() + ", " + cabinLabel + ", " + posTag + ", " + statusWord;
                cells.add(new CellView(String.valueOf(col), match, st, posTag, cabinLabel, selectable, aria, css));
            }
            bands.add(new LetterRowBand(String.valueOf(col), letterOffset + idx, cells));
        }
        return bands;
    }

    public static VisualState classify(FlightSeat seat, String currentHoldOwner, Instant now) {
        if (seat == null || seat.getStatus() == null) {
            return VisualState.TAKEN;
        }
        SeatStatus status = seat.getStatus();
        if (status == SeatStatus.BOOKED) {
            return VisualState.TAKEN;
        }
        if (status == SeatStatus.HELD) {
            Instant heldUntil = seat.getHeldUntil();
            boolean notExpired = heldUntil == null || heldUntil.isAfter(now);
            if (notExpired) {
                if (currentHoldOwner != null && Objects.equals(seat.getHeldBy(), currentHoldOwner)) {
                    return VisualState.YOURS;
                }
                return VisualState.HELD_OTHER;
            }
        }
        return seat.getSeatClass() == SeatClass.FIRST ? VisualState.FIRST : VisualState.AVAILABLE;
    }

    public static String derivePositionTag(FlightSeat seat, List<FlightSeat> allSeats) {
        if (seat == null || allSeats == null || allSeats.isEmpty()) {
            return "";
        }
        TreeSet<Character> allColsSet = new TreeSet<>();
        for (FlightSeat s : allSeats) {
            if (s != null) {
                allColsSet.add(Character.toUpperCase(s.getSeatCol()));
            }
        }
        List<Character> allCols = new ArrayList<>(allColsSet);
        int split = resolveAisleSplit(allSeats, allCols);
        return derivePositionTag(Character.toUpperCase(seat.getSeatCol()),
                allCols.subList(0, split),
                allCols.subList(split, allCols.size()));
    }

    public static String derivePositionTag(char colUpper, List<Character> topCols, List<Character> bottomCols) {
        if (topCols.isEmpty() && bottomCols.isEmpty()) {
            return "";
        }
        if ((!topCols.isEmpty() && colUpper == topCols.get(0))
                || (!bottomCols.isEmpty() && colUpper == bottomCols.get(bottomCols.size() - 1))) {
            return "Window";
        }
        if ((!topCols.isEmpty() && colUpper == topCols.get(topCols.size() - 1))
                || (!bottomCols.isEmpty() && colUpper == bottomCols.get(0))) {
            return "Aisle";
        }
        return "Middle";
    }

    private static int resolveAisleSplit(List<FlightSeat> seats, List<Character> allCols) {
        if (allCols.size() <= 1) {
            return allCols.size();
        }
        // Detect explicit display_col gap (e.g. A=0, B=1, C=2, D=4 -> gap between C and D)
        Map<Character, Integer> displayCols = new LinkedHashMap<>();
        for (FlightSeat s : seats) {
            if (s != null && s.getDisplayCol() >= 0) {
                displayCols.putIfAbsent(Character.toUpperCase(s.getSeatCol()), s.getDisplayCol());
            }
        }
        if (displayCols.size() == allCols.size()) {
            int maxGap = 1;
            int gapIdx = -1;
            for (int i = 1; i < allCols.size(); i++) {
                int diff = displayCols.get(allCols.get(i)) - displayCols.get(allCols.get(i - 1));
                if (diff > maxGap) {
                    maxGap = diff;
                    gapIdx = i;
                }
            }
            if (gapIdx > 0) {
                return gapIdx;
            }
        }
        return (allCols.size() + 1) / 2;
    }
}
