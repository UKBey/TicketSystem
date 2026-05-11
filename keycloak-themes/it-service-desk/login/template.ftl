<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <#if properties.stylesCommon?has_content>
        <#list properties.stylesCommon?split(' ') as style>
            <link href="${url.resourcesCommonPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <link rel="icon" type="image/x-icon" href="${url.resourcesPath}/img/favicon.ico" />
    <link rel="icon" type="image/png" sizes="16x16" href="${url.resourcesPath}/img/favicon-16x16.png" />
    <link rel="icon" type="image/png" sizes="32x32" href="${url.resourcesPath}/img/favicon-32x32.png" />
    <link rel="apple-touch-icon" sizes="180x180" href="${url.resourcesPath}/img/apple-touch-icon.png" />
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <!-- Apply saved theme before paint to avoid flash.
         Uses the same localStorage key ('theme') as the React frontend
         so dark/light preference is shared across both apps. -->
    <script>
      (function () {
        var saved = localStorage.getItem('theme');
        var preferred = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', saved || preferred);

        // Sync Keycloak locale → React app (i18next reads localStorage key 'language')
        // msg("langCode") returns "TR" or "EN" — reliable since it drives the button and subtitle too
        var kcLangCode = '${msg("langCode")}';
        localStorage.setItem('language', kcLangCode === 'TR' ? 'tr' : 'en');
      })();
    </script>
</head>
<body>
    <!-- Controls bar: language switcher + theme toggle -->
    <div class="controls-bar">
        <#if locale?? && locale.supported?? && (locale.supported?size > 1)>
        <#-- Use msg() for current lang display — proven reliable since msg() already drives subtitle -->
        <#assign _msgLang = msg("langCode")>
        <div class="lang-switcher" id="lang-switcher">
            <button class="lang-btn" id="lang-btn" type="button" aria-haspopup="listbox" aria-expanded="false" aria-label="Select language">
                <span class="lang-label">${_msgLang}</span>
                <svg class="lang-chevron" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="6 9 12 15 18 9"/>
                </svg>
            </button>
            <div class="lang-dropdown" id="lang-dropdown" role="listbox" aria-label="Language options">
                <#list locale.supported as localeItem>
                <a class="lang-option<#if localeItem.languageTag == locale.current> lang-option--active</#if>"
                   href="${localeItem.url}"
                   role="option"
                   aria-selected="${(localeItem.languageTag == locale.current)?string('true', 'false')}">
                    <span class="lang-option-label">${localeItem.label}</span>
                    <#if localeItem.languageTag == locale.current>
                    <svg class="lang-check" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                    </svg>
                    </#if>
                </a>
                </#list>
            </div>
        </div>
        </#if>

        <button class="theme-toggle" id="theme-toggle" type="button" aria-label="Toggle dark/light mode">
            <svg class="icon-moon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
            <svg class="icon-sun" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="5"/>
                <line x1="12" y1="1"  x2="12" y2="3"/>
                <line x1="12" y1="21" x2="12" y2="23"/>
                <line x1="4.22" y1="4.22"  x2="5.64" y2="5.64"/>
                <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
                <line x1="1"  y1="12" x2="3"  y2="12"/>
                <line x1="21" y1="12" x2="23" y2="12"/>
                <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
                <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
        </button>
    </div>

    <div class="login-page">
        <!-- Background decorative elements -->
        <div class="bg-decor">
            <div class="bg-orb bg-orb-1"></div>
            <div class="bg-orb bg-orb-2"></div>
            <div class="bg-orb bg-orb-3"></div>
        </div>

        <div class="login-container">
            <!-- Main card -->
            <div class="login-card">
                <!-- Logo -->
                <div class="logo-container">
                    <div class="logo-icon">
                        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M3 11h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-5Zm0 0a9 9 0 1 1 18 0m0 0v5a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3Z"/>
                            <path d="M21 16v2a4 4 0 0 1-4 4h-5"/>
                        </svg>
                    </div>
                </div>

                <h1 class="login-title">IT Service Desk</h1>
                <p class="login-subtitle">${msg("loginSubtitle")}</p>

                <!-- Error / Info messages -->
                <#if displayMessage && message?has_content && (message.type != 'warning' || !(isAppInitiatedAction!false))>
                    <div class="alert alert-${message.type}">
                        ${kcSanitize(message.summary)?no_esc}
                    </div>
                </#if>

                <#nested "form">

                <#if (realm.password!false) && social?? && social.providers??>
                    <div class="social-divider">
                        <span>or</span>
                    </div>
                    <div class="social-providers">
                        <#list social.providers as p>
                            <a id="social-${p.alias}" class="social-link" href="${p.loginUrl}">
                                ${p.displayName!}
                            </a>
                        </#list>
                    </div>
                </#if>

                <#if displayInfo>
                    <div class="login-info">
                        <#nested "info">
                    </div>
                </#if>
            </div>
        </div>
    </div>

    <script>
      (function () {
        // Theme toggle
        var themeBtn = document.getElementById('theme-toggle');
        if (themeBtn) {
          themeBtn.addEventListener('click', function () {
            var current = document.documentElement.getAttribute('data-theme');
            var next = current === 'light' ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', next);
            localStorage.setItem('theme', next);
          });
        }

        // Language switcher
        var switcher  = document.getElementById('lang-switcher');
        var langBtn   = document.getElementById('lang-btn');
        var dropdown  = document.getElementById('lang-dropdown');
        if (!langBtn || !dropdown) return;

        function closeDropdown() {
          dropdown.classList.remove('open');
          langBtn.setAttribute('aria-expanded', 'false');
        }

        langBtn.addEventListener('click', function (e) {
          e.stopPropagation();
          var isOpen = dropdown.classList.contains('open');
          if (isOpen) {
            closeDropdown();
          } else {
            dropdown.classList.add('open');
            langBtn.setAttribute('aria-expanded', 'true');
          }
        });

        document.addEventListener('click', function (e) {
          if (switcher && !switcher.contains(e.target)) closeDropdown();
        });

        document.addEventListener('keydown', function (e) {
          if (e.key === 'Escape') closeDropdown();
        });
      })();
    </script>
</body>
</html>
</#macro>
