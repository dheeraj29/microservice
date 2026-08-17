<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "form">
        <div style="text-align: center; margin: 10px 0 20px;">
            <div style="font-size: 36px; margin-bottom: 12px;">ℹ️</div>
            <h2 style="font-size: 18px; font-weight: 700; margin-bottom: 12px;">${message.summary}</h2>
            <#if pageRedirectUri??>
                <a href="${pageRedirectUri}" class="btn-primary" style="text-decoration: none;">
                    Continue ➔
                </a>
            <#elseif actionUri??>
                <a href="${actionUri}" class="btn-primary" style="text-decoration: none;">
                    Continue ➔
                </a>
            <#elseif client.baseUrl??>
                <a href="${client.baseUrl}" class="btn-primary" style="text-decoration: none;">
                    Back to Application ➔
                </a>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
