package id.stanzz.practicer;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class StanzzPracticer extends JavaPlugin {
    public static final String PREFIX = "§8[§bstanzzPracticer§8] §f";

    private KitManager kits;
    private BotManager bots;
    private TeleportManager teleports;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        kits = new KitManager(this);
        bots = new BotManager(this, kits);
        teleports = new TeleportManager(this, bots);
        getServer().getPluginManager().registerEvents(bots, this);

        Commands handler = new Commands(this, kits, bots, teleports);
        for (String name : new String[]{"practice", "kit", "bot", "rtp", "tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(handler);
                cmd.setTabCompleter(handler);
            }
        }
    }

    @Override
    public void onDisable() {
        if (bots != null) bots.stopAll();
    }

    public static void msg(CommandSender to, String text) {
        to.sendMessage(PREFIX + text);
    }
}
