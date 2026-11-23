(function () {
    'use strict';

    var rows = Array.prototype.slice.call(document.querySelectorAll('.myb-row-btn'));
    var chips = Array.prototype.slice.call(document.querySelectorAll('.myb-filter-chip'));
    var groups = Array.prototype.slice.call(document.querySelectorAll('.myb-group'));
    var actionsBox = document.getElementById('mybPreviewActions');

    var pRef = document.getElementById('mybPreviewRef');
    var pFlight = document.getElementById('mybPreviewFlight');
    var pStatus = document.getElementById('mybPreviewStatus');
    var pOrig = document.getElementById('mybPreviewOrigin');
    var pDest = document.getElementById('mybPreviewDest');
    var pCities = document.getElementById('mybPreviewCities');
    var pTiming = document.getElementById('mybPreviewTiming');
    var pPax = document.getElementById('mybPreviewPax');
    var pSeat = document.getElementById('mybPreviewSeat');
    var pAircraft = document.getElementById('mybPreviewAircraft');
    var pTotal = document.getElementById('mybPreviewTotal');

    var pQrWrap = document.getElementById('mybPreviewQrWrap');
    var pQrImg = document.getElementById('mybPreviewQrImg');
    var pCancelledNote = document.getElementById('mybPreviewCancelledNote');
    var pPdf = document.getElementById('mybPreviewPdf');
    var pIcs = document.getElementById('mybPreviewIcs');
    var pVoucher = document.getElementById('mybPreviewVoucher');

    function updatePreviewFromRow(btn) {
        if (!btn) return;
        rows.forEach(function (r) {
            var active = r === btn;
            r.classList.toggle('is-selected', active);
            r.setAttribute('aria-pressed', active ? 'true' : 'false');
        });

        var ref = btn.getAttribute('data-ref') || '';
        var status = btn.getAttribute('data-status') || '';
        var isActiveTicket = btn.getAttribute('data-active') === 'true';
        var canCancel = btn.getAttribute('data-cancellable') === 'true';
        var cancelUrl = btn.getAttribute('data-cancel-url') || '';

        if (pRef) pRef.textContent = 'PNR ' + ref;
        if (pFlight) pFlight.textContent = btn.getAttribute('data-flight') || '';
        if (pStatus) {
            pStatus.textContent = status;
            pStatus.className = 'myb-pass-status' + (status === 'CANCELLED' ? ' is-cancelled' : (status === 'UPCOMING' ? ' is-upcoming' : ''));
        }
        if (pOrig) pOrig.textContent = btn.getAttribute('data-origin-code') || '';
        if (pDest) pDest.textContent = btn.getAttribute('data-dest-code') || '';
        if (pCities) {
            pCities.textContent = (btn.getAttribute('data-origin-city') || '') + ' to ' + (btn.getAttribute('data-dest-city') || '');
        }
        if (pTiming) {
            var dateTxt = btn.getAttribute('data-date') || '';
            var depTxt = btn.getAttribute('data-dep-time') || '';
            var arrTxt = btn.getAttribute('data-arr-time') || '';
            var durTxt = btn.getAttribute('data-duration') || '';
            pTiming.textContent = dateTxt + ' · ' + depTxt + (arrTxt ? ' – ' + arrTxt : '') + (durTxt ? ' · ' + durTxt : '');
        }
        if (pPax) pPax.textContent = btn.getAttribute('data-passenger') || '—';
        if (pSeat) pSeat.textContent = btn.getAttribute('data-seat') || '—';
        if (pAircraft) pAircraft.textContent = btn.getAttribute('data-aircraft') || 'Airbus A320';
        if (pTotal) pTotal.textContent = btn.getAttribute('data-total') || '';

        if (pQrWrap) pQrWrap.hidden = !isActiveTicket;
        if (pQrImg && isActiveTicket) {
            pQrImg.src = btn.getAttribute('data-qr-url') || '';
            pQrImg.alt = 'Verification QR code for booking ' + ref;
        }
        if (pCancelledNote) pCancelledNote.hidden = isActiveTicket;

        if (pPdf) {
            pPdf.hidden = !isActiveTicket;
            if (isActiveTicket) pPdf.href = btn.getAttribute('data-pdf-url') || '#';
        }
        if (pIcs) {
            pIcs.hidden = !isActiveTicket;
            if (isActiveTicket) pIcs.href = btn.getAttribute('data-ics-url') || '#';
        }
        if (pVoucher) {
            pVoucher.href = btn.getAttribute('data-confirm-url') || '#';
        }

        var existingCancel = document.getElementById('mybPreviewCancel');
        if (canCancel && cancelUrl) {
            if (!existingCancel && actionsBox) {
                existingCancel = document.createElement('a');
                existingCancel.id = 'mybPreviewCancel';
                existingCancel.className = 'myb-pass-cancel-link';
                existingCancel.textContent = 'Cancel booking';
                actionsBox.appendChild(existingCancel);
            }
            if (existingCancel) {
                existingCancel.href = cancelUrl;
                existingCancel.hidden = false;
            }
        } else if (existingCancel) {
            existingCancel.hidden = true;
            existingCancel.removeAttribute('href');
        }
    }

    rows.forEach(function (btn) {
        btn.addEventListener('click', function () {
            updatePreviewFromRow(btn);
        });
    });

    chips.forEach(function (chip) {
        chip.addEventListener('click', function () {
            var filter = chip.getAttribute('data-filter') || 'all';
            chips.forEach(function (c) {
                var active = c === chip;
                c.classList.toggle('is-active', active);
                c.setAttribute('aria-pressed', active ? 'true' : 'false');
            });

            var firstVisibleRow = null;
            groups.forEach(function (sec) {
                var groupName = sec.getAttribute('data-group');
                var show = filter === 'all' || filter === groupName;
                sec.hidden = !show;
                if (show && !firstVisibleRow) {
                    firstVisibleRow = sec.querySelector('.myb-row-btn');
                }
            });

            var currentlySelected = document.querySelector('.myb-row-btn.is-selected');
            var parentGroup = currentlySelected ? currentlySelected.closest('.myb-group') : null;
            if ((!currentlySelected || (parentGroup && parentGroup.hidden)) && firstVisibleRow) {
                updatePreviewFromRow(firstVisibleRow);
            }
        });
    });
})();
