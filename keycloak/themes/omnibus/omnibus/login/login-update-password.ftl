<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <div class="brand-header">
            <div class="brand-icon-wrapper">🔐</div>
            <div class="brand-title">Set New Password</div>
            <div class="brand-subtitle">Choose a strong password for your OmniBus account</div>
        </div>
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">
                <input type="text" id="username" name="username" value="${username}" autocomplete="username" readonly="readonly" style="display:none;"/>
                <input type="password" id="password" name="password" autocomplete="current-password" style="display:none;"/>

                <div class="form-group">
                    <label for="password-new" class="form-label">New Password</label>
                    <input tabindex="1" id="password-new" class="form-input" name="password-new" type="password" autofocus autocomplete="new-password" placeholder="••••••••" required />
                </div>

                <div class="form-group">
                    <label for="password-confirm" class="form-label">Confirm New Password</label>
                    <input tabindex="2" id="password-confirm" class="form-input" name="password-confirm" type="password" autocomplete="new-password" placeholder="••••••••" required />
                </div>

                <div class="security-badges-container" style="margin-bottom: 18px;">
                    <div class="security-badge">
                        <span class="dot"></span>
                        <span>Minimum 8 characters with numbers & symbols</span>
                    </div>
                </div>

                <button tabindex="3" class="btn-primary" name="submit" id="kc-submit" type="submit">
                    Update Password & Continue ➔
                </button>
            </form>

            <div class="login-footer">
                🔒 Protected by OmniBus Zero-Trust Identity Security
            </div>
          </div>
        </div>
    </#if>
</@layout.registrationLayout>
