package xyz.zyven.opti;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Verifies license keys (Ed25519 signature). The private key is NOT in the plugin, so keys cannot be forged. */
final class LicenseVerifier {
    private LicenseVerifier() {}

    /** Public key (raw 32 bytes, base64url). Matches the private.key used by tools/keygen.py. */
    static final String PUBLIC_KEY = "Hb0XtMSyDkevNXSvsMXsreTTLagPiDf4DB-SfQ_bZbo";

    record Result(boolean ok, String holder, long expiry, String error) {}

    private static Result bad(String why) { return new Result(false, "", 0, why); }

    static Result verify(String key) {
        if (key == null || key.isBlank()) return bad("No license key set");
        try {
            String k = key.trim();
            if (k.startsWith("ZYVEN-")) k = k.substring(6);
            String[] parts = k.split("\\.");
            if (parts.length != 2) return bad("Invalid key format");

            Base64.Decoder d = Base64.getUrlDecoder();
            byte[] payload = d.decode(parts[0]);
            byte[] sig = d.decode(parts[1]);

            byte[] raw = d.decode(PUBLIC_KEY);
            byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
            byte[] der = new byte[prefix.length + raw.length];
            System.arraycopy(prefix, 0, der, 0, prefix.length);
            System.arraycopy(raw, 0, der, prefix.length, raw.length);
            PublicKey pub = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));

            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(pub);
            s.update(payload);
            if (!s.verify(sig)) return bad("Invalid license key");

            String[] f = new String(payload, StandardCharsets.UTF_8).split("\\|");
            if (f.length != 2) return bad("Invalid license key");
            return new Result(true, f[0], Long.parseLong(f[1]), "");
        } catch (Exception e) {
            return bad("Invalid license key");
        }
    }
}
