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

  function setStaticBackdrop(url) {
    if (!url || !primary) return;
    primary.style.backgroundImage = 'url("' + url + '")';
    root.setAttribute('data-backdrop-src', url);
    if (nextLayer) {
      nextLayer.classList.remove('is-visible');
    }
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
  var urls = rawList.split(',').map(function (s) { return s.trim(); }).filter(Boolean).slice(0, 6);
  if (urls.length <= 1) return;

  var currentIdx = 0;
  var intervalMs = 12000;
  var fadeMs = 1500;
  var timerId = null;
  var preloadedIdx = -1;

  function preloadNextOnly(idx) {
    var nextIdx = (idx + 1) % urls.length;
    if (nextIdx === preloadedIdx) return;
    preloadedIdx = nextIdx;
    var pre = new Image();
    pre.src = urls[nextIdx];
  }

  function advance() {
    if (document.hidden) return;
    var nextIdx = (currentIdx + 1) % urls.length;
    var nextUrl = urls[nextIdx];
    nextLayer.style.backgroundImage = 'url("' + nextUrl + '")';
    nextLayer.classList.add('is-visible');

    window.setTimeout(function () {
      primary.style.backgroundImage = 'url("' + nextUrl + '")';
      nextLayer.classList.add('no-transition');
      nextLayer.classList.remove('is-visible');
      void nextLayer.offsetWidth;
      nextLayer.classList.remove('no-transition');
      currentIdx = nextIdx;
      preloadNextOnly(currentIdx);
    }, fadeMs + 60);
  }

  function startRotation() {
    if (timerId !== null || document.hidden) return;
    preloadNextOnly(currentIdx);
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
