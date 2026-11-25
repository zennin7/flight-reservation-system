(function () {
  var root = document.getElementById('seatMapRoot');
  if (!root) return;

  var flightId = root.getAttribute('data-flight-id');
  var csrfToken = root.getAttribute('data-csrf') || '';
  var totalHoldSecs = Math.max(1, parseInt(root.getAttribute('data-hold-secs') || '300', 10));
  var econPhoto = root.getAttribute('data-econ-photo') || '/photos/cabin-economy-1.jpg';
  var firstPhoto = root.getAttribute('data-first-photo') || '/photos/karsten-winegeart-TkKTqPPkWP4-unsplash.jpg';

  var sectionTabs = Array.prototype.slice.call(document.querySelectorAll('.sm-sec-tab'));
  var sectionPanels = Array.prototype.slice.call(document.querySelectorAll('.seat-section-panel'));
  var svgBand = document.getElementById('svgSectionBand');
  var secHeading = document.getElementById('activeSectionHeading');

  var zoomBtns = Array.prototype.slice.call(document.querySelectorAll('.sm-zoom-btn'));
  var gridScroll = document.getElementById('seatGridScroll');

  var cabinThumb = document.getElementById('cabinViewThumb');
  var cabinBadge = document.getElementById('cabinViewBadge');
  var openLightboxBtn = document.getElementById('openCabinLightboxBtn');
  var lightbox = document.getElementById('cabinLightbox');
  var lightboxImg = document.getElementById('cabinLightboxImg');
  var lightboxTitle = document.getElementById('cabinLightboxTitle');
  var closeLightboxBtn = document.getElementById('closeCabinLightboxBtn');

  var errorBanner = document.getElementById('seatMapErrorBanner');
  var errorText = document.getElementById('seatMapErrorText');

  var selSeatLabel = document.getElementById('selSeatLabel');
  var selRowSub = document.getElementById('selRowSub');
  var selCabinBadge = document.getElementById('selCabinBadge');
  var selTotalPrice = document.getElementById('selTotalPrice');
  var selTagCabin = document.getElementById('selTagCabin');
  var selTagPos = document.getElementById('selTagPos');
  var selBaseFare = document.getElementById('selBaseFare');
  var selUpgradeRow = document.getElementById('selUpgradeRow');
  var selUpgradeFare = document.getElementById('selUpgradeFare');
  var selAdjustmentsSummary = document.getElementById('selAdjustmentsSummary');
  var selVatFare = document.getElementById('selVatFare');

  var holdTimerBox = document.getElementById('holdTimerBox');
  var holdRingCircle = document.getElementById('holdRingCircle');
  var holdCountdown = document.getElementById('holdCountdown');
  var holdTimerCopy = document.getElementById('holdTimerCopy');
  var holdExpiredAlert = document.getElementById('holdExpiredState');

  var seatIdInput = document.getElementById('selectedSeatIdInput');
  var continueBtn = document.getElementById('continueCheckoutBtn');
  var releaseBtn = document.getElementById('releaseSeatBtn');
  var freeSeatsEl = document.getElementById('flightFreeSeatsCount');

  var RING_CIRCUMFERENCE = 113.1;
  var holdExpiresMs = parseInt(root.getAttribute('data-held-expires-ms') || '0', 10) || 0;
  var holdTimerInterval = null;
  var prevFocusBeforeLightbox = null;

  function showError(msg) {
    if (!errorBanner || !errorText) return;
    if (!msg) {
      errorBanner.hidden = true;
      errorText.textContent = '';
      return;
    }
    errorText.textContent = msg;
    errorBanner.hidden = false;
  }

  function crossfadeImg(imgEl, url) {
    if (!imgEl || !url) return;
    if (imgEl.getAttribute('data-current-src') === url) return;
    var pre = new Image();
    pre.onload = function () {
      imgEl.classList.add('is-fading');
      window.setTimeout(function () {
        imgEl.src = url;
        imgEl.setAttribute('data-current-src', url);
        imgEl.classList.remove('is-fading');
      }, 90);
    };
    pre.src = url;
  }

  function updateCabinVisuals(cabinName, explicitPhotoUrl, sectionLabel) {
    var isFirst = cabinName === 'FIRST' || cabinName === 'First Class';
    var url = explicitPhotoUrl || (isFirst ? firstPhoto : econPhoto);
    var label = sectionLabel || (isFirst ? 'First Class Cabin' : 'Economy Cabin');
    crossfadeImg(cabinThumb, url);
    if (cabinBadge) cabinBadge.textContent = label;
    crossfadeImg(lightboxImg, url);
    if (lightboxTitle) lightboxTitle.textContent = label;
    if (window.SkyRoyalBackdrop && typeof window.SkyRoyalBackdrop.setCabin === 'function') {
      window.SkyRoyalBackdrop.setCabin(url);
    }
  }

  function activateSection(idx) {
    sectionTabs.forEach(function (tab) {
      var match = parseInt(tab.getAttribute('data-sec-idx'), 10) === idx;
      tab.classList.toggle('is-active', match);
      tab.setAttribute('aria-selected', match ? 'true' : 'false');
      if (match) {
        if (svgBand) {
          var sx = tab.getAttribute('data-svg-x');
          var sw = tab.getAttribute('data-svg-w');
          if (sx) svgBand.setAttribute('x', sx);
          if (sw) svgBand.setAttribute('width', sw);
        }
        if (secHeading) {
          secHeading.textContent = tab.getAttribute('data-sec-title') || '';
        }
        var secPhoto = tab.getAttribute('data-cabin-photo') || '';
        var secLabel = tab.textContent ? (tab.textContent.trim() + ' Cabin') : '';
        updateCabinVisuals(tab.getAttribute('data-cabin-name') || 'ECONOMY', secPhoto, secLabel);
      }
    });
    sectionPanels.forEach(function (panel) {
      var match = parseInt(panel.getAttribute('data-sec-idx'), 10) === idx;
      panel.hidden = !match;
    });
  }

  sectionTabs.forEach(function (tab, i) {
    tab.addEventListener('click', function () {
      activateSection(parseInt(tab.getAttribute('data-sec-idx'), 10));
    });
    tab.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowRight') {
        e.preventDefault();
        var next = sectionTabs[(i + 1) % sectionTabs.length];
        next.focus();
        activateSection(parseInt(next.getAttribute('data-sec-idx'), 10));
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        var prev = sectionTabs[(i - 1 + sectionTabs.length) % sectionTabs.length];
        prev.focus();
        activateSection(parseInt(prev.getAttribute('data-sec-idx'), 10));
      }
    });
  });

  // Real zoom (100% / 125% / 150%)
  zoomBtns.forEach(function (btn) {
    btn.addEventListener('click', function () {
      var z = btn.getAttribute('data-zoom') || '100';
      if (gridScroll) {
        gridScroll.setAttribute('data-zoom', z);
      }
      zoomBtns.forEach(function (b) {
        var active = b === btn;
        b.classList.toggle('is-active', active);
        b.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
    });
  });

  // Hold ring countdown tied to server heldUntil epoch
  function formatClock(secs) {
    var m = Math.floor(secs / 60);
    var s = secs % 60;
    return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
  }

  function renderHoldTimer() {
    if (!holdExpiresMs || !seatIdInput || !seatIdInput.value) {
      if (holdTimerBox) holdTimerBox.classList.add('is-idle');
      if (holdTimerBox) holdTimerBox.classList.remove('is-expired');
      if (holdRingCircle) holdRingCircle.style.strokeDashoffset = '0';
      if (holdCountdown) holdCountdown.textContent = formatClock(totalHoldSecs);
      if (holdTimerCopy) holdTimerCopy.textContent = 'Select a seat to start your 5-minute hold.';
      if (holdExpiredAlert) holdExpiredAlert.hidden = true;
      return;
    }

    var remSecs = Math.max(0, Math.floor((holdExpiresMs - Date.now()) / 1000));
    if (holdCountdown) holdCountdown.textContent = formatClock(remSecs);
    var fraction = Math.max(0, Math.min(1, remSecs / totalHoldSecs));
    var dashOffset = (RING_CIRCUMFERENCE * (1 - fraction)).toFixed(2);
    if (holdRingCircle) holdRingCircle.style.strokeDashoffset = dashOffset;

    if (remSecs <= 0) {
      if (holdTimerBox) {
        holdTimerBox.classList.remove('is-idle');
        holdTimerBox.classList.add('is-expired');
      }
      if (holdTimerCopy) holdTimerCopy.textContent = 'Hold expired';
      if (holdExpiredAlert) holdExpiredAlert.hidden = false;
      if (continueBtn) continueBtn.disabled = true;
      if (releaseBtn) releaseBtn.disabled = true;
      clearYoursHighlight();
      if (holdTimerInterval) {
        window.clearInterval(holdTimerInterval);
        holdTimerInterval = null;
      }
    } else {
      if (holdTimerBox) {
        holdTimerBox.classList.remove('is-idle', 'is-expired');
      }
      if (holdTimerCopy) holdTimerCopy.textContent = 'Held for you. Nobody else can take it.';
      if (holdExpiredAlert) holdExpiredAlert.hidden = true;
    }
  }

  function startHoldCountdown() {
    if (holdTimerInterval) {
      window.clearInterval(holdTimerInterval);
    }
    renderHoldTimer();
    if (holdExpiresMs > Date.now()) {
      holdTimerInterval = window.setInterval(renderHoldTimer, 1000);
    }
  }

  function clearYoursHighlight() {
    var existingYours = document.querySelectorAll('.seat-cell.is-yours');
    Array.prototype.forEach.call(existingYours, function (btn) {
      var isFirst = btn.getAttribute('data-seat-class') === 'FIRST';
      btn.classList.remove('is-yours');
      btn.classList.add('is-available');
      if (isFirst) btn.classList.add('is-first');
      btn.setAttribute('data-seat-state', isFirst ? 'FIRST' : 'AVAILABLE');
      btn.setAttribute('aria-pressed', 'false');
    });
  }

  function applyHoldResponse(data, clickedBtn) {
    showError(null);
    clearYoursHighlight();
    if (clickedBtn) {
      clickedBtn.classList.remove('is-available', 'is-first');
      clickedBtn.classList.add('is-yours');
      clickedBtn.setAttribute('data-seat-state', 'YOURS');
      clickedBtn.setAttribute('aria-pressed', 'true');
    }

    if (seatIdInput) seatIdInput.value = data.seatId;
    if (selSeatLabel) selSeatLabel.textContent = data.seatLabel || '--';
    if (selRowSub) selRowSub.textContent = data.seatRow ? ('Row ' + data.seatRow) : '';
    if (selCabinBadge) selCabinBadge.textContent = data.cabinLabel || 'Economy';
    if (selTotalPrice) selTotalPrice.textContent = data.totalFormatted || '';
    if (selTagCabin) selTagCabin.textContent = data.cabinLabel || 'Economy';
    if (selTagPos) selTagPos.textContent = data.positionTag || '';
    if (selBaseFare) selBaseFare.textContent = data.baseFareFormatted || '';
    if (selUpgradeRow) selUpgradeRow.hidden = !data.hasClassPremium;
    if (selUpgradeFare) selUpgradeFare.textContent = data.classUpgradeFormatted || '';
    if (selAdjustmentsSummary) {
      selAdjustmentsSummary.textContent = (data.demandAdjFormatted || '+R 0.00') + ' / ' + (data.timingAdjFormatted || '+R 0.00');
    }
    if (selVatFare) selVatFare.textContent = data.vatFormatted || '';
    if (freeSeatsEl && typeof data.freeSeats === 'number' && typeof data.totalSeats === 'number') {
      freeSeatsEl.textContent = data.freeSeats + ' of ' + data.totalSeats;
    }

    if (continueBtn) continueBtn.disabled = false;
    if (releaseBtn) releaseBtn.disabled = false;
    if (holdExpiredAlert) holdExpiredAlert.hidden = true;

    updateCabinVisuals(data.seatClass || 'ECONOMY');
    holdExpiresMs = parseInt(data.holdExpiresEpochMs || '0', 10) || (Date.now() + totalHoldSecs * 1000);
    startHoldCountdown();
  }

  function clearSelectionUi(data) {
    clearYoursHighlight();
    holdExpiresMs = 0;
    if (seatIdInput) seatIdInput.value = '';
    if (selSeatLabel) selSeatLabel.textContent = '--';
    if (selRowSub) selRowSub.textContent = 'Pick an available seat';
    if (selCabinBadge) selCabinBadge.textContent = 'No seat held';
    if (selTagPos) selTagPos.textContent = 'Window / Aisle';
    if (continueBtn) continueBtn.disabled = true;
    if (releaseBtn) releaseBtn.disabled = true;
    if (data && freeSeatsEl && typeof data.freeSeats === 'number') {
      freeSeatsEl.textContent = data.freeSeats + ' of ' + data.totalSeats;
    }
    startHoldCountdown();
  }

  function holdSeat(btn) {
    if (!btn || btn.getAttribute('data-selectable') !== 'true') {
      return;
    }
    var seatId = btn.getAttribute('data-seat-id');
    if (!seatId || !flightId) return;

    var params = new URLSearchParams();
    params.set('csrf_token', csrfToken);
    params.set('flightSeatId', seatId);

    fetch('/flights/' + flightId + '/seats/hold', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Accept': 'application/json'
      },
      body: params.toString()
    }).then(function (resp) {
      return resp.json().then(function (data) {
        return { ok: resp.ok, status: resp.status, data: data };
      });
    }).then(function (res) {
      if (!res.ok) {
        showError((res.data && res.data.error) || 'Seat could not be held.');
        return;
      }
      applyHoldResponse(res.data, btn);
    }).catch(function () {
      showError('Could not hold this seat right now. Please try again.');
    });
  }

  if (releaseBtn) {
    releaseBtn.addEventListener('click', function () {
      var seatId = seatIdInput ? seatIdInput.value : '';
      if (!seatId || !flightId) return;
      var params = new URLSearchParams();
      params.set('csrf_token', csrfToken);
      params.set('flightSeatId', seatId);

      fetch('/flights/' + flightId + '/seats/release', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Accept': 'application/json'
        },
        body: params.toString()
      }).then(function (resp) {
        return resp.json().then(function (data) {
          return { ok: resp.ok, data: data };
        });
      }).then(function (res) {
        if (!res.ok) {
          showError((res.data && res.data.error) || 'Could not release your seat hold.');
          return;
        }
        showError(null);
        clearSelectionUi(res.data);
      }).catch(function () {
        showError('Could not release the seat right now.');
      });
    });
  }

  // Interactive seat buttons + roving tabindex arrow-key navigation
  var seatButtons = Array.prototype.slice.call(document.querySelectorAll('.seat-cell[data-seat-id]'));
  seatButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (btn.getAttribute('data-selectable') === 'true') {
        holdSeat(btn);
      } else {
        showError(btn.getAttribute('aria-label') || 'This seat is unavailable.');
      }
    });

    btn.addEventListener('keydown', function (e) {
      var panel = btn.closest('.seat-section-panel');
      if (!panel) return;
      var rowIdx = parseInt(btn.getAttribute('data-row-idx') || '0', 10);
      var colIdx = parseInt(btn.getAttribute('data-col-idx') || '0', 10);

      var targetRow = rowIdx;
      var targetCol = colIdx;
      var handled = false;
      if (e.key === 'ArrowRight') { targetRow += 1; handled = true; }
      else if (e.key === 'ArrowLeft') { targetRow -= 1; handled = true; }
      else if (e.key === 'ArrowDown') { targetCol += 1; handled = true; }
      else if (e.key === 'ArrowUp') { targetCol -= 1; handled = true; }
      else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        btn.click();
        return;
      } else if (e.key === 'Escape') {
        e.preventDefault();
        var activeTab = document.querySelector('.sm-sec-tab.is-active');
        if (activeTab) activeTab.focus();
        return;
      }

      if (!handled) return;
      e.preventDefault();
      var candidates = Array.prototype.slice.call(panel.querySelectorAll('.seat-cell[data-seat-id]'));
      var nextBtn = null;
      for (var step = 0; step < 8; step++) {
        var rCheck = (e.key === 'ArrowRight') ? rowIdx + 1 + step : (e.key === 'ArrowLeft' ? rowIdx - 1 - step : rowIdx);
        var cCheck = (e.key === 'ArrowDown') ? colIdx + 1 + step : (e.key === 'ArrowUp' ? colIdx - 1 - step : colIdx);
        if (step === 0) { rCheck = targetRow; cCheck = targetCol; }
        nextBtn = candidates.find(function (c) {
          return parseInt(c.getAttribute('data-row-idx'), 10) === rCheck
              && parseInt(c.getAttribute('data-col-idx'), 10) === cCheck;
        });
        if (nextBtn) break;
      }
      if (nextBtn) {
        candidates.forEach(function (c) { c.setAttribute('tabindex', '-1'); });
        nextBtn.setAttribute('tabindex', '0');
        nextBtn.focus();
      }
    });
  });

  // Accessible Cabin View Lightbox with Focus Trap + Escape
  function openLightbox() {
    if (!lightbox) return;
    prevFocusBeforeLightbox = document.activeElement;
    lightbox.hidden = false;
    document.body.classList.add('has-sheet-open');
    if (closeLightboxBtn) closeLightboxBtn.focus();
  }

  function closeLightbox() {
    if (!lightbox || lightbox.hidden) return;
    lightbox.hidden = true;
    document.body.classList.remove('has-sheet-open');
    if (prevFocusBeforeLightbox && typeof prevFocusBeforeLightbox.focus === 'function') {
      prevFocusBeforeLightbox.focus();
    } else if (openLightboxBtn) {
      openLightboxBtn.focus();
    }
  }

  if (openLightboxBtn && lightbox) {
    openLightboxBtn.addEventListener('click', openLightbox);
    if (closeLightboxBtn) {
      closeLightboxBtn.addEventListener('click', closeLightbox);
    }
    lightbox.addEventListener('click', function (e) {
      if (e.target === lightbox) closeLightbox();
    });
    document.addEventListener('keydown', function (e) {
      if (lightbox.hidden) return;
      if (e.key === 'Escape') {
        e.preventDefault();
        closeLightbox();
      } else if (e.key === 'Tab' && closeLightboxBtn) {
        // Single interactive focusable control inside lightbox: trap focus cleanly
        e.preventDefault();
        closeLightboxBtn.focus();
      }
    });
  }

  startHoldCountdown();
})();
