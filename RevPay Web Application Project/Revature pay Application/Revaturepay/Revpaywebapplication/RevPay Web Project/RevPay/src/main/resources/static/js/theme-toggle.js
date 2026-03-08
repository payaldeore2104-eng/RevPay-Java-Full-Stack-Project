/* RevPay – Theme Toggle Script */
(function () {
  function applyTheme(dark) {
    document.body.classList.toggle('dark-mode', dark);
    const icons = document.querySelectorAll('.theme-icon');
    icons.forEach(function(ic) { ic.textContent = dark ? '☀️' : '🌙'; });
  }

  window.toggleTheme = function () {
    const isDark = document.body.classList.contains('dark-mode');
    const next = !isDark;
    localStorage.setItem('revpay-theme', next ? 'dark' : 'light');
    applyTheme(next);
  };

  // Restore on load (avoid flash)
  var saved = localStorage.getItem('revpay-theme');
  if (saved === 'dark') {
    document.documentElement.classList.add('dark-start');
    document.addEventListener('DOMContentLoaded', function () {
      document.documentElement.classList.remove('dark-start');
      applyTheme(true);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var saved2 = localStorage.getItem('revpay-theme');
    applyTheme(saved2 === 'dark');
  });
})();
