(function () {
  var grid = document.getElementById('flightResultsGrid');
  var form = document.getElementById('filtersForm');

  // 1. Flight details accordion toggles
  var detailBtns = document.querySelectorAll('.flight-details-toggle');
  Array.prototype.forEach.call(detailBtns, function (btn) {
    btn.addEventListener('click', function () {
      var targetId = btn.getAttribute('aria-controls');
      var panel = targetId ? document.getElementById(targetId) : null;
      if (!panel) return;
      var willOpen = panel.hidden;
      panel.hidden = !willOpen;
      btn.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
      var chevron = btn.querySelector('.details-chevron');
      if (chevron) {
        chevron.textContent = willOpen ? '\u2303' : '\u2304';
      }
    });
  });

  // 2. WAI-ARIA tabs inside each Flight details panel
  var tabLists = document.querySelectorAll('.details-tablist');
  Array.prototype.forEach.call(tabLists, function (list) {
    var tabs = Array.prototype.slice.call(list.querySelectorAll('[role="tab"]'));
    function activateTab(tab) {
      var container = list.parentElement;
      if (!container) return;
      tabs.forEach(function (t) {
        var selected = t === tab;
        t.setAttribute('aria-selected', selected ? 'true' : 'false');
        t.classList.toggle('is-active', selected);
        var pid = t.getAttribute('aria-controls');
        var pane = pid ? document.getElementById(pid) : null;
        if (pane) pane.hidden = !selected;
      });
    }

    tabs.forEach(function (tab, idx) {
      tab.addEventListener('click', function () {
        activateTab(tab);
      });
      tab.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight') {
          e.preventDefault();
          var next = tabs[(idx + 1) % tabs.length];
          next.focus();
          activateTab(next);
        } else if (e.key === 'ArrowLeft') {
          e.preventDefault();
          var prev = tabs[(idx - 1 + tabs.length) % tabs.length];
          prev.focus();
          activateTab(prev);
        }
      });
    });
  });

  // 3. Search filters (client-side instant when single unfiltered page, server-submit when paginated or server-filtered)
  if (!form || !grid) return;

  var fallbackWrap = form.querySelector('.filter-submit-fallback');
  if (fallbackWrap) {
    fallbackWrap.hidden = true;
  }

  var isPaginated = grid.getAttribute('data-paginated') === 'true';
  var isServerFiltered = grid.getAttribute('data-server-filtered') === 'true';
  var useServerSubmit = isPaginated || isServerFiltered;
  var slider = document.getElementById('priceRangeSlider');
  var priceLabel = document.getElementById('priceCurrentLabel');
  var countEl = document.getElementById('resultsCountText');
  var emptyFiltered = document.getElementById('filterEmptyState');
  var resetPriceBtn = document.getElementById('resetPriceBtn');
  var resetCabinBtn = document.getElementById('resetCabinBtn');
  var resetAllEmptyBtn = document.getElementById('filterEmptyResetBtn');

  function formatRandWhole(val) {
    var n = Math.round(Number(val) || 0);
    return 'R ' + n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function updateSliderLabel() {
    if (slider && priceLabel) {
      priceLabel.textContent = 'Up to ' + formatRandWhole(slider.value);
    }
  }

  function applyClientFilters() {
    if (useServerSubmit) {
      form.submit();
      return;
    }

    var activeSlots = Array.prototype.slice.call(form.querySelectorAll('input[name="dep"]:checked'))
      .map(function (cb) { return cb.value; });
    var activeCabins = Array.prototype.slice.call(form.querySelectorAll('input[name="cabinFilter"]:checked'))
      .map(function (cb) { return cb.value; });
    var maxPrice = slider ? Number(slider.value) : Number.MAX_SAFE_INTEGER;

    var cards = Array.prototype.slice.call(grid.querySelectorAll('.flight-result-card'));
    var visible = 0;

    cards.forEach(function (card) {
      var slot = card.getAttribute('data-slot') || '';
      var price = Number(card.getAttribute('data-price') || '0');
      var econAvail = Number(card.getAttribute('data-econ-avail') || '0');
      var firstAvail = Number(card.getAttribute('data-first-avail') || '0');

      var slotOk = activeSlots.length === 0 || activeSlots.indexOf(slot) >= 0;
      var priceOk = price <= maxPrice;
      var cabinOk = activeCabins.length === 0 ||
        (activeCabins.indexOf('ECONOMY') >= 0 && econAvail > 0) ||
        (activeCabins.indexOf('FIRST') >= 0 && firstAvail > 0);

      var show = slotOk && priceOk && cabinOk;
      card.hidden = !show;
      if (show) visible++;
    });

    if (countEl) {
      countEl.textContent = 'Showing ' + visible + (visible === 1 ? ' flight' : ' flights');
    }
    if (emptyFiltered) {
      emptyFiltered.hidden = cards.length === 0 || visible > 0;
    }
  }

  Array.prototype.forEach.call(form.querySelectorAll('input[name="dep"], input[name="cabinFilter"]'), function (cb) {
    cb.addEventListener('change', applyClientFilters);
  });

  if (slider) {
    slider.addEventListener('input', function () {
      updateSliderLabel();
      if (!useServerSubmit) {
        applyClientFilters();
      }
    });
    slider.addEventListener('change', function () {
      updateSliderLabel();
      applyClientFilters();
    });
    updateSliderLabel();
  }

  if (resetPriceBtn && slider) {
    resetPriceBtn.addEventListener('click', function () {
      slider.value = slider.max;
      updateSliderLabel();
      applyClientFilters();
    });
  }

  if (resetCabinBtn) {
    resetCabinBtn.addEventListener('click', function () {
      Array.prototype.forEach.call(form.querySelectorAll('input[name="cabinFilter"]'), function (cb) {
        cb.checked = false;
      });
      applyClientFilters();
    });
  }

  if (resetAllEmptyBtn) {
    resetAllEmptyBtn.addEventListener('click', function () {
      Array.prototype.forEach.call(form.querySelectorAll('input[type="checkbox"]'), function (cb) {
        cb.checked = false;
      });
      if (slider) {
        slider.value = slider.max;
        updateSliderLabel();
      }
      applyClientFilters();
    });
  }
})();
