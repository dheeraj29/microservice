<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>OmniBus — Secure Authentication</title>
    <link rel="icon" href="${(url.resourcesPath)!''}/img/favicon.ico" />
    <#if properties?? && properties.styles?? && properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${(url.resourcesPath)!''}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties?? && properties.scripts?? && properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${(url.resourcesPath)!''}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
</head>

<body>
    <div class="login-container">
        <div class="brand-header">
            <div class="brand-logo">🚌</div>
            <h1 class="brand-title">OmniBus Transport</h1>
            <p class="brand-subtitle">Zero-Trust Enterprise Authentication</p>
        </div>

        <#if displayMessage && message?? && message.summary?? && ((message.type)!'') != 'warning'>
            <div class="alert-banner <#if ((message.type)!'') == 'error'>alert-error<#else>alert-success</#if>">
                <span>${(kcSanitize(message.summary))?no_esc}</span>
            </div>
        </#if>

        <div id="captcha-error-banner" class="alert-banner alert-error" style="display: none;"></div>

        <#nested "form">

        <div class="demo-credentials-box">
            <div class="demo-title">Quick-Fill Demo Credentials</div>
            <div class="demo-buttons">
                <button type="button" class="btn-demo" onclick="quickFill('admin', 'admin123')">🧑‍💼 Admin</button>
                <button type="button" class="btn-demo" onclick="quickFill('john_doe', 'user123')">🧑‍🦱 Customer</button>
            </div>
        </div>

        <div class="login-footer">
            🔒 Protected by Keycloak 26+ IAM & Zero-Trust BFF Security
        </div>
    </div>
</body>
</html>
</#macro>
