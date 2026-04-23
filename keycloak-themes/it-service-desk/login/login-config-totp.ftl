<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp','userLabel'); section>
    <#if section = "form">
        <form id="kc-totp-settings-form" action="${url.loginAction}" method="post">
            <p class="form-description">
                Set up two-factor authentication to secure your account.
            </p>

            <ol class="otp-steps">
                <li>
                    <span class="step-text">Install an authenticator app on your phone (e.g. Google Authenticator, Microsoft Authenticator, or FreeOTP).</span>
                </li>
                <li>
                    <span class="step-text">Scan the QR code below with your authenticator app:</span>
                </li>
            </ol>

            <div class="otp-qr-container">
                <img src="data:image/png;base64, ${totp.totpSecretQrCode}" alt="QR Code" class="otp-qr-image" />
            </div>

            <div class="otp-secret-fallback">
                <span class="otp-secret-label">Can't scan? Enter this key manually:</span>
                <code class="otp-secret-code">${totp.totpSecretEncoded}</code>
            </div>

            <div class="form-group">
                <label for="totp" class="form-label">Verification Code</label>
                <input type="text"
                       id="totp"
                       name="totp"
                       class="form-input <#if messagesPerField.existsError('totp')>input-error</#if>"
                       autocomplete="off"
                       autofocus
                       placeholder="Enter 6-digit code"
                       inputmode="numeric"
                       pattern="[0-9]*"
                />
                <#if messagesPerField.existsError('totp')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('totp'))?no_esc}
                    </div>
                </#if>
            </div>

            <div class="form-group">
                <label for="userLabel" class="form-label">Device Name <span class="label-optional">(optional)</span></label>
                <input type="text"
                       id="userLabel"
                       name="userLabel"
                       class="form-input <#if messagesPerField.existsError('userLabel')>input-error</#if>"
                       autocomplete="off"
                       placeholder="e.g. My Phone"
                />
                <#if messagesPerField.existsError('userLabel')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('userLabel'))?no_esc}
                    </div>
                </#if>
            </div>

            <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}" />
            <#if mode??><input type="hidden" id="mode" name="mode" value="${mode}"/></#if>

            <div class="form-actions">
                <input class="submit-btn" type="submit" value="${msg("doSubmit")}" />
                <#if isAppInitiatedAction??>
                    <button class="submit-btn btn-secondary" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
                </#if>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
