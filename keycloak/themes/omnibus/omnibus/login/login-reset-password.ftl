<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <div class="brand-header">
            <div class="brand-icon-wrapper">🔑</div>
            <div class="brand-title">Account Recovery</div>
            <div class="brand-subtitle">Reset your OmniBus account credentials</div>
        </div>
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
                <div class="form-group">
                    <label for="username" class="form-label">
                        <#if !realm.loginWithEmailAllowed>Username<#elseif !realm.registrationEmailAsUsername>Username or Email<#else>Email Address</#if>
                    </label>
                    <input tabindex="1" id="username" class="form-input" name="username" value="${(auth.attemptedUsername!'')}" type="text" autofocus autocomplete="off" placeholder="Enter your username or email" required />
                </div>

                <button tabindex="2" class="btn-primary" name="login" id="kc-submit" type="submit">
                    Send Reset Link ✉️
                </button>

                <div class="form-helper-row" style="margin-top: 18px; text-align: center;">
                    <a href="${url.loginUrl}" class="link-secondary">← Back to Sign In</a>
                </div>
            </form>

            <div class="login-footer">
                🔒 Instructions will be sent to your registered email address
            </div>
          </div>
        </div>
    </#if>
</@layout.registrationLayout>
