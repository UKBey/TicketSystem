/* IT Service Desk — Account console custom controls bar
   Injects a floating top-right bar with language switcher and dark/light
   theme toggle. Mirrors the login template controls so the two surfaces
   feel like one product. */

(function () {
  'use strict';

  // ─── Apply saved theme before paint to avoid flash ─────────
  var savedTheme = null;
  try { savedTheme = localStorage.getItem('theme'); } catch (e) {}
  var preferred = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', savedTheme || preferred);

  function injectControls() {
    if (document.querySelector('.it-controls-bar')) return;

    var bar = document.createElement('div');
    bar.className = 'it-controls-bar';

    // ─── Determine current locale ─────────────────────────────
    var currentUrl    = new URL(window.location.href);
    var currentLocale = currentUrl.searchParams.get('kc_locale') || document.documentElement.lang || 'en';
    currentLocale = currentLocale.toLowerCase().startsWith('tr') ? 'tr' : 'en';

    var locales = [
      { tag: 'tr', label: 'Türkçe' },
      { tag: 'en', label: 'English' }
    ];

    // ─── Language switcher ────────────────────────────────────
    var langSwitcher = document.createElement('div');
    langSwitcher.className = 'it-lang-switcher';

    var langBtn = document.createElement('button');
    langBtn.className = 'it-lang-btn';
    langBtn.type = 'button';
    langBtn.setAttribute('aria-haspopup', 'listbox');
    langBtn.setAttribute('aria-expanded', 'false');
    langBtn.setAttribute('aria-label', 'Select language');
    langBtn.innerHTML =
      '<span class="it-lang-label">' + currentLocale.toUpperCase() + '</span>' +
      '<svg class="it-lang-chevron" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" ' +
      'stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">' +
      '<polyline points="6 9 12 15 18 9"/></svg>';

    var dropdown = document.createElement('div');
    dropdown.className = 'it-lang-dropdown';
    dropdown.setAttribute('role', 'listbox');

    locales.forEach(function (l) {
      var isActive = l.tag === currentLocale;
      var item = document.createElement('a');
      item.className = 'it-lang-option' + (isActive ? ' it-lang-option--active' : '');
      // Build URL with kc_locale param while preserving everything else (including hash route)
      var u = new URL(window.location.href);
      u.searchParams.set('kc_locale', l.tag);
      item.href = u.toString();
      item.setAttribute('role', 'option');
      item.setAttribute('aria-selected', isActive ? 'true' : 'false');
      item.innerHTML =
        '<span class="it-lang-option-label">' + l.label + '</span>' +
        (isActive
          ? '<svg class="it-lang-check" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" ' +
            'stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">' +
            '<polyline points="20 6 9 17 4 12"/></svg>'
          : '');
      dropdown.appendChild(item);
    });

    langSwitcher.appendChild(langBtn);
    langSwitcher.appendChild(dropdown);

    langBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var isOpen = dropdown.classList.toggle('it-open');
      langBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    });

    document.addEventListener('click', function (e) {
      if (!langSwitcher.contains(e.target)) {
        dropdown.classList.remove('it-open');
        langBtn.setAttribute('aria-expanded', 'false');
      }
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        dropdown.classList.remove('it-open');
        langBtn.setAttribute('aria-expanded', 'false');
      }
    });

    // ─── Theme toggle ─────────────────────────────────────────
    var themeBtn = document.createElement('button');
    themeBtn.className = 'it-theme-toggle';
    themeBtn.type = 'button';
    themeBtn.setAttribute('aria-label', 'Toggle dark/light mode');
    themeBtn.innerHTML =
      '<svg class="it-icon-moon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" ' +
      'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>' +
      '<svg class="it-icon-sun" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" ' +
      'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
      '<circle cx="12" cy="12" r="5"/>' +
      '<line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/>' +
      '<line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>' +
      '<line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/>' +
      '<line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>' +
      '</svg>';

    themeBtn.addEventListener('click', function () {
      var current = document.documentElement.getAttribute('data-theme');
      var next = current === 'light' ? 'dark' : 'light';
      document.documentElement.setAttribute('data-theme', next);
      try { localStorage.setItem('theme', next); } catch (e) {}
    });

    bar.appendChild(langSwitcher);
    bar.appendChild(themeBtn);
    document.body.appendChild(bar);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', injectControls);
  } else {
    injectControls();
  }
})();
