(function () {
  var avatarBtn = document.getElementById('avatarMenuBtn');
  var avatarMenu = document.getElementById('avatarMenu');

  function closeAvatarMenu(restoreFocus) {
    if (!avatarBtn || !avatarMenu || avatarMenu.hidden) return;
    avatarMenu.hidden = true;
    avatarBtn.setAttribute('aria-expanded', 'false');
    if (restoreFocus) avatarBtn.focus();
  }

  function openAvatarMenu() {
    if (!avatarBtn || !avatarMenu) return;
    avatarMenu.hidden = false;
    avatarBtn.setAttribute('aria-expanded', 'true');
  }

  if (avatarBtn && avatarMenu) {
    avatarBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      if (avatarMenu.hidden) {
        openAvatarMenu();
      } else {
        closeAvatarMenu(false);
      }
    });

    avatarBtn.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
        if (avatarMenu.hidden) {
          e.preventDefault();
          openAvatarMenu();
          var first = avatarMenu.querySelector('[role="menuitem"]');
          if (first) first.focus();
        }
      } else if (e.key === 'Escape') {
        closeAvatarMenu(true);
      }
    });

    avatarMenu.addEventListener('keydown', function (e) {
      var items = Array.prototype.slice.call(avatarMenu.querySelectorAll('[role="menuitem"]'));
      var idx = items.indexOf(document.activeElement);
      if (e.key === 'Escape') {
        e.preventDefault();
        closeAvatarMenu(true);
      } else if (e.key === 'ArrowDown' && items.length > 0) {
        e.preventDefault();
        items[(idx + 1) % items.length].focus();
      } else if (e.key === 'ArrowUp' && items.length > 0) {
        e.preventDefault();
        items[(idx - 1 + items.length) % items.length].focus();
      } else if (e.key === 'Tab') {
        closeAvatarMenu(false);
      }
    });

    document.addEventListener('click', function (e) {
      if (!avatarMenu.hidden && !avatarMenu.contains(e.target) && !avatarBtn.contains(e.target)) {
        closeAvatarMenu(false);
      }
    });
  }

  var mobileBtn = document.getElementById('mobileMenuBtn');
  var sheetBackdrop = document.getElementById('mobileNavSheet');
  var sheetClose = document.getElementById('mobileSheetClose');
  var sheetPanel = sheetBackdrop ? sheetBackdrop.querySelector('.mobile-sheet-panel') : null;
  var prevFocus = null;

  function getSheetFocusables() {
    if (!sheetPanel) return [];
    return Array.prototype.slice.call(
      sheetPanel.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])')
    );
  }

  function openMobileSheet() {
    if (!sheetBackdrop || !mobileBtn) return;
    prevFocus = document.activeElement;
    sheetBackdrop.hidden = false;
    document.body.classList.add('has-sheet-open');
    mobileBtn.setAttribute('aria-expanded', 'true');
    var focusables = getSheetFocusables();
    if (focusables.length > 0) {
      focusables[0].focus();
    }
  }

  function closeMobileSheet() {
    if (!sheetBackdrop || sheetBackdrop.hidden) return;
    sheetBackdrop.hidden = true;
    document.body.classList.remove('has-sheet-open');
    if (mobileBtn) mobileBtn.setAttribute('aria-expanded', 'false');
    if (prevFocus && typeof prevFocus.focus === 'function') {
      prevFocus.focus();
    } else if (mobileBtn) {
      mobileBtn.focus();
    }
  }

  if (mobileBtn && sheetBackdrop && sheetPanel) {
    mobileBtn.addEventListener('click', openMobileSheet);
    if (sheetClose) {
      sheetClose.addEventListener('click', closeMobileSheet);
    }
    sheetBackdrop.addEventListener('click', function (e) {
      if (e.target === sheetBackdrop) {
        closeMobileSheet();
      }
    });
    document.addEventListener('keydown', function (e) {
      if (sheetBackdrop.hidden) return;
      if (e.key === 'Escape') {
        e.preventDefault();
        closeMobileSheet();
        return;
      }
      if (e.key === 'Tab') {
        var focusables = getSheetFocusables();
        if (focusables.length === 0) return;
        var first = focusables[0];
        var last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    });
  }

  // Optional subtitle & friendly date sync for Home / Flights search strips
  var DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
  var SHORT_DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  var SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  var syncControls = document.querySelectorAll('[data-sync-sub]');
  Array.prototype.forEach.call(syncControls, function (ctrl) {
    var targetId = ctrl.getAttribute('data-sync-sub');
    var target = document.getElementById(targetId);
    var dispId = ctrl.getAttribute('data-display-target');
    var dispTarget = dispId ? document.getElementById(dispId) : null;
    if (!target && !dispTarget) return;

    function updateSub() {
      if (ctrl.tagName === 'SELECT') {
        var opt = ctrl.options[ctrl.selectedIndex];
        if (target && opt && opt.getAttribute('data-subtitle')) {
          target.textContent = opt.getAttribute('data-subtitle');
        }
      } else if (ctrl.type === 'date' && ctrl.value) {
        var parts = ctrl.value.split('-');
        if (parts.length === 3) {
          var dt = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
          if (!isNaN(dt.getTime())) {
            if (target) {
              target.textContent = DAYS[dt.getDay()];
            }
            if (dispTarget) {
              dispTarget.textContent = SHORT_DAYS[dt.getDay()] + ' ' + dt.getDate() + ' ' + SHORT_MONTHS[dt.getMonth()];
            }
          }
        }
      }
    }

    ctrl.addEventListener('change', updateSub);
    ctrl.addEventListener('input', updateSub);
    updateSub();
  });
})();
