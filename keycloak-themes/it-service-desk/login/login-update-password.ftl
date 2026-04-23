<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <#if section = "form">
        <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">
            <p class="form-description">
                Please set a new password for your account.
            </p>

            <input type="text" id="username" name="username" value="${username}" autocomplete="username" readonly="readonly" style="display:none;"/>
            <input type="password" id="password-hidden" name="password-hidden" autocomplete="current-password" style="display:none;"/>

            <div class="form-group">
                <label for="password-new" class="form-label">${msg("passwordNew")}</label>
                <input type="password"
                       id="password-new"
                       name="password-new"
                       class="form-input <#if messagesPerField.existsError('password','password-confirm')>input-error</#if>"
                       autofocus
                       autocomplete="new-password"
                       placeholder="Enter new password"
                />
                <#if messagesPerField.existsError('password')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}
                    </div>
                </#if>
            </div>

            <div class="form-group">
                <label for="password-confirm" class="form-label">${msg("passwordConfirm")}</label>
                <input type="password"
                       id="password-confirm"
                       name="password-confirm"
                       class="form-input <#if messagesPerField.existsError('password-confirm')>input-error</#if>"
                       autocomplete="new-password"
                       placeholder="Confirm new password"
                />
                <#if messagesPerField.existsError('password-confirm')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}
                    </div>
                </#if>
            </div>

            <div class="form-actions">
                <#if isAppInitiatedAction??>
                    <input class="submit-btn" type="submit" value="${msg("doSubmit")}" />
                    <button class="submit-btn btn-secondary" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
                <#else>
                    <input class="submit-btn" type="submit" value="${msg("doSubmit")}" />
                </#if>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
