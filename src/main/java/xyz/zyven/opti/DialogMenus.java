package xyz.zyven.opti;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static net.kyori.adventure.text.format.NamedTextColor.*;

/** Dialog based UI. Needs Paper 1.21.6+ (loaded by MenuFactory only when the Dialog API exists). */
public final class DialogMenus implements MenuUI {
    private static final ClickCallback.Options OPTS =
            ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final ZyvenOpti p;

    public DialogMenus(ZyvenOpti p) { this.p = p; }

    // ---------- helpers ----------
    private static Component t(String s, NamedTextColor c) { return Component.text(s, c); }
    private static Component line(Component... parts) { return Component.textOfChildren(parts); }
    private static Component onOff(boolean b) { return b ? t("ON", GREEN) : t("OFF", RED); }

    private ActionButton btn(Component label, Consumer<Player> run) {
        return ActionButton.create(label, null, 150, DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player pl) || !pl.hasPermission(ZyvenOpti.PERM)) return;
            Bukkit.getScheduler().runTask(p, () -> run.accept(pl));
        }, OPTS));
    }

    private ActionButton backBtn() { return btn(t("Back", YELLOW), this::main); }

    private ActionButton closeBtn() { return ActionButton.create(t("Close", RED), null, 150, null); }

    private void show(Player pl, String title, List<Component> lines, List<ActionButton> buttons, ActionButton exit) {
        Component body = Component.join(JoinConfiguration.newlines(), lines);
        Dialog d = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(t(title, GREEN))
                        .body(List.of(DialogBody.plainMessage(body, 300)))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons, exit, 2)));
        pl.showDialog(d);
    }

    private ActionButton toggleBtn(String path, String label, Runnable reopen) {
        boolean v = p.getConfig().getBoolean(path);
        return btn(line(t(label + ": ", WHITE), onOff(v)), x -> {
            p.getConfig().set(path, !p.getConfig().getBoolean(path));
            p.saveConfig(); p.applyDistances(); reopen.run();
        });
    }

    private void change(String path, double d, double min) {
        Object v = p.getConfig().get(path);
        if (v instanceof Integer i) p.getConfig().set(path, (int) Math.max(min, i + d));
        else if (v instanceof Double db) p.getConfig().set(path, Math.max(min, Math.round((db + d) * 10) / 10.0));
        p.saveConfig(); p.applyDistances();
    }

    private ActionButton stepBtn(String label, String path, double d, double min, Runnable reopen) {
        return btn(t(label, WHITE), x -> { change(path, d, min); reopen.run(); });
    }

    private ActionButton simpleBtn(String label, NamedTextColor c, Runnable action) {
        return btn(t(label, c), x -> action.run());
    }

    private static String ago(long ms) {
        long s = (System.currentTimeMillis() - ms) / 1000;
        return s < 60 ? s + "s ago" : s < 3600 ? (s / 60) + "m ago" : (s / 3600) + "h ago";
    }

    // ---------- screens ----------
    @Override
    public void main(Player pl) {
        double tps = p.tps();
        NamedTextColor tc = tps >= 18 ? GREEN : tps >= 15 ? YELLOW : RED;
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576, max = rt.maxMemory() / 1048576;

        List<Component> lines = new ArrayList<>();
        lines.add(t("Analyze and optimize your server performance.", GRAY));
        if (!p.guard.history.isEmpty()) {
            Guard.LagEntry le = p.guard.history.get(p.guard.history.size() - 1);
            Location l = le.loc();
            lines.add(line(t("PROBLEM DETECTED", RED), t(" - Lag machine detected: " + le.type() + " <" + l.getBlockX() + ", "
                    + l.getBlockY() + ", " + l.getBlockZ() + "> " + l.getWorld().getName(), WHITE)));
        } else lines.add(t("No problems detected", GREEN));
        lines.add(Component.empty());
        lines.add(line(t("TPS: ", GRAY), t(String.format("%.1f", tps), tc), t("  (target 20.0)", DARK_GRAY)));
        lines.add(t(String.format("MSPT: %.1f | RAM: %d/%d MB | Players: %d", p.mspt(), used, max, Bukkit.getOnlinePlayers().size()), GRAY));
        lines.add(t("Frozen chunks: " + p.guard.frozenCount(), GRAY));

        List<ActionButton> b = List.of(
                btn(t("Plugin Manager", WHITE), this::plugins),
                btn(t("Performance Settings", WHITE), this::perf),
                btn(t("Cleanup Tools", WHITE), this::cleanup),
                btn(t("Redstone & Limiter", WHITE), this::redstone),
                btn(t("Lag Machines", WHITE), this::lag),
                btn(t("Config", WHITE), this::config),
                btn(t("Advanced Options", WHITE), this::adv),
                btn(t("Help", WHITE), this::help));
        show(pl, "Zyven Optimization", lines, b, closeBtn());
    }

    private void plugins(Player pl) {
        Plugin[] all = Bukkit.getPluginManager().getPlugins();
        Arrays.sort(all, Comparator.comparing(x -> x.getName().toLowerCase()));
        List<ActionButton> b = new ArrayList<>();
        for (int i = 0; i < Math.min(60, all.length); i++) {
            Plugin pg = all[i];
            boolean on = pg.isEnabled();
            b.add(btn(line(t(on ? "[ON] " : "[OFF] ", on ? GREEN : RED), t(pg.getName(), WHITE)), x -> {
                if (pg != p) {
                    if (pg.isEnabled()) Bukkit.getPluginManager().disablePlugin(pg);
                    else Bukkit.getPluginManager().enablePlugin(pg);
                }
                plugins(x);
            }));
        }
        show(pl, "Plugin Manager", List.of(t("Plugins (" + all.length + ") - click to enable / disable", GRAY)), b, backBtn());
    }

    private void perf(Player pl) {
        var c = p.getConfig();
        Runnable r = () -> perf(pl);
        List<Component> lines = List.of(
                t("Performance Settings", GREEN),
                line(t("View distance: ", GRAY), t(String.valueOf(c.getInt("performance.view-distance")), WHITE), t(" (reduced by " + p.reduce + ")", DARK_GRAY)),
                line(t("Simulation distance: ", GRAY), t(String.valueOf(c.getInt("performance.simulation-distance")), WHITE)),
                line(t("Adaptive mode: ", GRAY), onOff(c.getBoolean("performance.adaptive-view"))),
                line(t("Auto clear: ", GRAY), onOff(c.getBoolean("cleanup.auto-clear"))));
        List<ActionButton> b = List.of(
                btn(t("View Distance -1", WHITE), x -> { vd("performance.view-distance", -1); r.run(); }),
                btn(t("View Distance +1", WHITE), x -> { vd("performance.view-distance", 1); r.run(); }),
                btn(t("Simulation -1", WHITE), x -> { vd("performance.simulation-distance", -1); r.run(); }),
                btn(t("Simulation +1", WHITE), x -> { vd("performance.simulation-distance", 1); r.run(); }),
                toggleBtn("performance.adaptive-view", "Adaptive", r),
                toggleBtn("cleanup.auto-clear", "Auto Clear", r));
        show(pl, "Performance Settings", lines, b, backBtn());
    }

    private void vd(String path, int d) {
        p.getConfig().set(path, Math.max(2, Math.min(32, p.getConfig().getInt(path) + d)));
        p.saveConfig(); p.applyDistances();
    }

    private void cleanup(Player pl) {
        Cleaner.Result c = p.cleaner.count();
        Cleaner.Result l = p.cleaner.last;
        Runnable r = () -> cleanup(pl);
        List<Component> lines = List.of(
                t("Cleanup Tools", GREEN),
                t("Items on ground: " + c.items(), GRAY),
                t("Removable mobs: " + c.mobs(), GRAY),
                t("Arrows: " + c.arrows() + " | XP orbs: " + c.xp(), GRAY),
                t("Last cleanup: " + l.items() + " items, " + l.mobs() + " mobs, " + l.arrows() + " arrows, " + l.xp() + " xp", GRAY),
                line(t("Auto clear: ", GRAY), onOff(p.getConfig().getBoolean("cleanup.auto-clear")),
                        t(" every " + p.getConfig().getInt("cleanup.minutes") + " minute(s)", GRAY)),
                t("Named, tamed, leashed mobs and villagers are never removed.", DARK_GRAY));
        List<ActionButton> b = List.of(
                simpleBtn("Clear Items", WHITE, () -> { p.cleaner.clear(true, false, false, false); r.run(); }),
                simpleBtn("Clear Mobs", WHITE, () -> { p.cleaner.clear(false, true, false, false); r.run(); }),
                simpleBtn("Clear Projectiles", WHITE, () -> { p.cleaner.clear(false, false, true, false); r.run(); }),
                simpleBtn("Clear XP Orbs", WHITE, () -> { p.cleaner.clear(false, false, false, true); r.run(); }),
                simpleBtn("Clear Everything", RED, () -> { p.cleaner.clear(true, true, true, true); r.run(); }),
                simpleBtn("Refresh", WHITE, r));
        show(pl, "Cleanup Tools", lines, b, backBtn());
    }

    private void redstone(Player pl) {
        var c = p.getConfig();
        Runnable r = () -> redstone(pl);
        List<Component> lines = List.of(
                t("Redstone & Limiter", GREEN),
                line(t("Redstone: ", GRAY), c.getBoolean("redstone.enabled") ? t("ENABLED", GREEN) : t("DISABLED", RED)),
                line(t("Limiter: ", GRAY), onOff(c.getBoolean("redstone.limiter")), t(" | max " + c.getInt("limits.redstone-limit") + " updates/sec per chunk", GRAY)),
                t("Held back last second: " + p.guard.heldLastSecond + " | total: " + p.guard.heldTotal, GRAY),
                t("Frozen chunks: " + p.guard.frozenCount(), GRAY),
                t("The limiter holds extra updates of a chunk until the next second.", DARK_GRAY));
        List<ActionButton> b = List.of(
                toggleBtn("redstone.enabled", "Redstone", r),
                toggleBtn("redstone.limiter", "Limiter", r),
                stepBtn("Limit -100", "limits.redstone-limit", -100, 100, r),
                stepBtn("Limit +100", "limits.redstone-limit", 100, 100, r),
                btn(t("Freeze My Chunk", AQUA), x -> { p.guard.freeze(x.getLocation(), c.getInt("lag-machines.freeze-seconds")); r.run(); }),
                simpleBtn("Unfreeze All", GOLD, () -> { p.guard.unfreezeAll(); r.run(); }));
        show(pl, "Redstone & Limiter", lines, b, backBtn());
    }

    private void lag(Player pl) {
        List<Guard.LagEntry> h = p.guard.history;
        Runnable r = () -> lag(pl);
        List<Component> lines = new ArrayList<>();
        lines.add(line(t("Lag Machines ", GREEN), t("(detector ", GRAY), onOff(p.getConfig().getBoolean("lag-machines.detector")), t(")", GRAY)));
        List<ActionButton> b = new ArrayList<>();
        if (h.isEmpty()) lines.add(t("No lag machines detected.", GREEN));
        for (int i = 0; i < Math.min(10, h.size()); i++) {
            Guard.LagEntry le = h.get(h.size() - 1 - i);
            Location l = le.loc();
            lines.add(line(t("#" + (i + 1) + " " + le.type(), RED), t(" <" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + "> "
                    + l.getWorld().getName() + " (" + ago(le.time()) + ")" + (p.guard.isFrozen(l) ? " [frozen]" : ""), WHITE)));
            b.add(btn(t("Teleport #" + (i + 1), WHITE), x -> x.teleport(l.clone().add(0.5, 1, 0.5))));
        }
        lines.add(t("Frozen chunks: " + p.guard.frozenCount(), GRAY));
        b.add(simpleBtn("Unfreeze All", GOLD, () -> { p.guard.unfreezeAll(); r.run(); }));
        b.add(simpleBtn("Clear History", RED, () -> { p.guard.history.clear(); r.run(); }));
        b.add(simpleBtn("Refresh", WHITE, r));
        show(pl, "Lag Machines", lines, b, backBtn());
    }

    private void config(Player pl) {
        var c = p.getConfig();
        Runnable r = () -> config(pl);
        List<Component> lines = new ArrayList<>();
        lines.add(t("Config", GREEN));
        List<ActionButton> b = new ArrayList<>();
        for (String[] e : Menus.CFG) {
            String path = e[0], label = e[1];
            Object v = c.get(path);
            lines.add(line(t(label + ": ", GRAY), t(String.valueOf(v), WHITE)));
            if (v instanceof Boolean) b.add(toggleBtn(path, label, r));
            else {
                double step = v instanceof Double ? 1 : Double.parseDouble(e[2]);
                b.add(stepBtn(label + " +", path, step, 1, r));
                b.add(stepBtn(label + " -", path, -step, 1, r));
            }
        }
        lines.add(t("Or use /opti config set <path> <value>", DARK_GRAY));
        b.add(simpleBtn("Reload config.yml", GREEN, () -> { p.reloadConfig(); r.run(); }));
        show(pl, "Config", lines, b, backBtn());
    }

    private void adv(Player pl) {
        var c = p.getConfig();
        Runnable r = () -> adv(pl);
        List<Component> lines = List.of(
                t("Advanced Options", GREEN),
                line(t("Lag detector: ", GRAY), onOff(c.getBoolean("lag-machines.detector")), t(" | Auto-freeze: ", GRAY), onOff(c.getBoolean("lag-machines.auto-freeze"))),
                line(t("Auto clear: ", GRAY), onOff(c.getBoolean("cleanup.auto-clear")), t(" | Chunk limits: ", GRAY), onOff(c.getBoolean("limits.chunk-entity-limits"))),
                line(t("Adaptive view: ", GRAY), onOff(c.getBoolean("performance.adaptive-view")), t(" | Emergency: ", GRAY), onOff(c.getBoolean("cleanup.emergency"))));
        List<ActionButton> b = List.of(
                toggleBtn("lag-machines.detector", "Lag Detector", r),
                toggleBtn("lag-machines.auto-freeze", "Auto-Freeze", r),
                toggleBtn("cleanup.auto-clear", "Auto Clear", r),
                toggleBtn("limits.chunk-entity-limits", "Chunk Limits", r),
                toggleBtn("performance.adaptive-view", "Adaptive View", r),
                toggleBtn("cleanup.emergency", "Emergency Cleanup", r),
                simpleBtn("Reload Files", WHITE, () -> { p.reloadConfig(); p.applyDistances(); r.run(); }));
        show(pl, "Advanced Options", lines, b, backBtn());
    }

    private void help(Player pl) {
        List<Component> lines = List.of(
                t("Zyven Opti Commands", GREEN),
                t("/opti - open menu", WHITE),
                t("/opti status - TPS / MSPT / RAM", WHITE),
                t("/opti clear [items|mobs|projectiles|xp|all]", WHITE),
                t("/opti config set <path> <value>", WHITE),
                t("/opti reload", WHITE));
        show(pl, "Help", lines, List.of(backBtn()), null);
    }
}
