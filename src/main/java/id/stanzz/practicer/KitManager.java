package id.stanzz.practicer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class KitManager {

    public static final class Kit {
        public final ItemStack[] storage = new ItemStack[36];
        public ItemStack[] armor = new ItemStack[4]; // boots, legs, chest, helmet
        public ItemStack offhand;
    }

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            yml = new YamlConfiguration();
            putDefaults();
            save();
        } else {
            yml = YamlConfiguration.loadConfiguration(file);
        }
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal menyimpan kits.yml: " + e.getMessage());
        }
    }

    public TreeSet<String> names() {
        return new TreeSet<>(yml.getKeys(false));
    }

    public boolean exists(String name) {
        return yml.isConfigurationSection(name.toLowerCase());
    }

    public Kit get(String name) {
        String key = name.toLowerCase();
        if (!yml.isConfigurationSection(key)) return null;
        Kit k = new Kit();
        List<?> st = yml.getList(key + ".storage");
        if (st != null) {
            for (int i = 0; i < Math.min(36, st.size()); i++) k.storage[i] = clean(st.get(i));
        }
        List<?> ar = yml.getList(key + ".armor");
        if (ar != null) {
            for (int i = 0; i < Math.min(4, ar.size()); i++) k.armor[i] = clean(ar.get(i));
        }
        k.offhand = clean(yml.get(key + ".offhand"));
        return k;
    }

    private static ItemStack clean(Object o) {
        if (o instanceof ItemStack s && !s.getType().isAir()) return s;
        return null;
    }

    private static ItemStack orAir(ItemStack s) {
        return s == null ? new ItemStack(Material.AIR) : s;
    }

    public void put(String name, Kit k) {
        String key = name.toLowerCase();
        List<ItemStack> st = new ArrayList<>();
        for (ItemStack s : k.storage) st.add(orAir(s));
        List<ItemStack> ar = new ArrayList<>();
        for (ItemStack s : k.armor) ar.add(orAir(s));
        yml.set(key + ".storage", st);
        yml.set(key + ".armor", ar);
        yml.set(key + ".offhand", orAir(k.offhand));
        save();
    }

    public void delete(String name) {
        yml.set(name.toLowerCase(), null);
        save();
    }

    /** Simpan inventory pemain (hotbar+inventory+armor+offhand) sebagai kit. */
    public void saveFromPlayer(String name, Player p) {
        PlayerInventory inv = p.getInventory();
        Kit k = new Kit();
        ItemStack[] st = inv.getStorageContents();
        for (int i = 0; i < Math.min(36, st.length); i++) k.storage[i] = st[i] == null ? null : st[i].clone();
        ItemStack[] ar = inv.getArmorContents();
        for (int i = 0; i < 4; i++) k.armor[i] = ar[i] == null ? null : ar[i].clone();
        ItemStack off = inv.getItemInOffHand();
        k.offhand = off.getType().isAir() ? null : off.clone();
        put(name, k);
    }

    public void apply(Player p, Kit k) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setStorageContents(cloneAll(k.storage));
        inv.setArmorContents(cloneAll(k.armor));
        inv.setItemInOffHand(k.offhand == null ? new ItemStack(Material.AIR) : k.offhand.clone());
    }

    private static ItemStack[] cloneAll(ItemStack[] a) {
        ItemStack[] out = new ItemStack[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] == null ? null : a[i].clone();
        return out;
    }

    // ---------- default kits ----------

    private static ItemStack it(String mat, int amt, String... ench) {
        Material m = Material.matchMaterial(mat);
        if (m == null) return null; // item belum ada di versi server ini (mis. spear)
        ItemStack s = new ItemStack(m, amt);
        for (String e : ench) {
            String[] p = e.split(":");
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0]));
            if (en != null) s.addUnsafeEnchantment(en, Integer.parseInt(p[1]));
        }
        return s;
    }

    private static ItemStack potion(PotionType type, boolean splash) {
        ItemStack s = new ItemStack(splash ? Material.SPLASH_POTION : Material.POTION);
        PotionMeta meta = (PotionMeta) s.getItemMeta();
        meta.setBasePotionType(type);
        s.setItemMeta(meta);
        return s;
    }

    private static ItemStack[] armor(String mat, String... ench) {
        return new ItemStack[]{
                it(mat + "_BOOTS", 1, ench), it(mat + "_LEGGINGS", 1, ench),
                it(mat + "_CHESTPLATE", 1, ench), it(mat + "_HELMET", 1, ench)};
    }

    private void define(String name, ItemStack[] armor, ItemStack offhand, ItemStack... items) {
        Kit k = new Kit();
        k.armor = armor;
        k.offhand = offhand;
        int i = 0;
        for (ItemStack s : items) {
            if (s == null || i >= 36) continue;
            k.storage[i++] = s;
        }
        put(name, k);
    }

    private void putDefaults() {
        String[] netherite = {"protection:4", "unbreaking:3", "mending:1"};

        define("mace", armor("NETHERITE", netherite), it("TOTEM_OF_UNDYING", 1),
                it("MACE", 1, "density:5", "unbreaking:3", "mending:1"),
                it("NETHERITE_AXE", 1, "sharpness:5", "unbreaking:3", "mending:1"),
                it("WIND_CHARGE", 64), it("GOLDEN_APPLE", 64), it("ENDER_PEARL", 16),
                it("COBWEB", 16), it("WATER_BUCKET", 1), it("TOTEM_OF_UNDYING", 1));

        define("crystal", armor("NETHERITE", netherite), it("TOTEM_OF_UNDYING", 1),
                it("NETHERITE_SWORD", 1, "sharpness:5", "unbreaking:3", "mending:1"),
                it("OBSIDIAN", 64), it("END_CRYSTAL", 64), it("GOLDEN_APPLE", 64),
                it("ENDER_PEARL", 16), it("EXPERIENCE_BOTTLE", 64),
                it("TOTEM_OF_UNDYING", 1), it("TOTEM_OF_UNDYING", 1));

        define("spear", armor("NETHERITE", netherite), it("TOTEM_OF_UNDYING", 1),
                it("NETHERITE_SPEAR", 1, "lunge:3", "unbreaking:3", "mending:1"),
                it("SHIELD", 1), it("GOLDEN_APPLE", 64), it("ENDER_PEARL", 16),
                it("COBWEB", 16), it("TOTEM_OF_UNDYING", 1));

        define("sword", armor("NETHERITE", netherite), it("SHIELD", 1),
                it("NETHERITE_SWORD", 1, "sharpness:5", "unbreaking:3", "mending:1", "fire_aspect:2"),
                it("GOLDEN_APPLE", 64), it("ENDER_PEARL", 16), it("COOKED_BEEF", 64));

        define("axe", armor("NETHERITE", netherite), it("SHIELD", 1),
                it("NETHERITE_AXE", 1, "sharpness:5", "unbreaking:3", "mending:1"),
                it("NETHERITE_SWORD", 1, "sharpness:5", "unbreaking:3", "mending:1"),
                it("GOLDEN_APPLE", 64), it("ENDER_PEARL", 16), it("COOKED_BEEF", 64));

        List<ItemStack> pot = new ArrayList<>();
        pot.add(it("DIAMOND_SWORD", 1, "sharpness:5", "unbreaking:3"));
        pot.add(it("ENDER_PEARL", 16));
        pot.add(potion(PotionType.STRONG_SWIFTNESS, true));
        pot.add(potion(PotionType.STRONG_STRENGTH, true));
        for (int i = 0; i < 25; i++) pot.add(potion(PotionType.STRONG_HEALING, true));
        define("pot", armor("DIAMOND", "protection:4", "unbreaking:3"), null, pot.toArray(new ItemStack[0]));

        define("smp", armor("DIAMOND", "protection:3", "unbreaking:3", "mending:1"), it("SHIELD", 1),
                it("DIAMOND_SWORD", 1, "sharpness:5", "unbreaking:3", "mending:1"),
                it("BOW", 1, "power:5", "unbreaking:3", "infinity:1"),
                it("ARROW", 1), it("GOLDEN_APPLE", 32), it("COOKED_BEEF", 64));
    }
          }
