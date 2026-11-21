package xyz.zyven.opti;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Reads license_key from plugins/ZyvenOptimization/license.yml. No valid, unexpired key = the plugin does not run. */
public final class License {
    static final String PLACEHOLDER = "put your license key here";

    private final File file;
    private volatile boolean valid;
    private volatile long expiry;          // epoch seconds, 0 = lifetime
    private volatile String holder = "";
    private volatile String error = "No license key set";

    License(ZyvenOpti p) {
        this.file = new File(p.getDataFolder(), "license.yml");
        if (!file.exists()) p.saveResource("license.yml", false);
        load();
    }

    private static boolean expired(long expiry) {
        return expiry > 0 && System.currentTimeMillis() / 1000 > expiry;
    }

    private void apply(LicenseVerifier.Result r) {
        holder = r.holder();
        expiry = r.expiry();
        if (r.ok() && expired(r.expiry())) {
            valid = false;
            error = "License expired";
        } else {
            valid = r.ok();
            error = r.error();
        }
    }

    /** (Re)reads license.yml. */
    public void load() {
        String key = YamlConfiguration.loadConfiguration(file).getString("license_key", "");
        if (key == null || key.isBlank() || key.trim().equalsIgnoreCase(PLACEHOLDER)) {
            apply(new LicenseVerifier.Result(false, "", 0, "No license key set in license.yml"));
            return;
        }
        apply(LicenseVerifier.verify(key));
    }

    /** Cheap, safe to call from every event; also notices expiry while the server is running. */
    public boolean isValid() {
        if (valid && expired(expiry)) {
            valid = false;
            error = "License expired";
        }
        return valid;
    }

    public String timeLeft() {
        if (!isValid()) return error;
        if (expiry == 0) return "LIFETIME";
        long s = Math.max(0, expiry - System.currentTimeMillis() / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        return (d > 0 ? d + "d " : "") + h + "h " + m + "m left";
    }

    public String status() {
        if (!isValid()) return "NOT LICENSED (" + error + ")";
        return holder + " - " + timeLeft();
    }
}
