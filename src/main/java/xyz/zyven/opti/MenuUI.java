package xyz.zyven.opti;

import org.bukkit.entity.Player;

/** Common interface so the plugin can use chest menus (1.21-1.21.5) or dialogs (1.21.6+). */
public interface MenuUI {
    void main(Player player);
}
