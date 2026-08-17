<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <div class="brand-header">
            <div class="brand-icon-wrapper">🛡️</div>
            <div class="brand-title">Two-Factor Authentication</div>
            <div class="brand-subtitle">Enter the 6-digit security code from your Authenticator App</div>
        </div>
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <form id="kc-otp-login-form" action="${url.loginAction}" method="post">
                <#if otpLoginHelper.userOtpCredentials?size gt 1>
                    <div class="form-group">
                        <label for="selectedCredentialId" class="form-label">Select Authenticator Device</label>
                        <select id="selectedCredentialId" name="selectedCredentialId" class="form-input">
                            <#list otpLoginHelper.userOtpCredentials as credential>
                                <option value="${credential.id}">${credential.userLabel!credential.id}</option>
                            </#list>
                        </select>
                    </div>
                </#if>

                <div class="form-group">
                    <label for="otp" class="form-label" style="text-align: center; display: block;">Security Code</label>
                    <input tabindex="1" id="otp" class="form-input otp-code-input" name="otp" type="text" autofocus autocomplete="one-time-code" placeholder="••••••" maxlength="8" required />
                </div>

                <button tabindex="2" class="btn-primary" name="login" id="kc-login" type="submit">
                    Verify & Authenticate ➔
                </button>
            </form>

            <div class="login-footer">
                🔒 Protected by RFC 6238 Time-based One-Time Password (TOTP) Security
            </div>
          </div>
        </div>
    </#if>
</@layout.registrationLayout>
