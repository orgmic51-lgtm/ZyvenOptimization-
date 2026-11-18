package xyz.zyven.opti;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Consumer;

/** All GUI screens: main, plugin manager, performance, cleanup, redstone, lag machines, config, advanced, help. */
public final class Menus implements Listener {
    static final class Menu implements InventoryHolder {
        final Inventory inv;
        final Map<Integer, Consumer<InventoryClickEvent>> acts = new HashMap<>();
        Menu(String title, int size) { inv = Bukkit.createInventory(this, size, title); }
        @Override public Inventory getInventory() { return inv; }
        void set(int slot, ItemStack it, Consumer<InventoryClickEvent> a) { inv.setItem(slot, it); if (a != null) acts.put(slot, a); }
    }

    private final ZyvenOpti p;
    Menus(ZyvenOpti p) { this.p = p; }

    // ---------- helpers ----------
    static ItemStack item(Material m, String name, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mm = i.getItemMeta();
        mm.setDisplayName(name);
        mm.setLore(Arrays.asList(lore));
        i.setItemMeta(mm);
        return i;
    }

    private Menu base(String title, int size) {
        Menu m = new Menu(ChatColor.DARK_GREEN + "Zyven Opti " + ChatColor.GRAY + "» " + ChatColor.GREEN + title, size);
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) m.inv.setItem(i, pane);
        return m;
    }

    private void back(Menu m, int slot, Player pl) {
        m.set(slot, item(Material.ARROW, ChatColor.YELLOW + "Back"), e -> main(pl));
    }

    private static String onOff(boolean b) { return b ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"; }

    private void toggle(Menu m, int slot, String path, String label, Runnable reopen) {
        boolean v = p.getConfig().getBoolean(path);
        m.set(slot, item(v ? Material.LIME_DYE : Material.GRAY_DYE, ChatColor.WHITE + label + ": " + onOff(v),
                ChatColor.GRAY + "Click to toggle", ChatColor.DARK_GRAY + path), e -> {
            p.getConfig().set(path, !p.getConfig().getBoolean(path));
            p.saveConfig(); p.applyDistances(); reopen.run();
        });
    }

    private void number(Menu m, int slot, String path, String label, double step, double min, Runnable reopen) {
        Object v = p.getConfig().get(path);
        m.set(slot, item(Material.PAPER, ChatColor.WHITE + label + ": " + ChatColor.AQUA + v,
                ChatColor.GRAY + "Left click: +" + (step % 1 == 0 ? String.valueOf((int) step) : String.valueOf(step)),
                ChatColor.GRAY + "Right click: -" + (step % 1 == 0 ? String.valueOf((int) step) : String.valueOf(step)),
                ChatColor.DARK_GRAY + path), e -> {
            double d = e.isRightClick() ? -step : step;
            if (v instanceof Integer i) p.getConfig().set(path, (int) Math.max(min, i + d));
            else if (v instanceof Double db) p.getConfig().set(path, Math.max(min, Math.round((db + d) * 10) / 10.0));
            p.saveConfig(); p.applyDistances(); reopen.run();
        });
    }

    private void btn(Menu m, int slot, Material mat, String name, Consumer<InventoryClickEvent> a, String... lore) {
        m.set(slot, item(mat, name, lore), a);
    }

    private static String ago(long t) {
        long s = (System.currentTimeMillis() - t) / 1000;
        return s < 60 ? s + "s ago" : s < 3600 ? (s / 60) + "m ago" : (s / 3600) + "h ago";
    }

    // ---------- screens ----------
    public void main(Player pl) {
        Menu m = base("Optimization", 27);
        double tps = p.tps();
        String tc = tps >= 18 ? ChatColor.GREEN.toString() : tps >= 15 ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576, max = rt.maxMemory() / 1048576;
        List<String> lore = new ArrayList<>(List.of(
                ChatColor.GRAY + "TPS: " + tc + String.format("%.1f", tps) + ChatColor.DARK_GRAY + " (target 20.0)",
                ChatColor.GRAY + "MSPT: " + ChatColor.WHITE + String.format("%.1f", p.mspt()),
                ChatColor.GRAY + "RAM: " + ChatColor.WHITE + used + "/" + max + " MB",
                ChatColor.GRAY + "Players: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size(),
                ChatColor.GRAY + "Frozen chunks: " + ChatColor.WHITE + p.guard.frozenCount()));
        if (!p.guard.history.isEmpty()) {
            Guard.LagEntry le = p.guard.history.get(p.guard.history.size() - 1);
            lore.add(ChatColor.RED + "PROBLEM: " + le.type() + " <" + le.loc().getBlockX() + ", " + le.loc().getBlockY() + ", " + le.loc().getBlockZ() + ">");
        } else lore.add(ChatColor.GREEN + "No problems detected");
        m.set(4, item(tps >= 18 ? Material.EMERALD : Material.REDSTONE_BLOCK, ChatColor.GREEN + "Server Status", lore.toArray(new String[0])), e -> main(pl));

        btn(m, 10, Material.BOOKSHELF, ChatColor.GREEN + "Plugin Manager", e -> plugins(pl), ChatColor.GRAY + "View and toggle plugins");
        btn(m, 11, Material.CLOCK, ChatColor.GREEN + "Performance Settings", e -> perf(pl), ChatColor.GRAY + "View / simulation distance");
        btn(m, 12, Material.LAVA_BUCKET, ChatColor.GREEN + "Cleanup Tools", e -> cleanup(pl), ChatColor.GRAY + "Clear items, mobs, projectiles");
        btn(m, 13, Material.REDSTONE, ChatColor.GREEN + "Redstone & Limiter", e -> redstone(pl), ChatColor.GRAY + "Limit redstone updates");
        btn(m, 14, Material.OBSERVER, ChatColor.GREEN + "Lag Machines", e -> lag(pl), ChatColor.GRAY + "Detected lag machines");
        btn(m, 15, Material.COMPARATOR, ChatColor.GREEN + "Config", e -> config(pl), ChatColor.GRAY + "Edit every setting");
        btn(m, 16, Material.COMMAND_BLOCK, ChatColor.GREEN + "Advanced Options", e -> adv(pl), ChatColor.GRAY + "Feature switches");
        btn(m, 18, Material.WRITABLE_BOOK, ChatColor.GREEN + "Help", e -> help(pl));
        btn(m, 22, Material.BARRIER, ChatColor.RED + "Close", e -> pl.closeInventory());
        pl.openInventory(m.inv);
    }

    private void plugins(Player pl) {
        Menu m = base("Plugin Manager", 54);
        Plugin[] all = Bukkit.getPluginManager().getPlugins();
        Arrays.sort(all, Comparator.comparing(x -> x.getName().toLowerCase()));
        for (int i = 0; i < Math.min(45, all.length); i++) {
            Plugin pg = all[i];
            boolean on = pg.isEnabled();
            m.set(i, item(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    (on ? ChatColor.GREEN + "[ON] " : ChatColor.RED + "[OFF] ") + ChatColor.WHITE + pg.getName(),
                    ChatColor.GRAY + "Version: " + pg.getPluginMeta().getVersion(),
                    ChatColor.YELLOW + "Click to " + (on ? "disable" : "enable")), e -> {
                if (pg == p) return;
                if (pg.isEnabled()) Bukkit.getPluginManager().disablePlugin(pg); else Bukkit.getPluginManager().enablePlugin(pg);
                plugins(pl);
            });
        }
        m.set(49, item(Material.PAPER, ChatColor.GRAY + "" + all.length + " plugins"), null);
        back(m, 53, pl);
        pl.openInventory(m.inv);
    }

    private void perf(Player pl) {
        Menu m = base("Performance Settings", 27);
        var c = p.getConfig();
        m.set(4, item(Material.CLOCK, ChatColor.GREEN + "Performance",
                ChatColor.GRAY + "View distance: " + ChatColor.WHITE + c.getInt("performance.view-distance") + ChatColor.DARK_GRAY + " (reduced by " + p.reduce + ")",
                ChatColor.GRAY + "Simulation distance: " + ChatColor.WHITE + c.getInt("performance.simulation-distance"),
                ChatColor.GRAY + "Adaptive: " + onOff(c.getBoolean("performance.adaptive-view"))), null);
        Runnable r = () -> perf(pl);
        btn(m, 10, Material.SPYGLASS, ChatColor.WHITE + "View Distance -1", e -> step("performance.view-distance", -1, r));
        btn(m, 11, Material.SPYGLASS, ChatColor.WHITE + "View Distance +1", e -> step("performance.view-distance", 1, r));
        btn(m, 12, Material.COMPASS, ChatColor.WHITE + "Simulation -1", e -> step("performance.simulation-distance", -1, r));
        btn(m, 13, Material.COMPASS, ChatColor.WHITE + "Simulation +1", e -> step("performance.simulation-distance", 1, r));
        toggle(m, 14, "performance.adaptive-view", "Adaptive", r);
        toggle(m, 15, "cleanup.auto-clear", "Auto Clear", r);
        back(m, 22, pl);
        pl.openInventory(m.inv);
    }

    private void step(String path, int d, Runnable r) {
        p.getConfig().set(path, Math.max(2, Math.min(32, p.getConfig().getInt(path) + d)));
        p.saveConfig(); p.applyDistances(); r.run();
    }

    private void cleanup(Player pl) {
        Menu m = base("Cleanup Tools", 27);
        Cleaner.Result c = p.cleaner.count();
        Cleaner.Result l = p.cleaner.last;
        m.set(4, item(Material.LAVA_BUCKET, ChatColor.GREEN + "Cleanup Tools",
                ChatColor.GRAY + "Items on ground: " + ChatColor.WHITE + c.items(),
                ChatColor.GRAY + "Removable mobs: " + ChatColor.WHITE + c.mobs(),
                ChatColor.GRAY + "Arrows: " + ChatColor.WHITE + c.arrows() + ChatColor.GRAY + " | XP orbs: " + ChatColor.WHITE + c.xp(),
                ChatColor.GRAY + "Last cleanup: " + l.items() + " items, " + l.mobs() + " mobs, " + l.arrows() + " arrows, " + l.xp() + " xp",
                ChatColor.GRAY + "Auto clear: " + onOff(p.getConfig().getBoolean("cleanup.auto-clear")) + ChatColor.GRAY + " every " + p.getConfig().getInt("cleanup.minutes") + " min",
                ChatColor.DARK_GRAY + "Named, tamed, leashed mobs and villagers are never removed."), null);
        btn(m, 10, Material.HOPPER, ChatColor.WHITE + "Clear Items", e -> { p.cleaner.clear(true, false, false, false); cleanup(pl); });
        btn(m, 11, Material.ZOMBIE_HEAD, ChatColor.WHITE + "Clear Mobs", e -> { p.cleaner.clear(false, true, false, false); cleanup(pl); });
        btn(m, 12, Material.ARROW, ChatColor.WHITE + "Clear Projectiles", e -> { p.cleaner.clear(false, false, true, false); cleanup(pl); });
        btn(m, 13, Material.EXPERIENCE_BOTTLE, ChatColor.WHITE + "Clear XP Orbs", e -> { p.cleaner.clear(false, false, false, true); cleanup(pl); });
        btn(m, 14, Material.TNT, ChatColor.RED + "Clear Everything", e -> { p.cleaner.clear(true, true, true, true); cleanup(pl); });
        btn(m, 15, Material.SUNFLOWER, ChatColor.WHITE + "Refresh", e -> cleanup(pl));
        back(m, 22, pl);
        pl.openInventory(m.inv);
    }

    private void redstone(Player pl) {
        Menu m = base("Redstone & Limiter", 27);
        var c = p.getConfig();
        m.set(4, item(Material.REDSTONE, ChatColor.GREEN + "Redstone & Limiter",
                ChatColor.GRAY + "Redstone: " + (c.getBoolean("redstone.enabled") ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"),
                ChatColor.GRAY + "Limiter: " + onOff(c.getBoolean("redstone.limiter")) + ChatColor.GRAY + " max " + c.getInt("limits.redstone-limit") + " updates/sec per chunk",
                ChatColor.GRAY + "Held back last second: " + ChatColor.WHITE + p.guard.heldLastSecond + ChatColor.GRAY + " | total: " + ChatColor.WHITE + p.guard.heldTotal,
                ChatColor.GRAY + "Frozen chunks: " + ChatColor.WHITE + p.guard.frozenCount(),
                ChatColor.DARK_GRAY + "The limiter holds extra updates of a chunk until the next second."), null);
        Runnable r = () -> redstone(pl);
        toggle(m, 10, "redstone.enabled", "Redstone", r);
        toggle(m, 11, "redstone.limiter", "Limiter", r);
        btn(m, 12, Material.RED_DYE, ChatColor.WHITE + "Limit -100", e -> { adj("limits.redstone-limit", -100, 100); r.run(); });
        btn(m, 13, Material.LIME_DYE, ChatColor.WHITE + "Limit +100", e -> { adj("limits.redstone-limit", 100, 100); r.run(); });
        btn(m, 14, Material.ICE, ChatColor.AQUA + "Freeze My Chunk", e -> { p.guard.freeze(pl.getLocation(), c.getInt("lag-machines.freeze-seconds")); r.run(); });
        btn(m, 15, Material.CAMPFIRE, ChatColor.GOLD + "Unfreeze All", e -> { p.guard.unfreezeAll(); r.run(); });
        back(m, 22, pl);
        pl.openInventory(m.inv);
    }

    private void adj(String path, int d, int min) {
        p.getConfig().set(path, Math.max(min, p.getConfig().getInt(path) + d));
        p.saveConfig();
    }

    private void lag(Player pl) {
        Menu m = base("Lag Machines", 54);
        List<Guard.LagEntry> h = p.guard.history;
        for (int i = 0; i < Math.min(36, h.size()); i++) {
            Guard.LagEntry le = h.get(h.size() - 1 - i);
            Location l = le.loc();
            m.set(i, item(Material.OBSERVER, ChatColor.RED + "#" + (i + 1) + " " + le.type(),
                    ChatColor.GRAY + "<" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + "> " + l.getWorld().getName(),
                    ChatColor.GRAY + "Detected " + ago(le.time()),
                    ChatColor.GRAY + "Chunk frozen: " + onOff(p.guard.isFrozen(l)),
                    ChatColor.YELLOW + "Click to teleport"), e -> { pl.closeInventory(); pl.teleport(l.clone().add(0.5, 1, 0.5)); });
        }
        if (h.isEmpty()) m.set(22, item(Material.LIME_DYE, ChatColor.GREEN + "No lag machines detected"), null);
        m.set(4, item(Material.NAME_TAG, ChatColor.GREEN + "Detector: " + onOff(p.getConfig().getBoolean("lag-machines.detector")),
                ChatColor.GRAY + "Frozen chunks: " + p.guard.frozenCount()), null);
        btn(m, 45, Material.CAMPFIRE, ChatColor.GOLD + "Unfreeze All", e -> { p.guard.unfreezeAll(); lag(pl); });
        btn(m, 46, Material.BARRIER, ChatColor.RED + "Clear History", e -> { p.guard.history.clear(); lag(pl); });
        btn(m, 47, Material.SUNFLOWER, ChatColor.WHITE + "Refresh", e -> lag(pl));
        back(m, 53, pl);
        pl.openInventory(m.inv);
    }

    private static final String[][] CFG = {
            {"cleanup.auto-clear", "Auto clear", "0"}, {"cleanup.minutes", "Minutes between clears", "1"},
            {"cleanup.clear-items", "Clear items", "0"}, {"cleanup.clear-mobs", "Clear mobs", "0"},
            {"cleanup.clear-arrows", "Clear arrows", "0"}, {"cleanup.clear-xp-orbs", "Clear xp orbs", "0"},
            {"cleanup.emergency", "Emergency cleanup", "0"}, {"cleanup.emergency-tps", "Emergency below TPS", "1"},
            {"limits.chunk-entity-limits", "Chunk entity limits", "0"}, {"limits.max-items-per-chunk", "Max items per chunk", "16"},
            {"limits.max-hostile-per-chunk", "Max hostile mobs per chunk", "10"}, {"limits.max-entities-per-chunk", "Entities per chunk limit", "50"},
            {"lag-machines.detector", "Lag machine detector", "0"}, {"lag-machines.auto-freeze", "Auto-freeze detected chunk", "0"},
            {"lag-machines.freeze-seconds", "Freeze time (seconds)", "30"}, {"limits.redstone-limit", "Redstone limit (updates/sec)", "100"},
            {"limits.piston-limit", "Piston limit (moves/sec)", "20"}, {"limits.hopper-limit", "Hopper limit (transfers/sec)", "50"},
            {"limits.dispenser-limit", "Dispenser limit (shots/sec)", "20"}, {"announcements.announce-lag-machines", "Announce lag machines", "0"},
            {"announcements.admins-only", "Announce to admins only", "0"}, {"announcements.show-coordinates", "Show coordinates", "0"},
            {"performance.adaptive-view", "Adaptive view distance", "0"},
    };

    private void config(Player pl) {
        Menu m = base("Config", 54);
        Runnable r = () -> config(pl);
        for (int i = 0; i < CFG.length; i++) {
            String path = CFG[i][0];
            if (p.getConfig().get(path) instanceof Boolean) toggle(m, i, path, CFG[i][1], r);
            else {
                double step = Double.parseDouble(CFG[i][2]);
                if (p.getConfig().get(path) instanceof Double) step = 1;
                number(m, i, path, CFG[i][1], step, 1, r);
            }
        }
        m.set(48, item(Material.WRITABLE_BOOK, ChatColor.GREEN + "Reload config.yml"), e -> { p.reloadConfig(); config(pl); });
        back(m, 49, pl);
        m.set(50, item(Material.BOOK, ChatColor.GRAY + "Tip", ChatColor.GRAY + "/opti config set <path> <value>"), null);
        pl.openInventory(m.inv);
    }

    private void adv(Player pl) {
        Menu m = base("Advanced Options", 27);
        Runnable r = () -> adv(pl);
        m.set(4, item(Material.COMMAND_BLOCK, ChatColor.GREEN + "Advanced Options", ChatColor.GRAY + "Master switches for every feature"), null);
        toggle(m, 10, "lag-machines.detector", "Lag Detector", r);
        toggle(m, 11, "lag-machines.auto-freeze", "Auto-Freeze", r);
        toggle(m, 12, "cleanup.auto-clear", "Auto Clear", r);
        toggle(m, 13, "limits.chunk-entity-limits", "Chunk Limits", r);
        toggle(m, 14, "performance.adaptive-view", "Adaptive View", r);
        toggle(m, 15, "cleanup.emergency", "Emergency Cleanup", r);
        btn(m, 16, Material.WRITABLE_BOOK, ChatColor.WHITE + "Reload Files", e -> { p.reloadConfig(); p.applyDistances(); adv(pl); });
        back(m, 22, pl);
        pl.openInventory(m.inv);
    }

    private void help(Player pl) {
        Menu m = base("Help", 27);
        m.set(13, item(Material.BOOK, ChatColor.GREEN + "Zyven Opti Commands",
                ChatColor.WHITE + "/opti" + ChatColor.GRAY + " - open menu",
                ChatColor.WHITE + "/opti status" + ChatColor.GRAY + " - TPS / MSPT / RAM",
                ChatColor.WHITE + "/opti clear [items|mobs|projectiles|xp|all]",
                ChatColor.WHITE + "/opti config set <path> <value>",
                ChatColor.WHITE + "/opti reload"), null);
        back(m, 22, pl);
        pl.openInventory(m.inv);
    }

    // ---------- events ----------
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player pl) || !pl.hasPermission(ZyvenOpti.PERM)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Consumer<InventoryClickEvent> a = m.acts.get(e.getSlot());
        if (a != null) a.accept(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
          }
