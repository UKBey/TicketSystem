<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp','userLabel'); section>
    <#if section = "form">
        <form id="kc-totp-settings-form" action="${url.loginAction}" method="post">
            <p class="form-description">${msg("otpSetupDesc")}</p>

            <ol class="otp-steps">
                <li><span class="step-text">${msg("otpStep1")}</span></li>
                <li><span class="step-text">${msg("otpStep2")}</span></li>
            </ol>

            <div class="otp-qr-container">
                <img src="data:image/png;base64, ${totp.totpSecretQrCode}" alt="QR Code" class="otp-qr-image" />
            </div>

            <div class="otp-secret-fallback">
                <span class="otp-secret-label">${msg("otpCantScan")}</span>
                <code class="otp-secret-code">${totp.totpSecretEncoded}</code>
            </div>

            <div class="form-group">
                <label for="totp" class="form-label">${msg("otpVerificationCode")}</label>
                <input type="text"
                       id="totp"
                       name="totp"
                       class="form-input <#if messagesPerField.existsError('totp')>input-error</#if>"
                       autocomplete="off"
                       autofocus
                       placeholder="${msg('otpCodePlaceholder')}"
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
                <label for="userLabel" class="form-label">${msg("otpDeviceName")} <span class="label-optional">${msg("otpDeviceNameOptional")}</span></label>
                <input type="text"
                       id="userLabel"
                       name="userLabel"
                       class="form-input <#if messagesPerField.existsError('userLabel')>input-error</#if>"
                       autocomplete="off"
                       placeholder="${msg('otpDevicePlaceholder')}"
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
