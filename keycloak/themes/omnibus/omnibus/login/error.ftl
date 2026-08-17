<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <div class="brand-header">
            <div class="brand-icon-wrapper" style="background: rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.35);">⚠️</div>
            <div class="brand-title">Authentication Notice</div>
            <div class="brand-subtitle">An error occurred during authentication</div>
        </div>
    <#elseif section = "form">
        <div id="kc-error-message">
            <div class="alert-banner alert-error" style="margin-bottom: 24px;">
                <span>${message.summary}</span>
            </div>

            <#if client?? && client.baseUrl?has_content>
                <a href="${client.baseUrl}" class="btn-primary" style="display: block; text-align: center; text-decoration: none; box-sizing: border-box;">
                    ← Back to Application
                </a>
            <#else>
                <a href="${url.loginUrl}" class="btn-primary" style="display: block; text-align: center; text-decoration: none; box-sizing: border-box;">
                    ← Return to Sign In
                </a>
            </#if>

            <div class="login-footer">
                OmniBus Zero-Trust Identity Protection System
            </div>
        </div>
    </#if>
</@layout.registrationLayout>
