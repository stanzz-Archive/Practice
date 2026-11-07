package id.stanzz.practicer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TeleportManager {

    private record Request(UUID from, UUID to, boolean here, long expires) {}

    private static final Set<Material> UNSAFE = EnumSet.of(
            Material.LAVA, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE, Material.SOUL_FIRE,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
            Material.WATER);

    private final JavaPlugin plugin;
    private final BotManager bots;
    private final Map<UUID, Request> byTarget = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();

    public TeleportManager(JavaPlugin plugin, BotManager bots) {
        this.plugin = plugin;
        this.bots = bots;
    }

    // ---------- RTP ----------

    public void rtp(Player p) {
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("rtp.cooldown-seconds", 30) * 1000L;
        Long last = rtpCooldown.get(p.getUniqueId());
        if (last != null && now - last < cd && !p.hasPermission("practice.admin")) {
            long left = (cd - (now - last)) / 1000 + 1;
            StanzzPracticer.msg(p, "§cTunggu " + left + " detik lagi untuk /rtp.");
            return;
        }
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            StanzzPracticer.msg(p, "§c/rtp hanya bisa di Overworld.");
            return;
        }
        rtpCooldown.put(p.getUniqueId(), now);
        StanzzPracticer.msg(p, "Mencari lokasi aman...");
        attempt(p, w, plugin.getConfig().getInt("rtp.radius", 5000), 0);
    }

    private void attempt(Player p, World w, int radius, int tries) {
        if (tries >= 12) {
            StanzzPracticer.msg(p, "§cGagal menemukan lokasi aman, coba lagi.");
            rtpCooldown.remove(p.getUniqueId());
            return;
        }
        Location center = w.getSpawnLocation();
        final int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        final int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!p.isOnline()) return;
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = w.getBlockAt(x, y, z);
            Location dest = new Location(w, x + 0.5, y + 1, z + 0.5);
            boolean bad = ground.getType().isAir() || ground.isLiquid() || UNSAFE.contains(ground.getType())
                    || !w.getWorldBorder().isInside(dest)
                    || !w.getBlockAt(x, y + 1, z).isPassable()
                    || !w.getBlockAt(x, y + 2, z).isPassable();
            if (bad) {
                attempt(p, w, radius, tries + 1);
                return;
            }
            p.teleportAsync(dest).thenAccept(ok -> {
                if (ok) StanzzPracticer.msg(p, "Teleport ke §a" + dest.getBlockX() + ", " + dest.getBlockY() + ", " + dest.getBlockZ());
            });
        });
    }

    // ---------- TPA ----------

    public void request(Player from, Player to, boolean here) {
        if (from.equals(to)) {
            StanzzPracticer.msg(from, "§cTidak bisa request ke diri sendiri.");
            return;
        }
        long timeout = plugin.getConfig().getLong("tpa.timeout-seconds", 60);
        byTarget.put(to.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), here,
                System.currentTimeMillis() + timeout * 1000L));

        StanzzPracticer.msg(from, "Request dikirim ke §b" + to.getName() + "§f. Batalkan dengan /tpacancel.");
        String text = here
                ? from.getName() + " meminta kamu teleport ke dia. "
                : from.getName() + " meminta teleport ke kamu. ";
        to.sendMessage(Component.text(StanzzPracticer.PREFIX + text)
                .append(Component.text("[TERIMA]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept")))
                .append(Component.text(" "))
                .append(Component.text("[TOLAK]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny"))));
    }

    private Request take(Player target) {
        Request r = byTarget.remove(target.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) return null;
        return r;
    }

    public void accept(Player target) {
        Request r = take(target);
        if (r == null) {
            StanzzPracticer.msg(target, "§cTidak ada request teleport.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            StanzzPracticer.msg(target, "§cPengirim request sudah offline.");
            return;
        }
        if (bots.inSession(from) || bots.inSession(target)) {
            StanzzPracticer.msg(target, "§cSalah satu pemain sedang practice.");
            return;
        }
        if (r.here()) {
            target.teleportAsync(from.getLocation());
        } else {
            from.teleportAsync(target.getLocation());
        }
        StanzzPracticer.msg(target, "Request diterima.");
        StanzzPracticer.msg(from, "§b" + target.getName() + "§f menerima request kamu.");
    }

    public void deny(Player target) {
        Request r = take(target);
        if (r == null) {
            StanzzPracticer.msg(target, "§cTidak ada request teleport.");
            return;
        }
        StanzzPracticer.msg(target, "Request ditolak.");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) StanzzPracticer.msg(from, "§b" + target.getName() + "§f menolak request kamu.");
    }

    public void cancel(Player from) {
        int n = 0;
        for (Iterator<Request> it = byTarget.values().iterator(); it.hasNext(); ) {
            if (it.next().from().equals(from.getUniqueId())) {
                it.remove();
                n++;
            }
        }
        StanzzPracticer.msg(from, n > 0 ? "Request teleport dibatalkan." : "§cKamu tidak punya request aktif.");
    }
    }
