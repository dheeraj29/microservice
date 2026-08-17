<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <div class="brand-header">
            <div class="brand-icon-wrapper">🛡️</div>
            <div class="brand-title">Two-Factor Authentication</div>
            <div class="brand-subtitle">Set up an Authenticator App to secure your account</div>
        </div>
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <div class="mfa-step-card">
                <div class="mfa-step-header">
                    <span class="step-badge">Step 1</span>
                    <span class="step-title">Scan QR Code with your Authenticator</span>
                </div>
                <div class="qr-code-wrapper">
                    <#if totp.totpSecretQrCode??>
                        <img src="data:image/png;base64,${totp.totpSecretQrCode}" alt="TOTP 2FA QR Code" class="qr-code-img" />
                    <#else>
                        <img src="${totp.qrUrl}" alt="TOTP 2FA QR Code" class="qr-code-img" />
                    </#if>
                </div>
                <div class="manual-key-box">
                    <span class="manual-key-label">Can't scan? Enter key manually:</span>
                    <code class="manual-key-code">${totp.totpSecretEncoded}</code>
                </div>
            </div>

            <form id="kc-totp-settings-form" action="${url.loginAction}" method="post">
                <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}" />

                <div class="form-group" style="margin-top: 16px;">
                    <label for="totp" class="form-label">
                        <span class="step-badge-sm">Step 2</span> Enter 6-Digit Code from App
                    </label>
                    <input tabindex="1" id="totp" class="form-input otp-code-input" name="totp" type="text" autofocus autocomplete="off" placeholder="000000" maxlength="8" required />
                </div>

                <div class="form-group">
                    <label for="userLabel" class="form-label">Device Name (Optional)</label>
                    <input tabindex="2" id="userLabel" class="form-input" name="userLabel" type="text" autocomplete="off" placeholder="e.g. My Mobile Phone" />
                </div>

                <button tabindex="3" class="btn-primary" name="save" id="saveTOTPBtn" type="submit">
                    Verify & Activate 2FA 🛡️
                </button>

                <#if isAppInitiatedAction??>
                    <div style="margin-top: 14px; text-align: center;">
                        <button type="submit" class="btn-secondary" id="cancelTOTPBtn" name="cancel-aio" value="true">Cancel</button>
                    </div>
                </#if>
            </form>

            <div class="login-footer">
                Compatible with Google Authenticator, Microsoft Authenticator, Authy, and 1Password
            </div>
          </div>
        </div>
    </#if>
</@layout.registrationLayout>
