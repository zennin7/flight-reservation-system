(function () {
    'use strict';

    var cardInput = document.getElementById('cardNumber');
    var nameInput = document.getElementById('cardholderName');
    var expInput = document.getElementById('cardExpiry');
    var cvvInput = document.getElementById('cardCvv');
    var passengerInput = document.getElementById('passengerName');

    var badge = document.getElementById('cardBrandBadge');
    var previewDigits = document.getElementById('cardPreviewDigits');
    var previewBrand = document.getElementById('cardPreviewBrand');
    var previewHolder = document.getElementById('cardPreviewHolder');
    var previewExpiry = document.getElementById('cardPreviewExpiry');
    var summaryTraveller = document.getElementById('summaryTravellerName');

    var timerBox = document.getElementById('holdTimerBox');
    var timerClock = document.getElementById('holdCountdown');
    var expiredAlert = document.getElementById('holdExpiredInlineAlert');
    var payBtn = document.getElementById('paySubmitBtn');

    function detectBrand(digits) {
        if (!digits) return 'CARD';
        if (digits.charAt(0) === '4') return 'VISA';
        if (/^(5[1-5]|2[2-7])/.test(digits)) return 'MASTERCARD';
        if (/^(34|37)/.test(digits)) return 'AMEX';
        return 'CARD';
    }

    function formatPreviewMask(digits) {
        if (!digits) {
            return '•••• •••• •••• ••••';
        }
        var last4 = digits.length > 4 ? digits.slice(-4) : digits;
        while (last4.length < 4) {
            last4 = '•' + last4;
        }
        return '•••• •••• •••• ' + last4;
    }

    function syncCardPreview() {
        var digits = cardInput ? (cardInput.value || '').replace(/\D/g, '').slice(0, 19) : '';
        var brand = detectBrand(digits);
        if (badge) badge.textContent = brand;
        if (previewBrand) previewBrand.textContent = brand;
        if (previewDigits) previewDigits.textContent = formatPreviewMask(digits);

        var holder = nameInput && nameInput.value ? nameInput.value.trim() : '';
        if (!holder && passengerInput && passengerInput.value) {
            holder = passengerInput.value.trim();
        }
        if (previewHolder) {
            previewHolder.textContent = holder ? holder.toUpperCase() : 'CARDHOLDER';
        }

        var exp = expInput && expInput.value ? expInput.value.trim() : '';
        if (previewExpiry) {
            previewExpiry.textContent = exp || 'MM/YY';
        }
    }

    if (cardInput) {
        cardInput.addEventListener('input', function () {
            var digits = (cardInput.value || '').replace(/\D/g, '').slice(0, 19);
            cardInput.value = digits.replace(/(\d{4})(?=\d)/g, '$1 ');
            syncCardPreview();
        });
    }

    if (nameInput) {
        nameInput.addEventListener('input', syncCardPreview);
    }

    if (passengerInput) {
        passengerInput.addEventListener('input', function () {
            if (summaryTraveller) {
                var val = (passengerInput.value || '').trim();
                summaryTraveller.textContent = val || 'Pending traveller details';
            }
            syncCardPreview();
        });
    }

    if (expInput) {
        expInput.addEventListener('input', function () {
            var raw = (expInput.value || '').replace(/\D/g, '').slice(0, 4);
            expInput.value = raw.length >= 3 ? raw.slice(0, 2) + '/' + raw.slice(2) : raw;
            syncCardPreview();
        });
    }

    if (cvvInput) {
        cvvInput.addEventListener('input', function () {
            cvvInput.value = (cvvInput.value || '').replace(/\D/g, '').slice(0, 4);
        });
    }

    syncCardPreview();

    if (timerBox && timerClock) {
        var expiresMs = parseInt(timerBox.getAttribute('data-expires-ms') || '0', 10);
        if (expiresMs > 0) {
            var tick = function () {
                var diffSec = Math.max(0, Math.floor((expiresMs - Date.now()) / 1000));
                var mm = String(Math.floor(diffSec / 60)).padStart(2, '0');
                var ss = String(diffSec % 60).padStart(2, '0');
                timerClock.textContent = mm + ':' + ss;
                if (diffSec <= 0) {
                    timerBox.classList.add('is-expired');
                    if (expiredAlert) expiredAlert.hidden = false;
                    if (payBtn) payBtn.disabled = true;
                    clearInterval(timerId);
                }
            };
            tick();
            var timerId = setInterval(tick, 1000);
        }
    }
})();
