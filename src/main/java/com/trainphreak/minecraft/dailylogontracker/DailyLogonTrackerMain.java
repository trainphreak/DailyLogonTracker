package com.trainphreak.minecraft.dailylogontracker;

import com.trainphreak.minecraft.dailylogontracker.listener.PlayerJoinListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.Logger;

public class DailyLogonTrackerMain extends JavaPlugin
{
    private static DailyLogonTrackerMain plugin;
    public Logger log;
    public final String prefix = "[DailyLogonTracker] ";
    private PlayerJoinListener joinListener;
    private Connection databaseConnection;

    public void onEnable()
    {
        DailyLogonTrackerMain.plugin = this;
        this.log = Logger.getLogger("Minecraft");
        this.saveDefaultConfig();
        // open DB
        try
        {
            Class.forName("org.sqlite.JDBC");
            databaseConnection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/playerlogons.db");
        }
        catch (Exception e)
        {
            log.warning("Error connecting to database.");
            e.printStackTrace();
            databaseConnection = null;
            this.setEnabled(false);
            return;
        }
        // if table doesn't exist, create table
        try
        {
            Statement statement = databaseConnection.createStatement();
            String query = "CREATE TABLE IF NOT EXISTS DailyLogonTracker " +
                           "(Uuid      TEXT     PRIMARY KEY     NOT NULL," +
                           " LastLogon TEXT                     NOT NULL);";
            statement.executeUpdate(query);
            statement.close();
        }
        catch (Exception e)
        {
            log.warning("Error creating/checking for table.");
            e.printStackTrace();
            try
            {
                if (databaseConnection != null)
                {
                    databaseConnection.close();
                    databaseConnection = null;
                }
            }
            catch (Exception e1)
            {
                // Already quitting
            }
            this.setEnabled(false);
            return;
        }
        // pass DB connection to listener
        joinListener = new PlayerJoinListener(this, databaseConnection);
        this.getServer().getPluginManager().registerEvents(joinListener, this);
    }

    public void onDisable()
    {
        try
        {
            if (databaseConnection != null)
                databaseConnection.close();
        }
        catch (Exception e)
        {
            log.warning("Error closing the database.");
            e.printStackTrace();
        }
        finally
        {
            HandlerList.unregisterAll(joinListener);
        }
    }
}
