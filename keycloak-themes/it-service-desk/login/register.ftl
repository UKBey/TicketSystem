<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm') displayInfo=true; section>
    <#if section = "form">
        <form id="kc-register-form" action="${url.registrationAction}" method="post">
            <p class="form-description">${msg("registerDesc")}</p>

            <div class="form-row">
                <div class="form-group form-group-half">
                    <label for="firstName" class="form-label">${msg("firstName")}</label>
                    <input type="text"
                           id="firstName"
                           name="firstName"
                           class="form-input <#if messagesPerField.existsError('firstName')>input-error</#if>"
                           value="${(register.formData.firstName!'')}"
                           placeholder="${msg('firstNamePlaceholder')}"
                    />
                    <#if messagesPerField.existsError('firstName')>
                        <div class="field-error">
                            ${kcSanitize(messagesPerField.getFirstError('firstName'))?no_esc}
                        </div>
                    </#if>
                </div>

                <div class="form-group form-group-half">
                    <label for="lastName" class="form-label">${msg("lastName")}</label>
                    <input type="text"
                           id="lastName"
                           name="lastName"
                           class="form-input <#if messagesPerField.existsError('lastName')>input-error</#if>"
                           value="${(register.formData.lastName!'')}"
                           placeholder="${msg('lastNamePlaceholder')}"
                    />
                    <#if messagesPerField.existsError('lastName')>
                        <div class="field-error">
                            ${kcSanitize(messagesPerField.getFirstError('lastName'))?no_esc}
                        </div>
                    </#if>
                </div>
            </div>

            <div class="form-group">
                <label for="email" class="form-label">${msg("email")}</label>
                <input type="email"
                       id="email"
                       name="email"
                       class="form-input <#if messagesPerField.existsError('email')>input-error</#if>"
                       value="${(register.formData.email!'')}"
                       autocomplete="email"
                       placeholder="${msg('emailPlaceholder')}"
                />
                <#if messagesPerField.existsError('email')>
                    <div class="field-error">
                        ${kcSanitize(messagesPerField.getFirstError('email'))?no_esc}
                    </div>
                </#if>
            </div>

            <#if !realm.registrationEmailAsUsername>
                <div class="form-group">
                    <label for="username" class="form-label">${msg("username")}</label>
                    <input type="text"
                           id="username"
                           name="username"
                           class="form-input <#if messagesPerField.existsError('username')>input-error</#if>"
                           value="${(register.formData.username!'')}"
                           autocomplete="username"
                           placeholder="${msg('chooseUsernamePlaceholder')}"
                    />
                    <#if messagesPerField.existsError('username')>
                        <div class="field-error">
                            ${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}
                        </div>
                    </#if>
                </div>
            </#if>

            <#if passwordRequired??>
                <div class="form-group">
                    <label for="password" class="form-label">${msg("password")}</label>
                    <input type="password"
                           id="password"
                           name="password"
                           class="form-input <#if messagesPerField.existsError('password','password-confirm')>input-error</#if>"
                           autocomplete="new-password"
                           placeholder="${msg('createPasswordPlaceholder')}"
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
                           placeholder="${msg('confirmYourPasswordPlaceholder')}"
                    />
                    <#if messagesPerField.existsError('password-confirm')>
                        <div class="field-error">
                            ${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}
                        </div>
                    </#if>
                </div>
            </#if>

            <#if recaptchaRequired??>
                <div class="form-group">
                    <div class="g-recaptcha" data-size="compact" data-sitekey="${recaptchaSiteKey}"></div>
                </div>
            </#if>

            <div class="form-actions">
                <input class="submit-btn" type="submit" value="${msg("doRegister")}" />
            </div>

            <div class="back-to-login">
                <a href="${url.loginUrl}">${msg("alreadyHaveAccount")}</a>
            </div>
        </form>
    </#if>

    <#if section = "info">
    </#if>
</@layout.registrationLayout>
