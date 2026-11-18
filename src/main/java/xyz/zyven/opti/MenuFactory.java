package xyz.zyven.opti;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

/** Picks the dialog UI on 1.21.6+ and the chest UI on 1.21 - 1.21.5. */
final class MenuFactory {
    private MenuFactory() {}

    /** Shown in /opti status so you can see why a mode was chosen. */
    static String mode = "CHEST";
    static String reason = "";

    static boolean dialogsAvailable() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static MenuUI create(ZyvenOpti p) {
        String cfg = p.getConfig().getString("menu", "auto").toLowerCase();
        if (cfg.equals("chest")) {
            reason = "config menu: chest";
        } else if (!dialogsAvailable()) {
            reason = "Dialog API not found (server is not Paper 1.21.6+)";
        } else {
            try {
                MenuUI ui = (MenuUI) Class.forName("xyz.zyven.opti.DialogMenus")
                        .getConstructor(ZyvenOpti.class).newInstance(p);
                mode = "DIALOG";
                reason = "ok";
                p.getLogger().info("Menu mode: DIALOG");
                return ui;
            } catch (Throwable t) {
                Throwable root = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                reason = "Dialog failed: " + root;
                p.getLogger().log(Level.WARNING, "Dialog menu failed to load, using chest menu", root);
            }
        }
        mode = "CHEST";
        p.getLogger().info("Menu mode: CHEST (" + reason + ")");
        return new Menus(p);
    }
}
