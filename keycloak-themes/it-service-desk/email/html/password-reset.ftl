<#ftl output_format="HTML">
<#--
  IT Service Desk — şifre sıfırlama maili (markalı HTML).
  Keycloak değişkenleri: link, linkExpiration, linkExpirationFormatter, user, realmName.
  Metinler email/messages/messages_{en,tr}.properties'ten gelir; dil kullanıcının
  locale'ine göre Keycloak tarafından seçilir.
-->
<#assign name = (user.firstName)!'' />
<#if !name?has_content><#assign name = (user.email)!'' /></#if>
<!DOCTYPE html>
<html lang="${msg('itPwResetLang')}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <meta name="color-scheme" content="light only">
</head>
<body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:32px 16px;">
    <tr><td align="center">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
        <tr><td style="background:linear-gradient(135deg,#2563eb 0%,#1e40af 100%);padding:32px;">
          <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
            <tr>
              <td style="vertical-align:middle;">
                <div style="display:inline-block;width:44px;height:44px;line-height:44px;text-align:center;background:rgba(255,255,255,0.18);border-radius:12px;font-size:22px;color:#fff;font-weight:700;">IT</div>
              </td>
              <td style="vertical-align:middle;padding-left:14px;">
                <div style="color:rgba(255,255,255,0.75);font-size:12px;letter-spacing:1.5px;text-transform:uppercase;font-weight:600;">IT Service Desk</div>
                <div style="color:#ffffff;font-size:20px;font-weight:700;margin-top:2px;">${msg('itPwResetTitle')}</div>
              </td>
            </tr>
          </table>
        </td></tr>
        <tr><td style="padding:32px;">
          <p style="margin:0 0 12px 0;font-size:15px;color:#111827;">${msg('itPwResetGreeting', name)}</p>
          <p style="margin:0 0 8px 0;font-size:15px;line-height:1.6;color:#374151;">${msg('itPwResetIntro')}</p>
          <p style="margin:0 0 24px 0;font-size:15px;line-height:1.6;color:#374151;">${msg('itPwResetValidity', linkExpirationFormatter(linkExpiration))}</p>
          <p style="margin:0 0 24px 0;text-align:center;">
            <a href="${link}" style="display:inline-block;padding:14px 28px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:10px;font-size:15px;font-weight:600;">${msg('itPwResetCta')}</a>
          </p>
          <p style="margin:0 0 8px 0;font-size:13px;color:#6b7280;">${msg('itPwResetFallback')}</p>
          <p style="margin:0 0 24px 0;font-size:12px;color:#6b7280;word-break:break-all;">${link}</p>
          <p style="margin:0;font-size:13px;color:#6b7280;line-height:1.5;">${msg('itPwResetIgnore')}</p>
        </td></tr>
        <tr><td style="padding:0 32px 28px 32px;">
          <div style="border-top:1px solid #e5e7eb;padding-top:18px;font-size:12px;color:#6b7280;text-align:center;line-height:1.5;">${msg('itPwResetFooter')}</div>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>
