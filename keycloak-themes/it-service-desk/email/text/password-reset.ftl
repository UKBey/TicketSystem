<#assign name = (user.firstName)!'' />
<#if !name?has_content><#assign name = (user.email)!'' /></#if>
${msg('itPwResetGreeting', name)}

${msg('itPwResetIntro')}
${msg('itPwResetValidity', linkExpirationFormatter(linkExpiration))}

${link}

${msg('itPwResetIgnore')}

${msg('itPwResetFooter')}
