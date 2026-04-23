<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "form">
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">

            <div class="form-group">
                <label for="username" class="form-label">
                    <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
                </label>
                <input tabindex="1"
                       id="username"
                       class="form-input <#if messagesPerField.existsError('username','password')>input-error</#if>"
                       name="username"
                       value="${(login.username!'')}"
                       type="text"
                       autofocus
                       autocomplete="username"
                       placeholder="Enter your username or email"
                />
                <#if messagesPerField.existsError('username','password')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                    </div>
                </#if>
            </div>

            <div class="form-group">
                <label for="password" class="form-label">
                    ${msg("password")}
                </label>
                <input tabindex="2"
                       id="password"
                       class="form-input <#if messagesPerField.existsError('username','password')>input-error</#if>"
                       name="password"
                       type="password"
                       autocomplete="current-password"
                       placeholder="Enter your password"
                />
            </div>

            <div class="form-options">
                <#if realm.rememberMe && !usernameEditDisabled??>
                    <label class="remember-me">
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox"
                               <#if login.rememberMe??>checked</#if>
                        />
                        <span>${msg("rememberMe")}</span>
                    </label>
                </#if>
                <#if realm.resetPasswordAllowed>
                    <a tabindex="5" class="forgot-password" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                </#if>
            </div>

            <input tabindex="4"
                   class="submit-btn"
                   name="login"
                   id="kc-login"
                   type="submit"
                   value="${msg("doLogIn")}"
            />
        </form>
    </#if>

    <#if section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <span class="register-link">
                ${msg("noAccount")} <a tabindex="6" href="${url.registrationUrl}">${msg("doRegister")}</a>
            </span>
        </#if>
    </#if>
</@layout.registrationLayout>
