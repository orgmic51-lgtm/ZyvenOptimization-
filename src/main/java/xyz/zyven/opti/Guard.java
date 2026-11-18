package xyz.zyven.opti;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.*;

/** Redstone limiter, piston/hopper/dispenser limits, lag machine detector and chunk freezing. */
public final class Guard implements Listener {
    record Key(UUID w, int x, int z) {}

    static final class Stat { int count, streak; Location loc; }

    public record LagEntry(String type, Location loc, long time) {}

    private final ZyvenOpti p;
    private final Map<Key, Stat> redstone = new HashMap<>();
    private final Map<Key, int[]> pistons = new HashMap<>(), hoppers = new HashMap<>(), dispensers = new HashMap<>();
    private final Map<Key, Long> frozen = new HashMap<>();
    private final Map<Key, Long> reported = new HashMap<>();
    final List<LagEntry> history = new ArrayList<>();
    int heldLastSecond = 0, heldTotal = 0, heldThisSecond = 0;

    Guard(ZyvenOpti p) { this.p = p; }

    static Key key(Location l) { return new Key(l.getWorld().getUID(), l.getBlockX() >> 4, l.getBlockZ() >> 4); }

    public boolean isFrozen(Key k) {
        Long until = frozen.get(k);
        if (until == null) return false;
        if (until < System.currentTimeMillis()) { frozen.remove(k); return false; }
        return true;
    }

    public boolean isFrozen(Location l) { return isFrozen(key(l)); }
    public int frozenCount() { frozen.values().removeIf(t -> t < System.currentTimeMillis()); return frozen.size(); }
    public void freeze(Location l, int seconds) { frozen.put(key(l), System.currentTimeMillis() + seconds * 1000L); }
    public void unfreezeAll() { frozen.clear(); }

    /** Called once per second. */
    void tick() {
        var c = p.getConfig();
        int limit = c.getInt("limits.redstone-limit");
        boolean detect = c.getBoolean("lag-machines.detector");
        heldLastSecond = heldThisSecond; heldThisSecond = 0;

        Iterator<Map.Entry<Key, Stat>> it = redstone.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Stat s = en.getValue();
            if (s.count > limit) s.streak++; else s.streak = 0;
            s.count = 0;
            if (detect && s.streak >= 3 && !isFrozen(en.getKey())) {
                long now = System.currentTimeMillis();
                Long rep = reported.get(en.getKey());
                if (rep == null || now - rep > 60_000) report(en.getKey(), s.loc, "REDSTONE CLOCK");
                s.streak = 0;
            }
            if (s.streak == 0) it.remove();
        }
        pistons.clear(); hoppers.clear(); dispensers.clear();
    }

    private void report(Key k, Location loc, String type) {
        var c = p.getConfig();
        reported.put(k, System.currentTimeMillis());
        history.add(new LagEntry(type, loc, System.currentTimeMillis()));
        if (history.size() > 36) history.remove(0);
        if (c.getBoolean("lag-machines.auto-freeze")) freeze(loc, c.getInt("lag-machines.freeze-seconds"));
        if (c.getBoolean("announcements.announce-lag-machines")) {
            String where = c.getBoolean("announcements.show-coordinates")
                    ? " <" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "> " + loc.getWorld().getName() : "";
            p.alert(ChatColor.RED + "Lag machine detected: " + type + where
                    + (c.getBoolean("lag-machines.auto-freeze") ? ChatColor.GRAY + " (chunk frozen)" : ""));
        }
    }

    private boolean over(Map<Key, int[]> m, Location l, int limit) {
        return ++m.computeIfAbsent(key(l), x -> new int[1])[0] > limit;
    }

    // ---------- events ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent e) {
        var c = p.getConfig();
        if (!c.getBoolean("redstone.enabled")) { e.setNewCurrent(e.getOldCurrent()); return; }
        Block b = e.getBlock();
        Key k = key(b.getLocation());
        if (isFrozen(k)) { e.setNewCurrent(e.getOldCurrent()); return; }
        Stat s = redstone.computeIfAbsent(k, x -> new Stat());
        s.loc = b.getLocation();
        s.count++;
        if (s.count > c.getInt("limits.redstone-limit") && c.getBoolean("redstone.limiter")) {
            e.setNewCurrent(e.getOldCurrent());
            heldThisSecond++; heldTotal++;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent e) {
        if (isFrozen(e.getBlock().getLocation()) || over(pistons, e.getBlock().getLocation(), p.getConfig().getInt("limits.piston-limit"))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent e) {
        if (isFrozen(e.getBlock().getLocation()) || over(pistons, e.getBlock().getLocation(), p.getConfig().getInt("limits.piston-limit"))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (isFrozen(e.getBlock().getLocation()) || over(dispensers, e.getBlock().getLocation(), p.getConfig().getInt("limits.dispenser-limit"))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        Location l = e.getSource().getLocation();
        if (l == null) return;
        if (isFrozen(l) || over(hoppers, l, p.getConfig().getInt("limits.hopper-limit"))) e.setCancelled(true);
    }
}
