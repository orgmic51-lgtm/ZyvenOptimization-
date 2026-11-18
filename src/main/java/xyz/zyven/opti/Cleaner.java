package xyz.zyven.opti;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.util.*;

public final class Cleaner {
    public record Result(int items, int mobs, int arrows, int xp) {
        public int total() { return items + mobs + arrows + xp; }
    }

    private final ZyvenOpti p;
    Result last = new Result(0, 0, 0, 0);

    Cleaner(ZyvenOpti p) { this.p = p; }

    /** Named, tamed, leashed mobs, villagers and bosses are never removed. */
    public static boolean removableMob(Entity e) {
        if (!(e instanceof Mob m)) return false;
        if (e instanceof Villager || e instanceof WanderingTrader || e instanceof Boss) return false;
        if (e.getCustomName() != null) return false;
        if (e instanceof Tameable t && t.isTamed()) return false;
        if (m.isLeashed()) return false;
        return !e.isPersistent() || true; // persistent natural mobs are still removable
    }

    public Result count() {
        int i = 0, m = 0, a = 0, x = 0;
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) {
                if (e instanceof Item) i++;
                else if (e instanceof AbstractArrow) a++;
                else if (e instanceof ExperienceOrb) x++;
                else if (removableMob(e)) m++;
            }
        return new Result(i, m, a, x);
    }

    public Result clearConfigured() {
        var c = p.getConfig();
        return clear(c.getBoolean("cleanup.clear-items"), c.getBoolean("cleanup.clear-mobs"),
                c.getBoolean("cleanup.clear-arrows"), c.getBoolean("cleanup.clear-xp-orbs"));
    }

    public Result clear(boolean items, boolean mobs, boolean proj, boolean xp) {
        int i = 0, m = 0, a = 0, x = 0;
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) {
                if (e instanceof Item && items) { e.remove(); i++; }
                else if (e instanceof AbstractArrow && proj) { e.remove(); a++; }
                else if (e instanceof ExperienceOrb && xp) { e.remove(); x++; }
                else if (mobs && removableMob(e)) { e.remove(); m++; }
            }
        last = new Result(i, m, a, x);
        return last;
    }

    /** Per-chunk entity limits. */
    public void enforceLimits() {
        var c = p.getConfig();
        int maxItems = c.getInt("limits.max-items-per-chunk");
        int maxHostile = c.getInt("limits.max-hostile-per-chunk");
        int maxAll = c.getInt("limits.max-entities-per-chunk");
        int removed = 0;
        for (World w : Bukkit.getWorlds())
            for (Chunk ch : w.getLoadedChunks()) {
                Entity[] ents = ch.getEntities();
                if (ents.length < Math.min(maxItems, Math.min(maxHostile, maxAll))) continue;
                List<Entity> items = new ArrayList<>(), hostile = new ArrayList<>(), mobs = new ArrayList<>();
                for (Entity e : ents) {
                    if (e instanceof Item) items.add(e);
                    else if (removableMob(e)) { mobs.add(e); if (e instanceof Monster) hostile.add(e); }
                }
                for (int k = maxItems; k < items.size(); k++) { items.get(k).remove(); removed++; }
                for (int k = maxHostile; k < hostile.size(); k++) { hostile.get(k).remove(); mobs.remove(hostile.get(k)); removed++; }
                int left = ents.length - Math.max(0, items.size() - maxItems) - Math.max(0, hostile.size() - maxHostile);
                for (int k = 0; left > maxAll && k < mobs.size(); k++)
                    if (mobs.get(k).isValid()) { mobs.get(k).remove(); left--; removed++; }
            }
        if (removed > 0) p.getLogger().info("Chunk limits removed " + removed + " entities.");
    }
}
