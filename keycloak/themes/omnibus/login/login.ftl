<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!((messagesPerField?? && messagesPerField.existsError('username','password'))!false) displayInfo=false; section>
    <#if section = "form">
        <form id="kc-form-login" onsubmit="return true;" action="${(url.loginAction)!''}" method="post">
            <div class="form-group">
                <label for="username" class="form-label">Username</label>
                <input tabindex="1" id="username" class="form-input" name="username" value="${((login.username)!'')}" type="text" autofocus autocomplete="off" placeholder="Enter username (e.g. admin or john_doe)" required />
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
    </#if>
</@layout.registrationLayout>
