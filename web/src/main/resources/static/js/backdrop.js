(function () {
  var root = document.querySelector('.sr-backdrop-root');
  if (!root) return;

  var primary = root.querySelector('.backdrop-layer.is-primary');
  var nextLayer = root.querySelector('.backdrop-layer.is-next');
  if (!primary) return;

  var mode = root.getAttribute('data-backdrop-mode') || 'fixed';
  var initialSrc = root.getAttribute('data-backdrop-src') || '';
  if (initialSrc) {
    primary.style.backgroundImage = 'url("' + initialSrc + '")';
  }

  var cabinSwapTimer = null;

  function setStaticBackdrop(url) {
    if (!url || !primary) return;
    if (root.getAttribute('data-backdrop-src') === url) return;
    if (!nextLayer) {
      primary.style.backgroundImage = 'url("' + url + '")';
      root.setAttribute('data-backdrop-src', url);
      return;
    }
    if (cabinSwapTimer) {
      window.clearTimeout(cabinSwapTimer);
      cabinSwapTimer = null;
    }
    nextLayer.style.transition = 'opacity 200ms ease';
    nextLayer.style.backgroundImage = 'url("' + url + '")';
    nextLayer.classList.add('is-visible');
    root.setAttribute('data-backdrop-src', url);
    cabinSwapTimer = window.setTimeout(function () {
      primary.style.backgroundImage = 'url("' + url + '")';
      nextLayer.classList.add('no-transition');
      nextLayer.classList.remove('is-visible');
      void nextLayer.offsetWidth;
      nextLayer.classList.remove('no-transition');
      nextLayer.style.transition = '';
      cabinSwapTimer = null;
    }, 220);
  }

  window.SkyRoyalBackdrop = {
    setCabin: function (url) {
      if (!url) return;
      var img = new Image();
      img.onload = function () {
        setStaticBackdrop(url);
      };
      img.src = url;
    }
  };

  if (mode !== 'rotate' || !nextLayer) return;

  var reducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reducedMotion) return;

  var rawList = root.getAttribute('data-backdrop-list') || '';
  var allUrls = rawList.split(',').map(function (s) { return s.trim(); }).filter(Boolean);
  if (allUrls.length <= 1) return;

  // Fisher-Yates shuffle keeping the initial image first so there is no immediate jump or repeat
  function buildShuffledQueue(items, firstUrl) {
    var rest = items.filter(function (u) { return u !== firstUrl; });
    for (var i = rest.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var tmp = rest[i];
      rest[i] = rest[j];
      rest[j] = tmp;
    }
    return firstUrl ? [firstUrl].concat(rest) : rest;
  }

  var queue = buildShuffledQueue(allUrls, initialSrc || allUrls[0]);
  var currentIdx = 0;
  var intervalMs = 12000;
  var fadeMs = 1500;
  var timerId = null;
  var preloadedUrl = '';

  function preloadNextOnly() {
    var nextIdx = (currentIdx + 1) % queue.length;
    var candidate = queue[nextIdx];
    if (!candidate || candidate === preloadedUrl) return;
    preloadedUrl = candidate;
    var pre = new Image();
    pre.src = candidate;
  }

  function advance() {
    if (document.hidden) return;
    var nextIdx = currentIdx + 1;
    if (nextIdx >= queue.length) {
      var lastShown = queue[currentIdx];
      queue = buildShuffledQueue(allUrls, lastShown);
      nextIdx = 1;
    }
    var nextUrl = queue[nextIdx];
    nextLayer.style.backgroundImage = 'url("' + nextUrl + '")';
    nextLayer.classList.add('is-visible');
    root.setAttribute('data-backdrop-src', nextUrl);

    window.setTimeout(function () {
      primary.style.backgroundImage = 'url("' + nextUrl + '")';
      nextLayer.classList.add('no-transition');
      nextLayer.classList.remove('is-visible');
      void nextLayer.offsetWidth;
      nextLayer.classList.remove('no-transition');
      currentIdx = nextIdx;
      preloadNextOnly();
    }, fadeMs + 60);
  }

  window.SkyRoyalBackdrop.advanceNow = advance;

  function startRotation() {
    if (timerId !== null || document.hidden) return;
    preloadNextOnly();
    timerId = window.setInterval(advance, intervalMs);
  }

  function stopRotation() {
    if (timerId !== null) {
      window.clearInterval(timerId);
      timerId = null;
    }
  }

  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      stopRotation();
    } else {
      startRotation();
    }
  });

  startRotation();
})();
