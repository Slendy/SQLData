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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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

    private boolean _connected;

    private boolean _crash = false;

    private HashMap<UUID, Long> _loginTimes = new HashMap<>();

    private HashMap<UUID, Long> _lastOntime = new HashMap<>();

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
        doAsync(this::setupTable);
        for (Player p : Bukkit.getOnlinePlayers()) {
            _loginTimes.put(p.getUniqueId(), System.currentTimeMillis());
            doAsync(() -> {
                openConnection();
                try {
                    PreparedStatement ontime = _connection.prepareStatement("SELECT ontime FROM `data` WHERE uuid=?;");
                    ontime.setString(1, p.getUniqueId().toString().replace("-", ""));
                    ResultSet result = ontime.executeQuery();
                    result.next();
                    _lastOntime.put(p.getUniqueId(), result.getLong("ontime"));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static double trim(int degree, double d)
    {
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
    public void onDisable() {
        if(!_crash) {
            getLogger().info("Saving player data");
            openConnection();
            try {
                PreparedStatement ps = _connection.prepareStatement("UPDATE `data` SET ontime=? WHERE uuid=?");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ps.setLong(1, _lastOntime.get(p.getUniqueId()) + (System.currentTimeMillis() - _loginTimes.get(p.getUniqueId())));
                    ps.setString(2, p.getUniqueId().toString().replace("-", ""));
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeConnection();
            }

            try {
                if (_connection != null && !_connection.isClosed()) {
                    _connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void getPlayerStats(CommandSender sender, UUID u){
        doAsync(() -> {
            openConnection();
            try {
                PreparedStatement statement = _connection.prepareStatement("SELECT * FROM `data` WHERE uuid=?;");
                statement.setString(1, u.toString().replace("-", ""));

                ResultSet result = statement.executeQuery();
                result.next();
                sender.sendMessage("§7UUID: §f" + result.getString("uuid"));
                sender.sendMessage("§7Username: §f" + Bukkit.getOfflinePlayer(u).getName());
                sender.sendMessage("§7Kills: §f" + result.getInt("kills"));
                sender.sendMessage("§7Deaths: §f" + result.getInt("deaths"));
                sender.sendMessage("§7Ontime: §f" + convertString(result.getLong("ontime"), 1));
                sender.sendMessage("§eTIP> §fRelog to update your ontime.");

            } catch (SQLException e) {
                e.printStackTrace();
            }

        });
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage("§eFetching player data...");
                doAsync(() -> {
                    getPlayerStats(player, player.getUniqueId());
                });
            } else {
                sender.sendMessage("§cInvalid command usage! /info <player>");
            }
            return true;
        }
        if (args.length == 1) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(args[0]);

            sender.sendMessage("§eFetching player data...");

            doAsync(() -> {
                if(!dataContainsPlayer(p.getUniqueId())){
                    sender.sendMessage("§cThat player doesn't exist in the database.");
                } else {
                    getPlayerStats(sender, p.getUniqueId());
                }

            });

        }
        return true;
    }

    private synchronized void setupTable() {
        try {
            openConnection();
            PreparedStatement create = _connection.prepareStatement("CREATE TABLE IF NOT EXISTS `data` (\n" +
                    " `uuid` varchar(33) NOT NULL,\n" +
                    " `kills` int(11) NOT NULL,\n" +
                    " `deaths` int(11) NOT NULL,\n" +
                    " `ontime` bigint(20) NOT NULL\n" +
                    ") ENGINE=MyISAM DEFAULT CHARSET=latin1");
            create.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            getLogger().info("Database initiated");
        }
    }

    private synchronized void closeConnection() {
        try {
            _connection.close();
            _connected = false;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private synchronized void openConnection() {
        if (_connected) {
            return;
        }
        try {
            _connection = DriverManager.getConnection("jdbc:mysql://" + getConfig().get("database.ip") + ":3306/" + getConfig().getString("database.name"),
                    getConfig().getString("database.username"),
                    getConfig().getString("database.password"));
            _connected = true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private synchronized boolean dataContainsPlayer(UUID uuid) {
        openConnection();
        try {

            PreparedStatement ps = _connection.prepareStatement("SELECT * FROM `data` WHERE uuid=?;");
            ps.setString(1, uuid.toString().replace("-", ""));

            ResultSet resultSet = ps.executeQuery();
            boolean containsPlayer = resultSet.next();

            ps.close();
            resultSet.close();
            return containsPlayer;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        _loginTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        doAsync(() -> {
            try {
                openConnection();
                if (!dataContainsPlayer(event.getPlayer().getUniqueId())) {
                    PreparedStatement newPlayer = _connection.prepareStatement("INSERT INTO `data` values(?,0,0,0);");
                    newPlayer.setString(1, event.getPlayer().getUniqueId().toString().replace("-", ""));
                    newPlayer.execute();
                    newPlayer.close();
                } else {
                    PreparedStatement ontime = _connection.prepareStatement("SELECT ontime FROM `data` WHERE uuid=?;");
                    ontime.setString(1, event.getPlayer().getUniqueId().toString().replace("-", ""));
                    ResultSet result = ontime.executeQuery();
                    result.next();
                    _lastOntime.put(event.getPlayer().getUniqueId(), result.getLong("ontime"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if(_loginTimes.get(event.getPlayer().getUniqueId()) == null){
            return;
        }
        doAsync(() -> {
            _lastOntime.remove(event.getPlayer().getUniqueId());
            openConnection();
            try {
                long ontime = _loginTimes.get(event.getPlayer().getUniqueId());
                long timeSpent = System.currentTimeMillis() - ontime;
                System.out.println(timeSpent);
                PreparedStatement ps = _connection.prepareStatement("SELECT ontime FROM `data` WHERE uuid=?;");
                ps.setString(1, event.getPlayer().getUniqueId().toString().replace("-", ""));
                ResultSet result = ps.executeQuery();
                result.next();

                ontime = result.getLong("ontime");

                PreparedStatement onTimeUpdate = _connection.prepareStatement("UPDATE `data` SET ontime=? WHERE uuid=?;");
                onTimeUpdate.setLong(1, ontime + timeSpent);
                onTimeUpdate.setString(2, event.getPlayer().getUniqueId().toString().replace("-", ""));
                onTimeUpdate.executeUpdate();
                onTimeUpdate.close();
                ps.close();
                result.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler
    public void onKill(EntityDamageByEntityEvent e){
        if(e.getEntity() == null || e.getDamager() == null){
            return;
        }
        if(!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)){
            return;
        }
        if(((Player) e.getEntity()).getHealth() > 0 && ((Player) e.getEntity()).getHealth() - e.getDamage() > 0) {
            return;
        }

        doAsync(() -> {
            openConnection();
            try{
                PreparedStatement ps = _connection.prepareStatement("SELECT kills FROM `data` WHERE uuid=?;");
                ps.setString(1, e.getDamager().getUniqueId().toString().replace("-", ""));
                ResultSet result = ps.executeQuery();
                result.next();

                int previousKills = result.getInt("kills");

                PreparedStatement killsUpdate = _connection.prepareStatement("UPDATE `data` SET kills=? WHERE uuid=?;");
                killsUpdate.setInt(1, previousKills + 1);
                killsUpdate.setString(2, e.getDamager().getUniqueId().toString().replace("-", ""));
                killsUpdate.executeUpdate();

                ps.close();
                result.close();
                killsUpdate.close();
            }catch(Exception ex){
                ex.printStackTrace();
            }
        });

    }


    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (event.getEntity() != null && event.getEntity() instanceof Player) {
            doAsync(() -> {
                openConnection();
                try {
                    PreparedStatement ps = _connection.prepareStatement("SELECT deaths FROM `data` WHERE uuid=?;");
                    ps.setString(1, event.getEntity().getUniqueId().toString().replace("-", ""));
                    ResultSet result = ps.executeQuery();
                    result.next();

                    int previousDeaths = result.getInt("deaths");

                    PreparedStatement deathsUpdate = _connection.prepareStatement("UPDATE `data` SET deaths=? WHERE uuid=?;");
                    deathsUpdate.setInt(1, previousDeaths + 1);
                    deathsUpdate.setString(2, event.getEntity().getUniqueId().toString().replace("-", ""));
                    deathsUpdate.executeUpdate();

                    ps.close();
                    result.close();
                    deathsUpdate.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }


}