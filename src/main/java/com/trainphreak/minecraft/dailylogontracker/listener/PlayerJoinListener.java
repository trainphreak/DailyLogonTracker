package com.trainphreak.minecraft.dailylogontracker.listener;

import com.enjin.core.EnjinServices;
import com.enjin.rpc.mappings.services.PointService;
import com.trainphreak.minecraft.dailylogontracker.DailyLogonTrackerMain;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class PlayerJoinListener implements Listener
{
    private DailyLogonTrackerMain plugin;
    private Connection databaseConnection;
    private PointService enjinPointService = null;

    public PlayerJoinListener(final DailyLogonTrackerMain plugin, final Connection connection)
    {
        this.plugin = plugin;
        this.databaseConnection = connection;
        try
        {
            this.enjinPointService = EnjinServices.getService(PointService.class);
        }
        catch (Exception e)
        {
            plugin.log.warning("Enjin plugin is not installed. 'E:' commands will be ignored.");
            if (plugin.getConfig().getBoolean("debug", false))
            {
                plugin.log.info("Exception caught: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event)
    {
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Calendar todaysDate = Calendar.getInstance();
        todaysDate.clear(Calendar.HOUR_OF_DAY);
        todaysDate.clear(Calendar.MINUTE);
        todaysDate.clear(Calendar.SECOND);
        todaysDate.clear(Calendar.MILLISECOND);
        if (debug)
            plugin.log.info("Today: " + dateFormat.format(todaysDate.getTime()));

        if (debug)
            plugin.log.info("Player " + player.getName() + " (" + playerUUID.toString() + ") logged on");

        // Get player's last logon date from DB
        //   select lastlogon where uuid = uuid.toString
        //   if lastlogon != today, execute logon commands (give reward, messages, etc)
        //   change last logon date to current date
        Statement statement = null;
        try
        {
            statement = databaseConnection.createStatement();
            String sql = "SELECT * FROM DailyLogonTracker " +
                    "WHERE Uuid = '" + playerUUID.toString() + "';";
            ResultSet resultSet = statement.executeQuery(sql);

            UUID dbUUID = null;
            Calendar dbDate = null;

            while (resultSet.next())
            {
                if (debug)
                    plugin.log.info("UUID found in database");
                dbUUID = UUID.fromString(resultSet.getString("Uuid"));
                if (debug)
                    plugin.log.info(dbUUID.toString());

                String dbDateString = resultSet.getString("LastLogon");
                if (debug)
                    plugin.log.info("LastLogon: " + dbDateString);
                String[] datePieces = dbDateString.split("-");
                dbDate = Calendar.getInstance();
                dbDate.clear();
                int year = Integer.parseInt(datePieces[0]);
                int month = Integer.parseInt(datePieces[1]) - 1; // Magic; 0-11 is January-December
                int day = Integer.parseInt(datePieces[2]);
                dbDate.set(year,month,day);

                if (debug)
                    plugin.log.info("UUID: " + dbUUID.toString() + ", LastLogon: " + dateFormat.format(dbDate.getTime()));
            }

            if (dbUUID == null)
            {
                if (debug)
                    plugin.log.info("UUID not found in database");
                sql = "INSERT INTO DailyLogonTracker (Uuid,LastLogon) " +
                                    "VALUES ('" + playerUUID.toString() + "', '" + dateFormat.format(todaysDate.getTime()) + "');";
                statement.executeUpdate(sql);
                if (debug)
                    plugin.log.info("Added " + playerUUID.toString() + " to the database");
            }
            else
            {
                if (debug)
                {
                    plugin.log.info("Today " + todaysDate.get(Calendar.YEAR) + "-" + (todaysDate.get(Calendar.MONTH)+1) + "-" + todaysDate.get(Calendar.DATE));
                    plugin.log.info("DB " + dbDate.get(Calendar.YEAR) + "-" + (dbDate.get(Calendar.MONTH)+1) + "-" + dbDate.get(Calendar.DATE));
                }
                if (
                        (todaysDate.get(Calendar.YEAR) > dbDate.get(Calendar.YEAR)) ||
                        ((todaysDate.get(Calendar.YEAR) == dbDate.get(Calendar.YEAR)) && (todaysDate.get(Calendar.MONTH) > dbDate.get(Calendar.MONTH))) ||
                        ((todaysDate.get(Calendar.YEAR) == dbDate.get(Calendar.YEAR)) && (todaysDate.get(Calendar.MONTH) == dbDate.get(Calendar.MONTH)) && (todaysDate.get(Calendar.DATE) > dbDate.get(Calendar.DATE)))
                   )
                {
                    if (debug)
                    {
                        if (todaysDate.get(Calendar.YEAR) > dbDate.get(Calendar.YEAR))
                        {
                            plugin.log.info(todaysDate.get(Calendar.YEAR) + " > " + dbDate.get(Calendar.YEAR));
                        }
                        else if ((todaysDate.get(Calendar.YEAR) == dbDate.get(Calendar.YEAR)) && (todaysDate.get(Calendar.MONTH) > dbDate.get(Calendar.MONTH)))
                        {
                            plugin.log.info(todaysDate.get(Calendar.YEAR) + " = " + dbDate.get(Calendar.YEAR));
                            plugin.log.info(todaysDate.get(Calendar.MONTH) + " > " + dbDate.get(Calendar.MONTH));
                        }
                        else if ((todaysDate.get(Calendar.YEAR) == dbDate.get(Calendar.YEAR)) && (todaysDate.get(Calendar.MONTH) == dbDate.get(Calendar.MONTH)) && (todaysDate.get(Calendar.DATE) > dbDate.get(Calendar.DATE)))
                        {
                            plugin.log.info(todaysDate.get(Calendar.YEAR) + " = " + dbDate.get(Calendar.YEAR));
                            plugin.log.info(todaysDate.get(Calendar.MONTH) + " = " + dbDate.get(Calendar.MONTH));
                            plugin.log.info(todaysDate.get(Calendar.DATE) + " > " + dbDate.get(Calendar.DATE));
                        }
                        else
                        {
                            plugin.log.info(player.getName() + " already logged in today but they're getting a reward anyway. Tell trainphreak about this. Like now.");
                        }
                    }
                    plugin.log.info("Giving reward to " + player.getName());

                    // Run tasks for logon
                    List<String> statements = plugin.getConfig().getStringList("on-daily-logon");
                    for (String str : statements)
                    {
                        str = str.replace("%name%", player.getName());
                        if (str.charAt(0) == 'B')
                        {
                            if (debug)
                                plugin.log.info("Broadcasting: \"" + str.substring(2) + "\"");
                            sendBroadcast(str.substring(2));
                        }
                        else if (str.charAt(0) == 'C')
                        {
                            if (debug)
                                    plugin.log.info("Executing console command: \"" + str.substring(2) + "\"");
                                dispatchConsoleCommand(str.substring(2));
                        }
                        else if (str.charAt(0) == 'E')
                        {
                            int points = Integer.parseInt(str.substring(2));
                            if (enjinPointService != null)
                            {
                                enjinPointService.add(player.getName(), points);
                                if (debug)
                                    plugin.log.info("Giving " + points + " points to " + player.getName());
                            }
                            else
                            {
                                plugin.log.warning("Skipping giving " + points + " points to " + player.getName() + " because the Enjin plugin is not installed");
                            }
                        }
                        else if (str.charAt(0) == 'P')
                        {
                            if (debug)
                                plugin.log.info("Messaging " + player.getName() + ": \"" + str.substring(2) + "\"");
                            sendMessage(player, str.substring(2));
                        }
                        else
                        {
                            plugin.log.info("Invalid logon statement: " + str);
                        }
                    }

                    sql = "UPDATE DailyLogonTracker " +
                            "SET LastLogon = '" + dateFormat.format(todaysDate.getTime()) + "' " +
                            "WHERE Uuid = '" + dbUUID + "';";
                    statement.executeUpdate(sql);
                    if (debug)
                        plugin.log.info(player.getName() + "'s logon date updated to " + dateFormat.format(todaysDate.getTime()));
                }
                else
                {
                    if (debug)
                    {
                        plugin.log.info("Player " + player.getName() + " already logged in today");
                    }
                }
            }

            statement.close();
        }
        catch (Exception e)
        {
            try
            {
                if (statement != null)
                {
                    statement.cancel();
                    statement.close();
                }
            }
            catch (Exception e1)
            {
                if(debug)
                {
                    plugin.log.warning("Something broke...");
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendBroadcast(String broadcast)
    {
        this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new DelayedBroadcast(broadcast), 10L);
    }

    private void dispatchConsoleCommand(String command)
    {
        this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new DelayedConsoleCommand(command), 10L);
    }

    private void sendMessage(Player player, String message)
    {
        this.plugin.getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new DelayedMessage(player, message), 10L);
    }

    private void dispatchEnjinCommand(String command)
    {
        ServerCommandEvent stupidUnnecessaryServerCommandEvent = new ServerCommandEvent(Bukkit.getConsoleSender(), command);
        Bukkit.getServer().getPluginManager().callEvent(stupidUnnecessaryServerCommandEvent);
    }

    private class DelayedBroadcast implements Runnable
    {
        String broadcast;

        DelayedBroadcast(String broadcast)
        {
            this.broadcast = broadcast;
        }

        public void run()
        {
            Bukkit.broadcastMessage(broadcast);
        }
    }

    private class DelayedConsoleCommand implements Runnable
    {
        String command;

        DelayedConsoleCommand(String command)
        {
            this.command = command;
        }

        public void run()
        {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private class DelayedMessage implements Runnable
    {
        Player player;
        String message;

        DelayedMessage(Player player, String message)
        {
            this.player = player;
            this.message = message;
        }

        public void run()
        {
            player.sendMessage(message);
        }
    }
}
