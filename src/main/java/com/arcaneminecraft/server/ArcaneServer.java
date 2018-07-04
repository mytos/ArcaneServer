package com.arcaneminecraft.server;

import com.arcaneminecraft.api.ArcaneText;
import com.arcaneminecraft.api.ArcaneColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class ArcaneServer extends JavaPlugin {
    private ArcAFK arcAFK;
    private PluginMessenger pluginMessenger;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // BungeeCord plugin message stuff
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        this.pluginMessenger = new PluginMessenger(this); // Above must come before this.
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", pluginMessenger);
        getServer().getPluginManager().registerEvents(pluginMessenger, this);

        // X-Ray and other notification alert
        getServer().getMessenger().registerOutgoingPluginChannel(this, "ArcaneAlert");
        getServer().getPluginManager().registerEvents(new Alert(this), this);

        // General stuff
        getServer().getPluginManager().registerEvents(new PlayerEvents(this), this);
        getServer().getPluginManager().registerEvents(new Greylist(), this);
        // this must come before AFK
        getServer().getPluginManager().registerEvents(new PlayerListRole(this), this);

        // Events tied to command
        LocalChat lc = new LocalChat(this);
        getCommand("local").setExecutor(lc);
        getCommand("localtoggle").setExecutor(lc);
        getCommand("localrange").setExecutor(lc);
        getCommand("global").setExecutor(lc);
        getServer().getPluginManager().registerEvents(lc, this);

        HelpCommand hc = new HelpCommand(this);
        getCommand("help").setExecutor(hc);
        getServer().getPluginManager().registerEvents(hc, this);

        this.arcAFK = new ArcAFK(this);
        getCommand("afk").setExecutor(arcAFK);
        getServer().getPluginManager().registerEvents(arcAFK, this);
    }

    @Override
    public void onDisable() {
        arcAFK.onDisable();
        // List roles
        for (Player p : getServer().getOnlinePlayers()) {
            p.setPlayerListName(p.getName());
        }
    }

    PluginMessenger getPluginMessenger() {
        return pluginMessenger;
    }

    ArcAFK getArcAFK() {
        return arcAFK;
    }

    // TODO: Implement TabCompleter (or TabExecutor)
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("kill")) {
            if (args.length == 0) {
                if (sender instanceof Damageable)
                    ((Damageable) sender).setHealth(0);
                else
                    sender.sendMessage("You must be a damageable entity");
                return true;
            }
            // For selected kill to go through, players will need Minecraft's kill permission in the end.
            if (sender instanceof Player) ((Player) sender).performCommand("minecraft:kill " + String.join(" ", args));
            if (sender instanceof ConsoleCommandSender)
                getServer().dispatchCommand(getServer().getConsoleSender(), "minecraft:kill " + String.join(" ", args));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("uuid")) {
            if (!sender.hasPermission("arcane.command.uuid")) {
                if (sender instanceof Player)
                    ((Player) sender).spigot().sendMessage(ChatMessageType.SYSTEM, ArcaneText.noPermissionMsg());
                else
                    sender.spigot().sendMessage(ArcaneText.noPermissionMsg());
                return true;
            }

            if (args.length == 0) {
                if (sender instanceof Player)
                    ((Player) sender).spigot().sendMessage(ChatMessageType.SYSTEM, ArcaneText.usage(cmd.getUsage()));
                else
                    sender.spigot().sendMessage(ArcaneText.usage(cmd.getUsage()));
                return true;
            }

            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                // OfflinePlayer can hold the thread: run as async.

                @SuppressWarnings("deprecation")
                OfflinePlayer pl = getServer().getOfflinePlayer(args[0]);

                String uuid = pl.getUniqueId().toString();

                BaseComponent send = new TextComponent(pl.getName());
                send.addExtra("'s UUID: ");
                send.addExtra(uuid);

                send.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuid));

                if (sender instanceof Player)
                    ((Player) sender).spigot().sendMessage(ChatMessageType.SYSTEM, send);
                else
                    sender.spigot().sendMessage(send);
            });

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("opme")) {
            if (!(sender instanceof Player)) {
                sender.spigot().sendMessage(ArcaneText.noConsoleMsg());
                return true;
            }
            Player p = (Player) sender;

            if (p.hasPermission("arcane.command.opme")) {
                p.setOp(true);
                p.spigot().sendMessage(ChatMessageType.SYSTEM, new TranslatableComponent("commands.op.success", p.getName()));
            } else {
                p.spigot().sendMessage(ChatMessageType.SYSTEM, ArcaneText.noPermissionMsg());
            }
            return true;
        }

        // Useful username command
        // "very useful i give a perfect 5/7" -Simon, 2016
        // "this is too good to remove" -Simon, 2018
        if (cmd.getName().equalsIgnoreCase("username")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("You'll always be named Server.");
                return true;
            }

            String name = ((Player) sender).getDisplayName();

            Random randy = new Random();

            String[] list = {
                    "It looks like your username is " + name + ".",
                    "Your username is " + name + ".",
                    "Your username is not Agentred100.",
                    "Username: " + name + ".",
                    ArcaneColor.NEGATIVE + "[Username] " + ArcaneColor.CONTENT + name + ".",
                    ArcaneColor.HEADING + "[Username]" + ArcaneColor.CONTENT + " At the moment, your username is " + name + ".",
                    ArcaneColor.HEADING + "YOUR USERNAME IS " + ArcaneColor.NEGATIVE + name + ".",
                    name
            };

            String r = list[randy.nextInt(list.length)];

            sender.sendMessage(ArcaneColor.CONTENT + r);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ((command.getName().equalsIgnoreCase("kill") && sender.hasPermission("minecraft.command.kill"))
                || command.getName().equalsIgnoreCase("uuid"))
            return null;

        return Collections.emptyList();
    }
}
