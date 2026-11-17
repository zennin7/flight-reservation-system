// Original concept: Petja / Luyanda. Integrated by David.
package com.group2.flightres.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Paginated response for Admin Bookings including exact total count and sum of totals.
 */
public class AdminBookingsPage {

    private final List<AdminBookingRow> rows;
    private final long totalCount;
    private final BigDecimal totalAmountSum;
    private final int page;
    private final int pageSize;

    public AdminBookingsPage(List<AdminBookingRow> rows, long totalCount, BigDecimal totalAmountSum, int page, int pageSize) {
        this.rows = rows != null ? Collections.unmodifiableList(rows) : List.of();
        this.totalCount = totalCount;
        this.totalAmountSum = totalAmountSum != null ? totalAmountSum : BigDecimal.ZERO;
        this.page = Math.max(1, page);
        this.pageSize = Math.max(1, pageSize);
    }

    public List<AdminBookingRow> getRows() { return rows; }
    public long getTotalCount() { return totalCount; }
    public BigDecimal getTotalAmountSum() { return totalAmountSum; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }

    public int getTotalPages() {
        if (totalCount <= 0) return 1;
        return (int) Math.ceil((double) totalCount / pageSize);
    }
}
