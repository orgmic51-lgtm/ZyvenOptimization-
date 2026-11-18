package xyz.zyven.opti;

/** Picks the dialog UI on 1.21.6+ and the chest UI on 1.21 - 1.21.5. */
final class MenuFactory {
    private MenuFactory() {}

    static boolean dialogsAvailable() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static MenuUI create(ZyvenOpti p) {
        String mode = p.getConfig().getString("menu", "auto").toLowerCase();
        boolean useDialog = !mode.equals("chest") && dialogsAvailable();
        if (useDialog) {
            try {
                MenuUI ui = (MenuUI) Class.forName("xyz.zyven.opti.DialogMenus")
                        .getConstructor(ZyvenOpti.class).newInstance(p);
                p.getLogger().info("Menu mode: DIALOG (1.21.6+)");
                return ui;
            } catch (Throwable t) {
                p.getLogger().warning("Dialog menu failed to load, using chest menu: " + t);
            }
        }
        p.getLogger().info("Menu mode: CHEST (1.21 - 1.21.5)");
        return new Menus(p);
    }
}
