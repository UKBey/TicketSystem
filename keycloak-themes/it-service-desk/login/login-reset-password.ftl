<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username') displayInfo=true; section>
    <#if section = "form">
        <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
            <p class="form-description">
                Enter your username or email address and we will send you instructions to reset your password.
            </p>

            <div class="form-group">
                <label for="username" class="form-label">
                    <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                </label>
                <input type="text"
                       id="username"
                       name="username"
                       class="form-input <#if messagesPerField.existsError('username')>input-error</#if>"
                       autofocus
                       value="${(auth.attemptedUsername!'')}"
                       placeholder="Enter your username or email"
                />
                <#if messagesPerField.existsError('username')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}
                    </div>
                </#if>
            </div>

            <div class="form-actions">
                <input class="submit-btn" type="submit" value="${msg("doSubmit")}" />
            </div>

            <div class="back-to-login">
                <a href="${url.loginUrl}">&larr; Back to Sign In</a>
            </div>
        </form>
    </#if>

    <#if section = "info">
    </#if>
</@layout.registrationLayout>
