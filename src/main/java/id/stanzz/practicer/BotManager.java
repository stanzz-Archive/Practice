package id.stanzz.practicer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BotManager implements Listener {

    private static final class Session {
        String kit;
        ItemStack[] storage;
        ItemStack[] armor;
        ItemStack offhand;
        final Set<UUID> bots = new HashSet<>();
        final Set<Block> placed = new HashSet<>();
        int kills;
    }

    private final JavaPlugin plugin;
    private final KitManager kits;
    private final NamespacedKey botKey;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public BotManager(JavaPlugin plugin, KitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
        this.botKey = new NamespacedKey(plugin, "bot_owner");
    }

    public boolean inSession(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    // ---------- start / stop ----------

    public boolean start(Player p, String kitName) {
        KitManager.Kit kit = kits.get(kitName);
        if (kit == null) return false;

        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            s = new Session();
            PlayerInventory inv = p.getInventory();
            s.storage = cloneAll(inv.getStorageContents());
            s.armor = cloneAll(inv.getArmorContents());
            s.offhand = inv.getItemInOffHand().clone();
            sessions.put(p.getUniqueId(), s);
        } else {
            removeBots(s);
        }
        s.kit = kitName.toLowerCase();
        kits.apply(p, kit);

        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);

        spawnBot(p, s);
        return true;
    }

    public void stop(Player p, boolean announce) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        removeBots(s);
        for (Block b : s.placed) b.setType(Material.AIR);
        for (Entity en : p.getNearbyEntities(60, 60, 60)) {
            if (en instanceof EnderCrystal) en.remove();
        }
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(s.storage);
        inv.setArmorContents(s.armor);
        inv.setItemInOffHand(s.offhand);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        if (announce) StanzzPracticer.msg(p, "Practice selesai. Bot dikalahkan: §a" + s.kills + "§f. Inventory kamu dikembalikan.");
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stop(p, false);
        }
    }

    public void spawn(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) spawnBot(p, s);
    }

    public void clearBots(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) removeBots(s);
    }

    private void removeBots(Session s) {
        for (UUID id : s.bots) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        s.bots.clear();
    }

    // ---------- bot ----------

    private void spawnBot(Player p, Session s) {
        KitManager.Kit kit = kits.get(s.kit);
        if (kit == null) return;

        Location base = p.getLocation();
        Vector dir = base.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        Location loc = base.clone().add(dir.normalize().multiply(6));

        double health = plugin.getConfig().getDouble("bot.health", 40);
        double speed = plugin.getConfig().getDouble("bot.speed", 0.28);

        Zombie bot = p.getWorld().spawn(loc, Zombie.class, e -> {
            e.setAdult();
            e.setShouldBurnInDay(false);
            e.customName(Component.text("Bot " + s.kit, NamedTextColor.RED));
            e.setCustomNameVisible(true);
            e.setRemoveWhenFarAway(false);
            e.setCanPickupItems(false);
            e.setPersistent(false);

            EntityEquipment eq = e.getEquipment();
            eq.setArmorContents(cloneAll(kit.armor));
            ItemStack hand = kit.storage[0];
            eq.setItemInMainHand(hand == null ? new ItemStack(Material.AIR) : hand.clone());
            eq.setItemInOffHand(kit.offhand == null ? new ItemStack(Material.AIR) : kit.offhand.clone());
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);

            setAttr(e, Attribute.MAX_HEALTH, health);
            e.setHealth(health);
            setAttr(e, Attribute.MOVEMENT_SPEED, speed);
            setAttr(e, Attribute.FOLLOW_RANGE, 64);

            e.getPersistentDataContainer().set(botKey, PersistentDataType.STRING, p.getUniqueId().toString());
            e.setTarget(p);
        });
        s.bots.add(bot.getUniqueId());
    }

    private static void setAttr(Zombie z, Attribute a, double v) {
        AttributeInstance inst = z.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private String ownerOf(Entity e) {
        return e.getPersistentDataContainer().get(botKey, PersistentDataType.STRING);
    }

    private static ItemStack[] cloneAll(ItemStack[] a) {
        ItemStack[] out = new ItemStack[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] == null ? null : a[i].clone();
        return out;
    }

    private boolean nearSession(Location loc, double dist) {
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc) <= dist * dist) {
                return true;
            }
        }
        return false;
    }

    // ---------- events ----------

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        String owner = ownerOf(e.getEntity());
        if (owner == null) return;
        if (e.getTarget() == null || !e.getTarget().getUniqueId().toString().equals(owner)) e.setCancelled(true);
    }

    @EventHandler
    public void onBotDeath(EntityDeathEvent e) {
        String owner = ownerOf(e.getEntity());
        if (owner == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);

        UUID ownerId = UUID.fromString(owner);
        Player p = Bukkit.getPlayer(ownerId);
        Session s = sessions.get(ownerId);
        if (p == null || s == null) return;

        s.bots.remove(e.getEntity().getUniqueId());
        s.kills++;
        StanzzPracticer.msg(p, "Bot kalah! Total kill: §a" + s.kills);

        long delay = plugin.getConfig().getLong("bot.respawn-delay-ticks", 60);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(ownerId) == s && p.isOnline()) spawnBot(p, s);
        }, delay);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!sessions.containsKey(e.getEntity().getUniqueId())) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!sessions.containsKey(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) stop(p, true);
        }, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stop(e.getPlayer(), false);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (inSession(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) s.placed.add(e.getBlock());
    }

    @EventHandler
    public void onBucket(PlayerBucketEmptyEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) s.placed.add(e.getBlock());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null && !s.placed.remove(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (nearSession(e.getLocation(), 15)) e.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (nearSession(e.getBlock().getLocation(), 15)) e.blockList().clear();
    }

    @EventHandler
    public void onReinforcement(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS && nearSession(e.getLocation(), 25)) {
            e.setCancelled(true);
        }
    }
                          }
