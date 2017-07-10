package me.slendy.sqldata;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ************************************************************************
 * Copyright Slendy (c) 2017. All Right Reserved.
 * Any code contained within this document, and any associated APIs with similar branding
 * are the sole property of Slendy. Distribution, reproduction, taking snippets, or
 * claiming any contents as your own will break the terms of the license, and void any
 * agreements with you, the third party.
 * Thanks
 * ************************************************************************
 */
public class SQLData extends JavaPlugin implements Listener, CommandExecutor {

    private Connection _connection;

    public Connection getConnection() {
        return _connection;
    }

    private boolean _crash = false;

    private List<SQLPlayer> _players = new ArrayList<>();

    @Override
    public void onEnable() {
        if (!(new File(getDataFolder(), "config.yml").exists())) {
            saveDefaultConfig();
            System.out.println("PLEASE CONFIGURE THE DATABASE IN CONFIG.YML");
            _crash = true;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getConfig().getString("database.ip").equalsIgnoreCase("") || getConfig().getString("database.name").equalsIgnoreCase("") || getConfig().getString("database.username").equalsIgnoreCase("") || getConfig().getString("database.password").equalsIgnoreCase("")) {
            System.out.println("PLEASE CONFIGURE THE DATABASE IN CONFIG.YML");
            _crash = true;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("info").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Initiating Database");
        doAsync(() -> {
            openConnection();
            setupTable();
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    PreparedStatement statement = _connection.prepareStatement("SELECT * FROM `data` WHERE uuid=?;");
                    statement.setString(1, p.getUniqueId().toString());
                    ResultSet result = statement.executeQuery();
                    if (result.next()) {//if player exists, just in case
                        _players.add(new SQLPlayer(p.getUniqueId(), System.currentTimeMillis(), result.getLong("ontime"), result.getInt("kills"), result.getInt("deaths")));
                    } else {
                        PreparedStatement newPlayer = _connection.prepareStatement("INSERT INTO `data` VALUES(?,0,0,0);");
                        newPlayer.setString(1, p.getUniqueId().toString());
                        _players.add(new SQLPlayer(p.getUniqueId(), System.currentTimeMillis(), 0, 0, 0));
                        newPlayer.execute();
                        newPlayer.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onDisable() {
        if (!_crash) {
            getLogger().info("Saving player data");
            try {
                PreparedStatement ps = _connection.prepareStatement("UPDATE `data` SET ontime=?, kills=?, deaths=? WHERE uuid=?");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    SQLPlayer player = getPlayer(p.getUniqueId());
                    ps.setLong(1, (System.currentTimeMillis() - player.getLoginTime()) + player.getPreviousOnTime());
                    ps.setInt(2, player.getKills());
                    ps.setInt(3, player.getDeaths());
                    ps.setString(4, p.getUniqueId().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static double trim(int degree, double d) {
        StringBuilder format = new StringBuilder("#.#");
        for (int i = 1; i < degree; i++) {
            format.append("#");
        }
        DecimalFormat twoDForm = new DecimalFormat(format.toString());
        return Double.parseDouble(twoDForm.format(d));
    }

    private static String convertString(long time, int trim) {
        if (time < 60000L) {
            return trim(trim, time / 1000.0D) + " Seconds";
        } else if (time < 3600000L) {
            return trim(trim, time / 60000.0D) + " Minutes";
        } else if (time < 86400000L) {
            return trim(trim, time / 3600000.0D) + " Hours";
        } else {
            return trim(trim, time / 86400000.0D) + " Days";
        }
    }

    private void doAsync(Runnable r) {
        Bukkit.getScheduler().runTaskAsynchronously(this, r);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                SQLPlayer player = getPlayer(((Player) sender).getUniqueId());
                if (player == null) {
                    sender.sendMessage("§cAn error has occurred.");
                    return true;
                }
                sender.sendMessage("§ePlayer data for " + sender.getName());
                sender.sendMessage("§7UUID: §f" + player.getUuid());
                sender.sendMessage("§7Kills: §f" + player.getKills());
                sender.sendMessage("§7Deaths: §f" + player.getDeaths());
                sender.sendMessage("§7Ontime: §f" + convertString(player.getPreviousOnTime() + (System.currentTimeMillis() - player.getLoginTime()), 1));
            } else {
                sender.sendMessage("§cInvalid command usage! /info <player>");
            }
            return true;
        }
        if (args.length == 1) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(args[0]);
            SQLPlayer player = getPlayer(p.getUniqueId());
            if (player == null) {
                sender.sendMessage("§eFetching player data...");
                doAsync(() -> {


                    try {
                        PreparedStatement statement = _connection.prepareStatement("SELECT * FROM `data` WHERE uuid=?;");
                        statement.setString(1, p.getUniqueId().toString());
                        ResultSet result = statement.executeQuery();
                        if (result.next()) {
                            sender.sendMessage("§ePlayer data for " + p.getName());
                            sender.sendMessage("§7UUID: §f" + result.getString("uuid"));
                            sender.sendMessage("§7Kills: §f" + result.getInt("kills"));
                            sender.sendMessage("§7Deaths: §f" + result.getInt("deaths"));
                            sender.sendMessage("§7Ontime: §f" + convertString(result.getLong("ontime"), 1));
                        } else {
                            sender.sendMessage("§cThat player doesn't exist in the database.");
                            return;
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                });
            } else {
                sender.sendMessage("§ePlayer data for " + p.getName());
                sender.sendMessage("§7UUID: §f" + player.getUuid());
                sender.sendMessage("§7Kills: §f" + player.getKills());
                sender.sendMessage("§7Deaths: §f" + player.getDeaths());
                sender.sendMessage("§7Ontime: §f" + convertString(player.getPreviousOnTime() + (System.currentTimeMillis() - player.getLoginTime()), 1));
            }
        }
        return true;
    }

    private synchronized void setupTable() {
        try {
            PreparedStatement create = _connection.prepareStatement("CREATE TABLE IF NOT EXISTS `data` (\n" +
                    " `uuid` VARCHAR(37) NOT NULL,\n" +
                    " `kills` INT(11) NOT NULL,\n" +
                    " `deaths` INT(11) NOT NULL,\n" +
                    " `ontime` BIGINT(20) NOT NULL\n" +
                    ") ENGINE=MyISAM DEFAULT CHARSET=latin1");
            create.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private synchronized void closeConnection() {
        try {
            _connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private synchronized void openConnection() {
        try {
            _connection = DriverManager.getConnection("jdbc:mysql://" + getConfig().getString("database.ip") + ":3306/" + getConfig().getString("database.name")
                    + "?user=" + getConfig().getString("database.username")
                    + "&password=" + getConfig().getString("database.password")
                    + "&autoReconnect=true");
        } catch (Exception e) {
            _crash = true;
            getServer().getPluginManager().disablePlugin(this);
            getLogger().info("Could not establish connection to database.");
//            e.printStackTrace();
        }
    }

    public SQLPlayer getPlayer(UUID u) {
        for (SQLPlayer p : _players) {
            if (p.getUuid() == u) {
                return p;
            }
        }
        return null;
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        doAsync(() -> {
            try {
                if (_connection == null || _connection.isClosed()) {
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                    event.setKickMessage("§cThe server is still starting up\n §rplease join back in a second.");
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cThe server is still starting up\n §rplease join back in a second.");
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                PreparedStatement statement = _connection.prepareStatement("SELECT * FROM `data` WHERE uuid=?;");
                statement.setString(1, event.getUniqueId().toString());
                ResultSet result = statement.executeQuery();
                boolean playerExists = result.next();
                if (playerExists) {
                    _players.add(new SQLPlayer(event.getUniqueId(), System.currentTimeMillis(), result.getLong("ontime"), result.getInt("kills"), result.getInt("deaths")));
                    result.close();
                    statement.close();
                } else {
                    PreparedStatement newPlayer = _connection.prepareStatement("INSERT INTO `data` VALUES(?,0,0,0);");
                    newPlayer.setString(1, event.getUniqueId().toString());
                    newPlayer.execute();
                    newPlayer.close();
                    _players.add(new SQLPlayer(event.getUniqueId(), System.currentTimeMillis(), 0, 0, 0));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SQLPlayer player = getPlayer(event.getPlayer().getUniqueId());
        doAsync(() -> {
            try {
                if (_connection == null || _connection.isClosed()) {
                    return;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                PreparedStatement statement = _connection.prepareStatement("UPDATE `data` SET ontime=?, kills=?, deaths=? WHERE uuid=?;");
                statement.setLong(1, (System.currentTimeMillis() - player.getLoginTime()) + player.getPreviousOnTime());
                statement.setInt(2, player.getKills());
                statement.setInt(3, player.getDeaths());
                statement.setString(4, player.getUuid().toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onKill(EntityDamageByEntityEvent event) {
        if (event.getEntity() == null || event.getDamager() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        if (((Player) event.getEntity()).getHealth() > 0 && ((Player) event.getEntity()).getHealth() - event.getDamage() > 0) {
            return;
        }
        getPlayer(event.getEntity().getUniqueId()).incrementKills();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity() != null && event.getEntity() instanceof Player) {
            getPlayer(event.getEntity().getUniqueId()).incrementDeaths();
        }
    }

}