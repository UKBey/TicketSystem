<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>
    <#if section = "form">
        <form id="kc-otp-login-form" action="${url.loginAction}" method="post">
            <p class="form-description">
                Enter the verification code from your authenticator app.
            </p>

            <#if otpLogin.userOtpCredentials?size gt 1>
                <div class="form-group">
                    <label class="form-label">Select your device</label>
                    <div class="otp-device-list">
                        <#list otpLogin.userOtpCredentials as otpCredential>
                            <label class="otp-device-item">
                                <input type="radio"
                                       id="kc-otp-credential-${otpCredential?index}"
                                       name="selectedCredentialId"
                                       value="${otpCredential.id}"
                                       <#if otpCredential.id == otpLogin.selectedCredentialId>checked="checked"</#if>
                                />
                                <span class="otp-device-name">${otpCredential.userLabel}</span>
                            </label>
                        </#list>
                    </div>
                </div>
            </#if>

            <div class="form-group">
                <label for="otp" class="form-label">Verification Code</label>
                <input type="text"
                       id="otp"
                       name="otp"
                       class="form-input otp-input <#if messagesPerField.existsError('totp')>input-error</#if>"
                       autocomplete="off"
                       autofocus
                       placeholder="000000"
                       inputmode="numeric"
                       pattern="[0-9]*"
                       maxlength="6"
                />
                <#if messagesPerField.existsError('totp')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('totp'))?no_esc}
                    </div>
                </#if>
            </div>

            <div class="form-actions">
                <input class="submit-btn" type="submit" value="${msg("doLogIn")}" />
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
