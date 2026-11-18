package xyz.zyven.opti;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ZyvenOpti extends JavaPlugin {
    public static final String PREFIX = ChatColor.GREEN + "[Zyven Opti] " + ChatColor.GRAY;
    public static final String PERM = "zyven.opti.admin";
    public static final String ALERT_PERM = "zyven.opti.alerts";

    Cleaner cleaner;
    Guard guard;
    MenuUI menus;
    int reduce = 0, seconds = 0, tick = 0;
    long lastEmergency = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration c = getConfig();
        if (c.getInt("performance.view-distance") <= 0) c.set("performance.view-distance", Bukkit.getViewDistance());
        if (c.getInt("performance.simulation-distance") <= 0) c.set("performance.simulation-distance", Bukkit.getSimulationDistance());
        saveConfig();

        cleaner = new Cleaner(this);
        guard = new Guard(this);
        menus = MenuFactory.create(this);
        getServer().getPluginManager().registerEvents(guard, this);
        if (menus instanceof Listener l) getServer().getPluginManager().registerEvents(l, this);

        PluginCommand cmd = getCommand("opti");
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }

        getServer().getScheduler().runTaskTimer(this, this::secondTick, 20L, 20L);
        applyDistances();
        getLogger().info("Zyven Optimization enabled.");
    }

    @Override
    public void onDisable() {
        reduce = 0;
        applyDistances();
    }

    // ---------- helpers ----------
    public double tps() { return Math.min(20.0, Bukkit.getTPS()[0]); }
    public double mspt() { return Bukkit.getAverageTickTime(); }

    private boolean gets(Player pl) {
        boolean restrict = getConfig().getBoolean("announcements.admins-only", true);
        return !restrict || pl.hasPermission(ALERT_PERM);
    }

    /** Chat message to everyone who has zyven.opti.alerts. */
    public void announce(String msg) {
        for (Player pl : Bukkit.getOnlinePlayers())
            if (gets(pl)) pl.sendMessage(PREFIX + msg);
        getLogger().info(ChatColor.stripColor(msg));
    }

    /** Chat + action bar + sound for important problems (lag machines, emergency). */
    public void alert(String msg) {
        announce(msg);
        if (!getConfig().getBoolean("announcements.sound", true)) return;
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!gets(pl)) continue;
            pl.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.stripColor(msg),
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
        }
    }

    public void applyDistances() {
        int v = Math.max(2, getConfig().getInt("performance.view-distance") - reduce);
        int s = Math.max(2, getConfig().getInt("performance.simulation-distance") - reduce);
        for (World w : Bukkit.getWorlds()) {
            if (w.getViewDistance() != v) w.setViewDistance(Math.min(32, v));
            if (w.getSimulationDistance() != s) w.setSimulationDistance(Math.min(32, s));
        }
    }

    // ---------- main loop (1/sec) ----------
    private void secondTick() {
        FileConfiguration c = getConfig();
        guard.tick();
        seconds++;
        tick++;

        if (c.getBoolean("cleanup.auto-clear") && seconds >= Math.max(1, c.getInt("cleanup.minutes")) * 60) {
            seconds = 0;
            Cleaner.Result r = cleaner.clearConfigured();
            announce("Auto clear: " + r.items() + " items, " + r.mobs() + " mobs, " + r.arrows() + " arrows, " + r.xp() + " xp orbs.");
        }
        if (tick % 5 == 0 && c.getBoolean("cleanup.emergency") && tps() < c.getDouble("cleanup.emergency-tps")
                && System.currentTimeMillis() - lastEmergency > 60_000) {
            lastEmergency = System.currentTimeMillis();
            Cleaner.Result r = cleaner.clear(true, false, true, true);
            alert(ChatColor.RED + "Emergency cleanup (TPS " + String.format("%.1f", tps()) + "): " + r.total() + " entities removed.");
        }
        if (tick % 10 == 0 && c.getBoolean("limits.chunk-entity-limits")) cleaner.enforceLimits();
        if (tick % 30 == 0) adaptive();
    }

    private void adaptive() {
        if (!getConfig().getBoolean("performance.adaptive-view")) {
            if (reduce != 0) { reduce = 0; applyDistances(); }
            return;
        }
        double t = tps();
        if (t < 17 && reduce < 4) { reduce++; applyDistances(); }
        else if (t > 19.5 && reduce > 0) { reduce--; applyDistances(); }
    }

    // ---------- command ----------
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission(PERM)) { s.sendMessage(PREFIX + ChatColor.RED + "No permission."); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("menu")) {
            if (s instanceof Player pl) menus.main(pl);
            else s.sendMessage(PREFIX + "Use /opti status from console.");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "status" -> {
                s.sendMessage(PREFIX + String.format("TPS %.1f | MSPT %.1f | RAM %d/%d MB | Players %d | Frozen chunks %d | View reduce -%d",
                        tps(), mspt(), (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576,
                        Runtime.getRuntime().maxMemory() / 1048576, Bukkit.getOnlinePlayers().size(), guard.frozenCount(), reduce));
                s.sendMessage(PREFIX + "Menu mode: " + MenuFactory.mode + " (" + MenuFactory.reason + ")");
            }
            case "clear" -> {
                String t = a.length > 1 ? a[1].toLowerCase() : "all";
                Cleaner.Result r = switch (t) {
                    case "items" -> cleaner.clear(true, false, false, false);
                    case "mobs" -> cleaner.clear(false, true, false, false);
                    case "projectiles" -> cleaner.clear(false, false, true, false);
                    case "xp" -> cleaner.clear(false, false, false, true);
                    default -> cleaner.clear(true, true, true, true);
                };
                s.sendMessage(PREFIX + "Removed " + r.total() + " entities.");
            }
            case "reload" -> { reloadConfig(); applyDistances(); s.sendMessage(PREFIX + "Reloaded."); }
            case "config" -> {
                if (a.length >= 4 && a[1].equalsIgnoreCase("set")) {
                    Object old = getConfig().get(a[2]);
                    if (old == null) { s.sendMessage(PREFIX + ChatColor.RED + "Unknown key."); return true; }
                    try {
                        if (old instanceof Boolean) getConfig().set(a[2], Boolean.parseBoolean(a[3]));
                        else if (old instanceof Integer) getConfig().set(a[2], Integer.parseInt(a[3]));
                        else if (old instanceof Double) getConfig().set(a[2], Double.parseDouble(a[3]));
                        else getConfig().set(a[2], a[3]);
                    } catch (NumberFormatException ex) { s.sendMessage(PREFIX + ChatColor.RED + "Invalid value."); return true; }
                    saveConfig(); applyDistances();
                    s.sendMessage(PREFIX + a[2] + " = " + getConfig().get(a[2]));
                } else s.sendMessage(PREFIX + "/opti config set <path> <value>");
            }
            default -> s.sendMessage(PREFIX + "/opti [menu|status|clear [items|mobs|projectiles|xp|all]|config set <path> <value>|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("menu", "status", "clear", "config", "reload");
        if (a.length == 2 && a[0].equalsIgnoreCase("clear")) return List.of("items", "mobs", "projectiles", "xp", "all");
        if (a.length == 2 && a[0].equalsIgnoreCase("config")) return List.of("set");
        if (a.length == 3 && a[0].equalsIgnoreCase("config")) return new ArrayList<>(getConfig().getKeys(true));
        return List.of();
    }
}
