package id.stanzz.practicer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements CommandExecutor, TabCompleter {

    private final StanzzPracticer plugin;
    private final KitManager kits;
    private final BotManager bots;
    private final TeleportManager tp;

    public Commands(StanzzPracticer plugin, KitManager kits, BotManager bots, TeleportManager tp) {
        this.plugin = plugin;
        this.kits = kits;
        this.bots = bots;
        this.tp = tp;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            StanzzPracticer.msg(sender, "Command ini hanya untuk pemain.");
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "practice" -> practice(p, args);
            case "kit" -> kit(p, args);
            case "bot" -> bot(p, args);
            case "rtp" -> {
                if (bots.inSession(p)) StanzzPracticer.msg(p, "§cSelesaikan practice dulu (/practice stop).");
                else tp.rtp(p);
            }
            case "tpa", "tpahere" -> {
                if (args.length < 1) {
                    StanzzPracticer.msg(p, "§cPakai: /" + cmd.getName() + " <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    StanzzPracticer.msg(p, "§cPemain tidak ditemukan.");
                } else if (bots.inSession(p)) {
                    StanzzPracticer.msg(p, "§cSelesaikan practice dulu (/practice stop).");
                } else {
                    tp.request(p, target, cmd.getName().equalsIgnoreCase("tpahere"));
                }
            }
            case "tpaccept" -> tp.accept(p);
            case "tpdeny" -> tp.deny(p);
            case "tpacancel" -> tp.cancel(p);
            default -> { return false; }
        }
        return true;
    }

    private void practice(Player p, String[] args) {
        if (args.length < 1) {
            StanzzPracticer.msg(p, "Kit: §b" + String.join("§f, §b", kits.names()) + "§f. Pakai /practice <kit> atau /practice stop.");
            return;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (!bots.inSession(p)) StanzzPracticer.msg(p, "§cKamu tidak sedang practice.");
            else bots.stop(p, true);
            return;
        }
        if (!bots.start(p, args[0])) {
            StanzzPracticer.msg(p, "§cKit tidak ditemukan. Cek /kit list.");
            return;
        }
        StanzzPracticer.msg(p, "Practice kit §b" + args[0].toLowerCase() + "§f dimulai! Stop dengan /practice stop.");
    }

    private void kit(Player p, String[] args) {
        if (args.length < 1 || args[0].equalsIgnoreCase("list")) {
            StanzzPracticer.msg(p, "Kit: §b" + String.join("§f, §b", kits.names()));
            return;
        }
        if (!p.hasPermission("practice.admin")) {
            StanzzPracticer.msg(p, "§cKamu tidak punya izin edit kit.");
            return;
        }
        if (bots.inSession(p)) {
            StanzzPracticer.msg(p, "§cStop practice dulu sebelum edit kit.");
            return;
        }
        if (args.length < 2) {
            StanzzPracticer.msg(p, "§cPakai: /kit <load|save|delete> <nama>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,24}")) {
            StanzzPracticer.msg(p, "§cNama kit hanya huruf kecil/angka/_/- (maks 24).");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "load" -> {
                KitManager.Kit k = kits.get(name);
                if (k == null) {
                    StanzzPracticer.msg(p, "§cKit tidak ada.");
                    return;
                }
                kits.apply(p, k);
                StanzzPracticer.msg(p, "Kit §b" + name + "§f dimuat ke inventory. Ubah, lalu /kit save " + name);
            }
            case "save", "create", "edit" -> {
                kits.saveFromPlayer(name, p);
                StanzzPracticer.msg(p, "Kit §b" + name + "§f disimpan dari inventory kamu.");
            }
            case "delete" -> {
                if (!kits.exists(name)) {
                    StanzzPracticer.msg(p, "§cKit tidak ada.");
                    return;
                }
                kits.delete(name);
                StanzzPracticer.msg(p, "Kit §b" + name + "§f dihapus.");
            }
            default -> StanzzPracticer.msg(p, "§cPakai: /kit <list|load|save|delete> [nama]");
        }
    }

    private void bot(Player p, String[] args) {
        if (!bots.inSession(p)) {
            StanzzPracticer.msg(p, "§cMulai dulu dengan /practice <kit>.");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("spawn")) {
            bots.spawn(p);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
            bots.clearBots(p);
            StanzzPracticer.msg(p, "Semua bot dihapus.");
        } else {
            StanzzPracticer.msg(p, "§cPakai: /bot <spawn|clear>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "practice" -> {
                if (args.length == 1) {
                    out.addAll(kits.names());
                    out.add("stop");
                }
            }
            case "kit" -> {
                if (args.length == 1) out.addAll(List.of("list", "load", "save", "delete"));
                else if (args.length == 2) out.addAll(kits.names());
            }
            case "bot" -> {
                if (args.length == 1) out.addAll(List.of("spawn", "clear"));
            }
            default -> { return null; }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
                    }
