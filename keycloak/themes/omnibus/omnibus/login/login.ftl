<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=false; section>

    <#if section = "header">
        <div class="brand-header">
            <div class="brand-icon-wrapper">🚌</div>
            <div class="brand-title">OmniBus Transport</div>
            <div class="brand-subtitle">Zero-Trust Enterprise Authentication</div>
        </div>
    <#elseif section = "form">
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <div id="captcha-error-banner" class="alert-banner alert-error" style="display: none;"></div>

            <form id="kc-form-login" onsubmit="return true;" action="${url.loginAction}" method="post">
                <div class="form-group">
                    <label for="username" class="form-label">Username</label>
                    <input tabindex="1" id="username" class="form-input" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="off" placeholder="Enter username (e.g. admin or john_doe)" required />
                </div>

                <div class="form-group">
                    <label for="password" class="form-label">Password</label>
                    <input tabindex="2" id="password" class="form-input" name="password" type="password" autocomplete="off" placeholder="••••••••" required />
                </div>

                <!-- Native Visual CAPTCHA -->
                <div class="captcha-box">
                    <div class="captcha-row">
                        <div id="captcha-image-container" class="captcha-svg-container"></div>
                        <button type="button" class="btn-refresh-captcha" onclick="refreshCaptcha()" title="Refresh CAPTCHA">🔄</button>
                    </div>
                    <input tabindex="3" id="captchaAnswer" class="form-input" type="text" autocomplete="off" placeholder="Type the 5 characters above" maxlength="5" required />
                </div>

                <button tabindex="4" class="btn-primary" name="login" id="kc-login" type="submit">
                    Sign In to OmniBus ➔
                </button>
            </form>

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
        </div>
    </#if>
</@layout.registrationLayout>
