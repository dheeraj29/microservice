<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false displayInfo=false; section>
    <#if section = "form">
        <div style="text-align: center; margin: 10px 0 20px;">
            <div style="font-size: 40px; margin-bottom: 12px;">👋</div>
            <h2 style="font-size: 20px; font-weight: 700; margin-bottom: 8px;">You Have Signed Out</h2>
            <p style="font-size: 13px; color: var(--text-secondary); margin-bottom: 24px;">
                Your session and authentication tokens have been securely terminated.
            </p>
            <a href="${url.loginUrl}" class="btn-primary" style="text-decoration: none;">
                Sign In Again ➔
            </a>
        </div>
    </#if>
</@layout.registrationLayout>
