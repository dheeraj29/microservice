/**
 * OmniBus Keycloak Theme — Client-Side Native Vector SVG CAPTCHA Engine
 */
(function() {
  var CHARSET = "2345679ACDEFGHJKLMNPQRSTUVWXYZ";
  var CODE_LENGTH = 5;
  var COLOR_PALETTE = ["#2563EB", "#7C3AED", "#059669", "#D97706", "#DC2626", "#0D9488", "#4F46E5"];
  var currentCode = "";

  function generateCaptcha() {
    var code = "";
    for (var i = 0; i < CODE_LENGTH; i++) {
      var idx = Math.floor(Math.random() * CHARSET.length);
      code += CHARSET.charAt(idx);
    }
    currentCode = code;

    var width = 200;
    var height = 65;
    var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + width + '" height="' + height + '" viewBox="0 0 ' + width + ' ' + height + '" style="background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%); border-radius: 8px; border: 1px solid #cbd5e1; user-select: none;">';

    // Noise dots
    svg += '<g opacity="0.45">';
    for (var d = 0; d < 25; d++) {
      var cx = Math.floor(Math.random() * width);
      var cy = Math.floor(Math.random() * height);
      var r = Math.floor(Math.random() * 3) + 1;
      var color = COLOR_PALETTE[Math.floor(Math.random() * COLOR_PALETTE.length)];
      svg += '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" fill="' + color + '" />';
    }
    svg += '</g>';

    // Interference bezier curves
    svg += '<g fill="none" stroke-width="1.5" opacity="0.35">';
    for (var c = 0; c < 3; c++) {
      var sx = Math.floor(Math.random() * 20);
      var sy = 10 + Math.floor(Math.random() * (height - 20));
      var c1x = 40 + Math.floor(Math.random() * 60);
      var c1y = Math.floor(Math.random() * height);
      var c2x = 110 + Math.floor(Math.random() * 60);
      var c2y = Math.floor(Math.random() * height);
      var ex = width - 10 - Math.floor(Math.random() * 20);
      var ey = 10 + Math.floor(Math.random() * (height - 20));
      var cr = COLOR_PALETTE[Math.floor(Math.random() * COLOR_PALETTE.length)];
      svg += '<path d="M' + sx + ',' + sy + ' C' + c1x + ',' + c1y + ' ' + c2x + ',' + c2y + ' ' + ex + ',' + ey + '" stroke="' + cr + '" />';
    }
    svg += '</g>';

    // Glyphs
    var charSpacing = (width - 40) / code.length;
    var startX = 25;
    for (var g = 0; g < code.length; g++) {
      var ch = code.charAt(g);
      var x = startX + (g * charSpacing) + (Math.floor(Math.random() * 7) - 3);
      var y = 42 + Math.floor(Math.sin(g * 1.2) * 5) + (Math.floor(Math.random() * 5) - 2);
      var rotate = Math.floor(Math.random() * 25) - 12;
      var clr = COLOR_PALETTE[Math.floor(Math.random() * COLOR_PALETTE.length)];
      var fsize = 28 + Math.floor(Math.random() * 5);
      svg += '<text x="' + x + '" y="' + y + '" font-family="Segoe UI, Roboto, sans-serif" font-weight="bold" font-size="' + fsize + '" fill="' + clr + '" transform="rotate(' + rotate + ',' + x + ',' + y + ')" letter-spacing="2">' + ch + '</text>';
    }

    svg += '</svg>';

    var container = document.getElementById('captcha-image-container');
    if (container) {
      container.innerHTML = svg;
    }
    var answerInput = document.getElementById('captchaAnswer');
    if (answerInput) {
      answerInput.value = '';
    }
  }

  window.refreshCaptcha = function() {
    generateCaptcha();
  };

  window.quickFill = function(username, password) {
    var u = document.getElementById('username');
    var p = document.getElementById('password');
    if (u) u.value = username;
    if (p) p.value = password;
    var ans = document.getElementById('captchaAnswer');
    if (ans) ans.focus();
  };

  document.addEventListener('DOMContentLoaded', function() {
    generateCaptcha();

    var form = document.getElementById('kc-form-login');
    if (form) {
      form.addEventListener('submit', function(e) {
        var ans = document.getElementById('captchaAnswer');
        var errBanner = document.getElementById('captcha-error-banner');
        if (!ans || ans.value.trim().toUpperCase() !== currentCode) {
          e.preventDefault();
          if (errBanner) {
            errBanner.style.display = 'flex';
            errBanner.textContent = 'Invalid CAPTCHA code. Please enter the characters shown above.';
          } else {
            alert('Invalid CAPTCHA code. Please enter the characters shown above.');
          }
          generateCaptcha();
          if (ans) ans.focus();
          return false;
        }
        if (errBanner) {
          errBanner.style.display = 'none';
        }
      });
    }
  });
})();
