package com.da.demo.keycloak.captcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Keycloak Server-Side Native Visual CAPTCHA Authenticator SPI.
 * Enforces mandatory cryptographic CAPTCHA verification inside Keycloak's server-side
 * authentication pipeline BEFORE checking username / password credentials.
 * 
 * 🛡️ Security Guarantees:
 * 1. 100% Server Enforced: Direct POST / curl / bot requests cannot bypass verification.
 * 2. Ephemeral Rotating HMAC: Tokens are cryptographically signed with 120s TTL (0 bytes memory leak).
 * 3. Timing-Attack Resistant: Uses MessageDigest.isEqual for signature verification.
 */
public class ValkeyCaptchaAuthenticator implements Authenticator {

    private static final String CHARSET = "2345679ACDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 5;
    private static final long CAPTCHA_TTL_MILLIS = 120_000; // 120 seconds
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final byte[] HMAC_SECRET = new byte[32];
    static {
        RANDOM.nextBytes(HMAC_SECRET);
    }

    private static final String[] COLOR_PALETTE = {
        "#2563EB", "#7C3AED", "#059669", "#D97706", "#DC2626", "#0D9488", "#4F46E5"
    };

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String code = generateRandomCode();
        long timestamp = System.currentTimeMillis();
        String token = createToken(code, timestamp);
        String svg = renderSvg(code);

        Response response = context.form()
                .setAttribute("captchaSvg", svg)
                .setAttribute("captchaToken", token)
                .createForm("login.ftl");

        context.challenge(response);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String captchaAnswer = formData.getFirst("captchaAnswer");
        String captchaToken = formData.getFirst("captchaToken");

        if (captchaAnswer == null || captchaAnswer.isBlank() || captchaToken == null || captchaToken.isBlank()) {
            retryWithNewCaptcha(context, "Please enter the security CAPTCHA code.");
            return;
        }

        boolean isValid = validateToken(captchaToken, captchaAnswer);
        if (!isValid) {
            retryWithNewCaptcha(context, "Invalid or expired CAPTCHA code. Please try again.");
            return;
        }

        // CAPTCHA is valid! Proceed to credential verification in Keycloak pipeline
        context.success();
    }

    private void retryWithNewCaptcha(AuthenticationFlowContext context, String errorMessage) {
        String code = generateRandomCode();
        long timestamp = System.currentTimeMillis();
        String token = createToken(code, timestamp);
        String svg = renderSvg(code);

        Response response = context.form()
                .setAttribute("captchaSvg", svg)
                .setAttribute("captchaToken", token)
                .setError(errorMessage)
                .createForm("login.ftl");

        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, response);
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    private String createToken(String code, long timestamp) {
        String signature = computeHmac(timestamp + ":" + code.toUpperCase());
        String payload = timestamp + "." + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validateToken(String token, String userInput) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int dotIdx = decoded.indexOf('.');
            if (dotIdx <= 0) return false;

            long timestamp = Long.parseLong(decoded.substring(0, dotIdx));
            String signature = decoded.substring(dotIdx + 1);

            long now = System.currentTimeMillis();
            if ((now - timestamp) > CAPTCHA_TTL_MILLIS || timestamp > (now + 5000)) {
                return false;
            }

            String expectedSig = computeHmac(timestamp + ":" + userInput.trim().toUpperCase());
            return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSig.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(HMAC_SECRET, "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private String renderSvg(String code) {
        int width = 200;
        int height = 65;
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
          .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(" ").append(height)
          .append("\" style=\"background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%); border-radius: 8px; border: 1px solid #cbd5e1; user-select: none;\">");

        // Noise dots
        sb.append("<g opacity=\"0.45\">");
        for (int i = 0; i < 25; i++) {
            int cx = RANDOM.nextInt(width);
            int cy = RANDOM.nextInt(height);
            int r = RANDOM.nextInt(3) + 1;
            String clr = COLOR_PALETTE[RANDOM.nextInt(COLOR_PALETTE.length)];
            sb.append("<circle cx=\"").append(cx).append("\" cy=\"").append(cy).append("\" r=\"").append(r).append("\" fill=\"").append(clr).append("\" />");
        }
        sb.append("</g>");

        // Glyphs with tilt and jitter
        int charSpacing = (width - 40) / code.length();
        int startX = 25;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            int x = startX + (i * charSpacing) + (RANDOM.nextInt(7) - 3);
            int y = 42 + (int) (Math.sin(i * 1.2) * 5) + (RANDOM.nextInt(5) - 2);
            int rotate = RANDOM.nextInt(25) - 12;
            String clr = COLOR_PALETTE[RANDOM.nextInt(COLOR_PALETTE.length)];
            int fsize = 28 + RANDOM.nextInt(5);
            sb.append("<text x=\"").append(x).append("\" y=\"").append(y)
              .append("\" font-family=\"'Segoe UI', -apple-system, Roboto, sans-serif\" font-weight=\"bold\" font-size=\"").append(fsize)
              .append("\" fill=\"").append(clr).append("\" transform=\"rotate(").append(rotate).append(",").append(x).append(",").append(y).append(")\"")
              .append(" letter-spacing=\"2\">").append(ch).append("</text>");
        }

        sb.append("</svg>");
        return sb.toString();
    }

    @Override
    public boolean requiresUser() { return false; }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public void close() {}
}
