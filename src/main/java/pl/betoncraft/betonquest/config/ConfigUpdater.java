/**
 * BetonQuest - advanced quests for Bukkit
 * Copyright (C) 2015  Jakub "Co0sh" Sapalski
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pl.betoncraft.betonquest.config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.core.QuestItem;
import pl.betoncraft.betonquest.database.Connector;
import pl.betoncraft.betonquest.database.Connector.QueryType;
import pl.betoncraft.betonquest.database.Connector.UpdateType;
import pl.betoncraft.betonquest.database.Database;
import pl.betoncraft.betonquest.utils.Debug;
import pl.betoncraft.betonquest.utils.Utils;

/**
 * Updates configuration files to the newest version.
 * 
 * @author Co0sh
 */
public class ConfigUpdater {
    
    /**
     * Error which should be displayed to the player when 
     */
    private final String ERROR = "There was an error during updating process! Please "
            + "downgrade to the previous working version of the plugin and restore your "
            + "configuration from the backup. Don't forget to send this error to the developer"
            + ", so he can fix it! Sorry for inconvenience, here's the link:"
            + " <https://github.com/Co0sh/BetonQuest/issues> and a cookie: "
            + "<http://i.imgur.com/iR4UMH5.png>";

    /**
     * BetonQuest's instance
     */
    private BetonQuest instance = BetonQuest.getInstance();
    /**
     * Main configuration instance
     */
    private FileConfiguration config = instance.getConfig();
    /**
     * Destination version. At the end of the updating process this will be the
     * current version
     */
    private final String destination = "v13";
    /**
     * Deprecated ConfigHandler, used fo updating older configuration files
     */
    private ConfigHandler ch;

    public ConfigUpdater() {
        String version = BetonQuest.getInstance().getConfig().getString("version", null);
        Debug.info("Initializing updater with version " + version + ", destination is "
            + destination);
        // when the config is up to date then check for pending names
        // conversion;
        // conversion will occur only if UUID is manually set to true
        if (config.getString("uuid") != null && config.getString("uuid").equals("true")
            && config.getString("convert") != null && config.getString("convert").equals("true")) {
            convertNamesToUUID();
            config.set("convert", null);
            instance.saveConfig();
        }
        // move backup files to backup folder
        for (File file : instance.getDataFolder().listFiles()) {
            if (file.getName().matches("^backup-.*\\.zip$")) {
                file.renameTo(new File(file.getParentFile().getAbsolutePath() + File.separator
                    + "backups" + File.separator + file.getName()));
                Debug.broadcast("File " + file.getName() + " moved to backup folder!");
            }
        }
        if (version != null && version.equals(destination)) {
            Debug.broadcast("Configuration up to date!");
            return;
        } else {
            Utils.backup();
        }
        // instanitiate old configuration handler
        ch = new ConfigHandler();
        // if the version is null the plugin is updated from pre-1.3 version
        // (which can be 1.0, 1.1 or 1.2)
        if (version == null) {
            updateTo1_3();
        } else if (version.equals("1.3")) {
            updateTo1_4();
        } else if (version.equals("1.4")) {
            updateTo1_4_1();
        } else if (version.equals("1.4.1")) {
            updateTo1_4_2();
        } else if (version.equals("1.4.2")) {
            updateTo1_4_3();
        } else if (version.equals("1.4.3")) {
            updateTo1_5();
        } else if (version.equals("1.5")) {
            updateTo1_5_1();
        } else if (version.equals("1.5.1")) {
            updateTo1_5_2();
        } else if (version.equals("1.5.2")) {
            updateTo1_5_3();
        } else if (version.equals("1.5.3") || version.equals("1.5.4")|| version.equals("1.6")) {
            updateTo1_6();
        } else if (version.matches("^v\\d+$")) {
            performUpdate();
        } else {
            Debug.broadcast("Something is not right wit configuration version. Consider fixing this.");
        }
    }
    
    /**
     * Performes full update in new updating system.
     */
    private void performUpdate() {
        // this is new, post-1.5.3 updating system, where config versions
        // are numbered separately from plugin's releases
        Debug.broadcast("Updating configuration to version " + destination);
        update();
        updateLanguages();
        instance.saveConfig();
        // reload configuration file to apply all possible changes
        new Config();
        Debug.broadcast("Successfully updated configuration!");
        addChangelog();
    }

    /**
     * Invokes method that updates config from current version to the next. It
     * repeats itself until everything is converted.
     */
    private void update() {
        String version = config.getString("version", null);
        // if the version is the same as destination, updating process is
        // finished
        if (version == null || version.equals(destination))
            return;
        try {
            // call the right updating method
            Method method = this.getClass().getDeclaredMethod("update_from_" + version);
            method.setAccessible(true);
            Debug.info("Starting update from " + version + "!");
            method.invoke(this);
            Debug.info("Update to " + config.getString("version") + " done!");
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        // update again until destination is reached
        update();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v12() {
        try {
            Debug.info("Moving all configuration to \"default\" package");
            // clear the default package, which contains only default quest
            File defPkg = Config.getPackage("default").getFolder();
            Debug.info("  Deleting default files");
            for (File file : defPkg.listFiles()) {
                file.delete();
            }
            // move files that can be moved without modifications
            File root = instance.getDataFolder();
            String[] filesToMove = new String[]{"events", "conditions", "items", "journal"};
            for (String fileToMove : filesToMove) {
                Debug.info("  Moving " + fileToMove + ".yml file");
                new File(root, fileToMove + ".yml").renameTo(new File(defPkg, fileToMove + ".yml"));
            }
            // move all conversations
            File newConversationFolder = new File(defPkg, "conversations");
            File oldConversationFolder = new File(root, "conversations");
            newConversationFolder.mkdir();
            for (File conversation : oldConversationFolder.listFiles()) {
                Debug.info("  Moving " + conversation.getName() + " conversation file");
                conversation.renameTo(new File(newConversationFolder, conversation.getName()));
            }
            // generate main.yml file
            Debug.info("  Generating main.yml file");
            File mainFile = new File(defPkg, "main.yml");
            mainFile.createNewFile();
            FileConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
            // copy the data
            String globalLocations = config.getString("global_locations");
            ConfigurationSection staticEvents = config.getConfigurationSection("static");
            ConfigurationSection npcs = ch.getConfigs().get("npcs").getConfig().getRoot();
            main.set("global_locations", globalLocations);
            if (staticEvents != null) {
                for (String key : staticEvents.getKeys(false)) {
                    main.set("static." + key, staticEvents.getString(key));
                }
            }
            if (npcs != null) {
                for (String key : npcs.getKeys(false)) {
                    main.set("npcs." + key, npcs.getString(key));
                }
                for (File conv : newConversationFolder.listFiles()) {
                    main.set("npcs." + conv.getName().replace(".yml", ""), conv.getName().replace(".yml", ""));
                }
            }
            main.save(mainFile);
            // remove old values from configuration
            Debug.info("  Removing old files and config values");
            oldConversationFolder.delete();
            config.set("global_locations", null);
            config.set("static", null);
            new File(root, "npcs.yml").delete();
            Debug.info("Configuration updated!");
            Debug.broadcast("Updating the database, it may take a long time!");
            Connection con = instance.getDB().openConnection();
            String prefix = instance.getConfig().getString("mysql.prefix", "");
            ResultSet res = con.createStatement().executeQuery("SELECT * FROM " + prefix + "objectives");
            ArrayList<String[]> objectives = new ArrayList<>();
            // iterate over every objective string in the database
            while (res.next()) {
                String[] parts = res.getString("instructions").split(" ");
                StringBuilder newInstruction = new StringBuilder();
                for (String part : parts) {
                    if (part.startsWith("events:")) {
                        newInstruction.append("events:");
                        String[] events = part.substring(7).split(",");
                        for (String event : events) {
                            newInstruction.append("default." + event + ",");
                        }
                        newInstruction.deleteCharAt(newInstruction.length() - 1);
                    } else if (part.startsWith("conditions:")) {
                        newInstruction.append("conditions:");
                        String[] conditions = part.substring(11).split(",");
                        for (String condition : conditions) {
                            newInstruction.append("default." + condition + ",");
                        }
                        newInstruction.deleteCharAt(newInstruction.length() - 1);
                    } else {
                        newInstruction.append(part);
                    }
                    newInstruction.append(' ');
                }
                objectives.add(new String[]{res.getString("playerID"), newInstruction.toString().trim()});
            }
            res = con.createStatement().executeQuery("SELECT * FROM " + prefix + "journal");
            ArrayList<String[]> pointers = new ArrayList<>();
            // iterate over every journal pointer in the database
            while (res.next()) {
                pointers.add(new String[]{res.getString("playerID"), "default."
                        + res.getString("pointer"), res.getString("date")});
            }
            con.createStatement().executeUpdate("DELETE FROM " + prefix + "objectives");
            con.createStatement().executeUpdate("DELETE FROM " + prefix + "journal");
            for (String[] objective : objectives) {
                PreparedStatement stmt = con.prepareStatement("INSERT INTO " + prefix + "objectives "
                        + "(playerID, instructions) VALUES (?,?)");
                stmt.setString(1, objective[0]);
                stmt.setString(2, objective[1]);
                stmt.executeUpdate();
            }
            for (String[] pointer : pointers) {
                PreparedStatement stmt = con.prepareStatement("INSERT INTO " + prefix + "journal "
                        + "(playerID, pointer, date) VALUES (?,?,?)");
                stmt.setString(1, pointer[0]);
                stmt.setString(2, pointer[1]);
                stmt.setString(3, pointer[2]);
                stmt.executeUpdate();
            }
            instance.getDB().closeConnection(con);
            Debug.info("Done! Everything converted.");
        } catch (Exception e) {
            e.printStackTrace();
            Debug.error(ERROR);
        }
        Debug.broadcast("Introduced new packaging system and moved configuration to \"default\" package!");
        config.set("version", "v13");
        instance.saveConfig();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v11() {
        try {
            Debug.info("Updating objectives in configuration");
            ConfigAccessor events = ch.getConfigs().get("events");
            ArrayList<String> labels = new ArrayList<>();
            boolean notified = false;
            // for every event check if it's objective
            for (String key : events.getConfig().getKeys(false)) {
                String value = events.getConfig().getString(key);
                if (value.startsWith("objective ")) {
                    Debug.info("  Found " + key + " objective event");
                    // replace "tag:" with "label:" in all found objectives
                    String[] parts = value.split(" ");
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].startsWith("tag:")) {
                            String label = parts[i].substring(4);
                            if (!notified && labels.contains(label)) {
                                notified = true;
                                Debug.error("You have multiple objectives with the same label!"
                                    + " That is an error, because the player cannot have"
                                    + " active more than one objective with the same label");
                            }
                            labels.add(label);
                            parts[i] = "label:" + label;
                        }
                        builder.append(parts[i]);
                        builder.append(" ");
                    }
                    String newValue = builder.toString().trim();
                    Debug.info("    After processing: " + newValue);
                    events.getConfig().set(key, newValue);
                }
            }
            events.saveConfig();
            Debug.info("Converted all objectives in configuration");
            // update all objectives in the database
            Debug.broadcast("Converting objectives in the database, it may take a long time");
            Connection con = instance.getDB().openConnection();
            String prefix = instance.getConfig().getString("mysql.prefix", "");
            ResultSet res = con.createStatement().executeQuery("SELECT * FROM " + prefix
                    + "objectives");
            HashMap<String,ArrayList<String>> objectives = new HashMap<>();
            HashMap<String,ArrayList<String>> labels2 = new HashMap<>();
            // iterate over every objective string in the database
            while (res.next()) {
                String playerID = res.getString("playerID");
                String objective = res.getString("instructions");
                String label = null;
                for (String part : objective.split(" ")) {
                    if (part.startsWith("tag:")) {
                        label = part.substring(4);
                    }
                }
                if (label == null) {
                    Debug.info("  Found objective without a label, that's strange... Anyway,"
                        + " skipping. Player: " + playerID);
                    continue;
                }
                Debug.info("  Found objective for player " + playerID + " with label " + label);
                ArrayList<String> oList = objectives.get(playerID);
                ArrayList<String> lList = labels2.get(playerID);
                if (oList == null) {
                    oList = new ArrayList<>();
                    lList = new ArrayList<>();
                }
                // cannot have two objectives with the same tag
                if (lList.contains(label)) {
                    Debug.info("    Label already exists, skipping this one!");
                    continue;
                }
                String converted = convertObjective(objective);
                Debug.info("    Objective converted: " + converted);
                oList.add(converted);
                lList.add(label);
                objectives.put(playerID, oList);
                labels2.put(playerID, lList);
            }
            // everything is extracted from the database and converted
            // time to put it back
            Debug.info("Inserting everything into the database...");
            con.createStatement().executeUpdate("DELETE FROM " + prefix + "objectives");
            for (String playerID : objectives.keySet()) {
                for (String objective : objectives.get(playerID)) {
                    PreparedStatement stmt = con.prepareStatement("INSERT INTO " + prefix
                            + "objectives (playerID, instructions) VALUES (?,?);");
                    stmt.setString(1, playerID);
                    stmt.setString(2, objective);
                    stmt.executeUpdate();
                }
            }
            instance.getDB().closeConnection(con);
            Debug.info("Done! Everything converted");
        } catch (Exception e) {
            e.printStackTrace();
            Debug.error(ERROR);
        }
        Debug.broadcast("Changed keyword \"tag:\" to \"label:\" in all objectives!");
        config.set("version", "v12");
        instance.saveConfig();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v10() {
        try {
            Debug.info("Updating instruction strings");
            Debug.info("  Updating conditions");
            ConfigAccessor conditions = ch.getConfigs().get("conditions");
            conditions:
            for (String key : conditions.getConfig().getKeys(false)) {
                Debug.info("    Processing " + key + " condition");
                String instruction = conditions.getConfig().getString(key).trim();
                String[] parts = instruction.split(" ");
                String type = parts[0].toLowerCase();
                ArrayList<String> newParts = new ArrayList<>();
                newParts.add(type);
                switch (type) {
                    case "hand":
                        Debug.info("      Found hand type");
                        String item = null;
                        for (String part : parts) {
                            if (part.startsWith("item:")) {
                                item = part.substring(5);
                            }
                        }
                        if (item != null) {
                            newParts.add(item);
                        } else {
                            Debug.info("      There is no item defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "or":
                    case "and":
                        Debug.info("      Found or/and type");
                        String orAndConditions = null;
                        for (String part : parts) {
                            if (part.startsWith("conditions:")) {
                                orAndConditions = part.substring(11);
                            }
                        }
                        if (orAndConditions != null) {
                            newParts.add(orAndConditions);
                        } else {
                            Debug.info("      There are no conditions defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "location":
                        Debug.info("      Found location type");
                        String location = null;
                        for (String part : parts) {
                            if (part.startsWith("loc:")) {
                                location = part.substring(4);
                            }
                        }
                        if (location != null) {
                            newParts.add(location);
                        } else {
                            Debug.info("      There is no location defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "health":
                        Debug.info("      Found health type");
                        String health = null;
                        for (String part : parts) {
                            if (part.startsWith("health:")) {
                                health = part.substring(7);
                            }
                        }
                        if (health != null) {
                            newParts.add(health);
                        } else {
                            Debug.info("      There is no health amount defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "experience":
                        Debug.info("      Found experience type");
                        String exp = null;
                        for (String part : parts) {
                            if (part.startsWith("exp:")) {
                                exp = part.substring(4);
                            }
                        }
                        if (exp != null) {
                            newParts.add(exp);
                        } else {
                            Debug.info("      There is no experience level defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "permission":
                        Debug.info("      Found permission type");
                        String perm = null;
                        for (String part : parts) {
                            if (part.contains("perm:")) {
                                perm = part.substring(5);
                            }
                        }
                        if (perm != null) {
                            newParts.add(perm);
                        } else {
                            Debug.info("      There is no permission defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "point":
                        Debug.info("      Found point type");
                        String category = null;
                        String amount = null;
                        for (String part : parts) {
                            if (part.startsWith("category:")) {
                                category = part.substring(9);
                            } else if (part.startsWith("count:")) {
                                amount = part.substring(6);
                            }
                        }
                        if (category != null && amount != null) {
                            newParts.add(category);
                            newParts.add(amount);
                        } else {
                            Debug.info("      There is no category/amount defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "tag":
                        Debug.info("      Found tag type");
                        String tag = null;
                        for (String part : parts) {
                            if (part.startsWith("tag:")) {
                                tag = part.substring(4);
                            }
                        }
                        if (tag != null) {
                            newParts.add(tag);
                        } else {
                            Debug.info("      There is no tag defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "armor":
                        Debug.info("      Found armor type");
                        String material = null;
                        String armorType = null;
                        String enchants = null;
                        for (String part : parts) {
                            if (part.startsWith("material:")) {
                                material = part.substring(9);
                            }
                            if (part.startsWith("type:")) {
                                armorType = part.substring(5);
                            }
                            if (part.startsWith("enchants:")) {
                                enchants = part;
                            }
                        }
                        if (material != null && type != null) {
                            Material armor = null;
                            try {
                                armor = Material.matchMaterial(material + "_" + armorType);
                            } catch (Exception e) {
                                Debug.info("      Could not read armor type, skipping");
                                continue conditions;
                            }
                            String itemInstruction = armor.toString();
                            if (enchants != null) {
                                itemInstruction = itemInstruction + " " + enchants;
                            }
                            ConfigAccessor itemsConfig = ch.getConfigs().get("items");
                            int i = 0;
                            while (itemsConfig.getConfig().contains("armor" + i)) {
                                i++;
                            }
                            itemsConfig.getConfig().set("armor" + i, itemInstruction);
                            itemsConfig.saveConfig();
                            newParts.add("armor" + i);
                        } else {
                            Debug.info("      There is no armor defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "effect":
                        Debug.info("      Found effect type");
                        String effect = null;
                        for (String part : parts) {
                            if (part.startsWith("type:")) {
                                effect = part.substring(5);
                            }
                        }
                        if (effect != null) {
                            newParts.add(effect);
                        } else {
                            Debug.info("      There is no effect defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "time":
                        Debug.info("      Found time type");
                        String time = null;
                        for (String part : parts) {
                            if (part.startsWith("time:")) {
                                time = part.substring(5);
                            }
                        }
                        if (time != null) {
                            newParts.add(time);
                        } else {
                            Debug.info("      There is no time defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "weather":
                        Debug.info("      Found weather type");
                        String weather = null;
                        for (String part : parts) {
                            if (part.startsWith("type:")) {
                                weather = part.substring(5);
                            }
                        }
                        if (weather != null) {
                            newParts.add(weather);
                        } else {
                            Debug.info("      There is no weather defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "height":
                        Debug.info("      Found height type");
                        String height = null;
                        for (String part : parts) {
                            if (part.startsWith("height:")) {
                                height = part.substring(7);
                            }
                        }
                        if (height != null) {
                            newParts.add(height);
                        } else {
                            Debug.info("      There is no height defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "rating":
                        Debug.info("      Found rating type");
                        String rating = null;
                        for (String part : parts) {
                            if (part.startsWith("rating:")) {
                                rating = part.substring(7);
                            }
                        }
                        if (rating != null) {
                            newParts.add(rating);
                        } else {
                            Debug.info("      There is no rating defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "random":
                        Debug.info("      Found random type");
                        String random = null;
                        for (String part : parts) {
                            if (part.startsWith("random:")) {
                                random = part.substring(7);
                            }
                        }
                        if (random != null) {
                            newParts.add(random);
                        } else {
                            Debug.info("      There is no random defined, skipping");
                            continue conditions;
                        }
                        break;
                    case "money":
                        Debug.info("      Found money type");
                        String money = null;
                        for (String part : parts) {
                            if (part.startsWith("money:")) {
                                money = part.substring(6);
                            }
                        }
                        if (money != null) {
                            newParts.add(money);
                        } else {
                            Debug.info("      There is no amount defined, skipping");
                            continue conditions;
                        }
                        break;
                    default:
                        Debug.info("      This one does not need updating");
                        continue conditions;
                }
                StringBuilder builder = new StringBuilder();
                for (String part : newParts) {
                    builder.append(part);
                    builder.append(' ');
                }
                String newInstruction = builder.toString().trim();
                Debug.info("      Processing done, instruction: '" + newInstruction + "'");
                conditions.getConfig().set(key, newInstruction);
            }
            Debug.info("  All conditions updated successfully, saving to the file");
            conditions.saveConfig();
            
            Debug.info("  Updating events");
            ConfigAccessor events = ch.getConfigs().get("events");
            events:
            for (String key : events.getConfig().getKeys(false)) {
                Debug.info("    Processing " + key + " event");
                String instruction = events.getConfig().getString(key).trim();
                String[] parts = instruction.split(" ");
                String type = parts[0].toLowerCase();
                ArrayList<String> newParts = new ArrayList<>();
                newParts.add(type);
                switch (type) {
                    case "folder":
                        Debug.info("      Found folder type");
                        String folderEvents = null;
                        String delay = null;
                        String random = null;
                        for (String part : parts) {
                            if (part.startsWith("events:")) {
                                folderEvents = part.substring(7);
                            }
                            if (part.startsWith("delay:")) {
                                delay = part;
                            }
                            if (part.startsWith("random:")) {
                                random = part;
                            }
                        }
                        if (events != null) {
                            newParts.add(folderEvents);
                            if (delay != null) {
                                newParts.add(delay);
                            }
                            if (random != null) {
                                newParts.add(random);
                            }
                        } else {
                            Debug.info("      There are no events defined, skipping");
                            continue events;
                        }
                        break;
                    case "setblock":
                        Debug.info("      Found setblock type");
                        String block = null;
                        String loc = null;
                        String data = null;
                        for (String part : parts) {
                            if (part.startsWith("block:")) {
                                block = part.substring(6);
                            }
                            if (part.startsWith("loc:")) {
                                loc = part.substring(4);
                            }
                            if (part.startsWith("data:")) {
                                data = part;
                            }
                        }
                        if (block != null && loc != null) {
                            newParts.add(block);
                            newParts.add(loc);
                            if (data != null) {
                                newParts.add(data);
                            }
                        } else {
                            Debug.info("      There is no block/location defined, skipping");
                            continue events;
                        }
                        break;
                    default:
                        Debug.info("      This one does not need updating");
                        continue events;
                }
                StringBuilder builder = new StringBuilder();
                for (String part : newParts) {
                    builder.append(part);
                    builder.append(' ');
                }
                String newInstruction = builder.toString().trim();
                Debug.info("      Processing done, instruction: '" + newInstruction + "'");
                events.getConfig().set(key, newInstruction);
            }
            Debug.info("  All events updated successfully, saving to the file");
            events.saveConfig();
            
        } catch (Exception e) {
            e.printStackTrace();
            Debug.error(ERROR);
        }
        Debug.broadcast("Made instruction strings more beautiful! Please read the"
            + " documentation again.");
        config.set("version", "v11");
        instance.saveConfig();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v9() {
        config.set("combat_delay", "10");
        config.set("notify_pullback", "false");
        Debug.broadcast("Added combat delay and pullback notify options!");
        config.set("version", "v10");
        instance.saveConfig();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v8() {
        config.set("version", "v9");
        instance.saveConfig();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v7() {
        ConfigAccessor messages = ch.getConfigs().get("messages");
        messages.getConfig().set("date_format", "dd.MM.yyyy HH:mm");
        messages.saveConfig();
        Debug.broadcast("Added date format line to messages.yml");
        config.set("version", "v8");
        instance.saveConfig();
    }
    
    @SuppressWarnings("unused")
    private void update_from_v6() {
        Debug.broadcast("Added backpacks to the database!");
        config.set("version", "v7");
        instance.saveConfig();
    }

    @SuppressWarnings("unused")
    private void update_from_v5() {
        try {
            // delete isused column from tables objectives and tags
            Database database = instance.getDB();
            Connection connection = database.openConnection();
            String[] tables = new String[] { "objectives", "tags" };
            String prefix = instance.getConfig().getString("mysql.prefix", "");
            if (instance.isMySQLUsed()) {
                connection.prepareStatement(
                        "ALTER TABLE " + prefix + "objectives DROP COLUMN isused;").executeUpdate();
                connection.prepareStatement("ALTER TABLE " + prefix + "tags DROP COLUMN isused;")
                        .executeUpdate();
            } else {
                // drop column from objectives
                connection.prepareStatement("BEGIN TRANSACTION").executeUpdate();
                connection.prepareStatement("ALTER TABLE " + prefix + "objectives RENAME TO "
                    + prefix + "objectives_old").executeUpdate();
                connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + prefix + "objectives"
                    + " (id INTEGER PRIMARY KEY AUTOINCREMENT, playerID VARCHAR(256) NOT NULL, "
                    + "instructions VARCHAR(2048) NOT NULL);").executeUpdate();
                connection.prepareStatement("INSERT INTO " + prefix + "objectives SELECT id, "
                    + "playerID, instructions FROM " + prefix + "objectives_old").executeUpdate();
                connection.prepareStatement("DROP TABLE " + prefix + "objectives_old").executeUpdate();
                connection.prepareStatement("COMMIT").executeUpdate();
                // drop column from tags
                connection.prepareStatement("BEGIN TRANSACTION").executeUpdate();
                connection.prepareStatement("ALTER TABLE " + prefix + "tags RENAME TO "
                    + prefix + "tags_old").executeUpdate();
                connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + prefix + "tags"
                    + " (id INTEGER PRIMARY KEY AUTOINCREMENT, playerID VARCHAR(256) NOT NULL, "
                    + "tag TEXT NOT NULL);").executeUpdate();
                connection.prepareStatement("INSERT INTO " + prefix + "tags SELECT id, "
                    + "playerID, tag FROM " + prefix + "tags_old").executeUpdate();
                connection.prepareStatement("DROP TABLE " + prefix + "tags_old").executeUpdate();
                connection.prepareStatement("COMMIT").executeUpdate();
            }
            database.closeConnection(connection);
            Debug.broadcast("Updated database format to better one.");
        } catch (Exception e) {
            e.printStackTrace();
            Debug.error(ERROR);
        }
        config.set("version", "v6");
        instance.saveConfig();
    }

    @SuppressWarnings("unused")
    private void update_from_v4() {
        try {
            // update all give/take events and item condition to match new
            // parser
            ConfigAccessor eventsAccessor = ch.getConfigs().get("events");
            FileConfiguration eventsConfig = eventsAccessor.getConfig();
            Debug.info("Updating events!");
            // check every event in configuration
            for (String key : eventsConfig.getKeys(false)) {
                Debug.info("  Processing " + key);
                String instruction = eventsConfig.getString(key);
                // if the event is of type "give" or "take" then proceed
                if (instruction.startsWith("give ") || instruction.startsWith("take ")) {
                    String[] parts = instruction.split(" ");
                    Debug.info("    Found " + parts[0] + " event");
                    // get item's amount
                    int amount = 1;
                    for (String part : parts) {
                        if (part.startsWith("amount:")) {
                            amount = Integer.parseInt(part.substring(7));
                            Debug.info("    Amount is set to " + amount);
                        }
                    }
                    // generate new instruction
                    String newInstruction = parts[0] + " " + parts[1]
                        + ((amount != 1) ? ":" + amount : "");
                    Debug.info("    Saving instruction '" + newInstruction + "'");
                    // save it
                    eventsConfig.set(key, newInstruction);
                }
            }
            // when all events are converted, save the file
            eventsAccessor.saveConfig();
            // update all item conditions
            ConfigAccessor conditionsAccessor = ch.getConfigs().get("conditions");
            FileConfiguration conditionsConfig = conditionsAccessor.getConfig();
            Debug.info("Updatng conditions!");
            // check every condition in configuration
            for (String key : conditionsConfig.getKeys(false)) {
                Debug.info("  Processing " + key);
                String instruction = conditionsConfig.getString(key);
                // if the condition is of type "item" then proceed
                if (instruction.startsWith("item ")) {
                    String[] parts = instruction.split(" ");
                    Debug.info("    Found item condition");
                    // get item name and amount
                    String name = null;
                    int amount = 1;
                    for (String part : parts) {
                        if (part.startsWith("item:")) {
                            name = part.substring(5);
                            Debug.info("    Name is " + name);
                        } else if (part.startsWith("amount:")) {
                            amount = Integer.parseInt(part.substring(7));
                            Debug.info("    Amount is " + amount);
                        }
                    }
                    // generate new instruction
                    String newInstruction = "item " + name + ((amount != 1) ? ":" + amount : "");
                    Debug.info("    Saving instruction '" + newInstruction + "'");
                    // save it
                    conditionsConfig.set(key, newInstruction);
                }
            }
            // when all conditions are converted, save the file
            conditionsAccessor.saveConfig();
            Debug.broadcast("Converted give/take events and item conditions to new format!");
        } catch (Exception e) {
            e.printStackTrace();
            Debug.error(ERROR);
        }
        config.set("version", "v5");
        instance.saveConfig();
    }

    @SuppressWarnings("unused")
    private void update_from_v3() {
        config.set("mysql.prefix", "");
        Debug.broadcast("Added prefix option to MySQL settings!");
        config.set("version", "v4");
        instance.saveConfig();
    }

    @SuppressWarnings("unused")
    private void update_from_v2() {
        try {
            // start time counting, because why not?
            long time = new Date().getTime();
            // Get all conditions with --inverted tag into the map
            // <name,instruction> without --inverted tag and remove them form
            // config
            ConfigAccessor conditionsAccessor = ch.getConfigs().get("conditions");
            FileConfiguration conditionsConfig = conditionsAccessor.getConfig();
            // at the beginning trim all conditions, so they won't get
            // confused later on
            for (String path : conditionsConfig.getKeys(false)) {
                conditionsConfig.set(path, conditionsConfig.getString(path).trim());
            }
            HashMap<String, String> conditionsInverted = new HashMap<>();
            Debug.info("Extracting conditions to a map");
            // for each condition
            for (String name : conditionsConfig.getKeys(false)) {
                // get instruction
                String condition = conditionsConfig.getString(name);
                boolean wasInverted = false;
                int i = 1;
                Debug.info("  Checking condition " + name);
                // if it is --inverted
                while (condition.contains("--inverted")) {
                    Debug.info("    Loop " + i);
                    i++;
                    Debug.info("      Instruction: '" + condition + "'");
                    // get starting index of --inverted
                    int startingIndex = condition.indexOf(" --inverted");
                    Debug.info("      First occurence of --inverted tag: " + startingIndex);
                    // get first half (to cut --inverted)
                    String firstHalf = condition.substring(0, startingIndex);
                    Debug.info("      First half is '" + firstHalf + "'");
                    // get last half (from the end of --inverted string)
                    String lastHalf = condition.substring(startingIndex + 11);
                    Debug.info("      Last half is '" + lastHalf + "'");
                    // get new condition string without --inverted tag
                    condition = firstHalf + lastHalf;
                    wasInverted = true;
                    Debug.info("      And the whole new condition is '" + condition + "'");
                }
                if (wasInverted) {
                    Debug.info("  Removing from config and putting into a map!");
                    // remove it from config
                    conditionsConfig.set(name, null);
                    // put it into the map
                    conditionsInverted.put(name, condition);
                }
            }
            // for each, check for duplicates
            Debug.info("Checking for duplicates in config");
            HashMap<String, String> nameChanging = new HashMap<>();
            for (String invertedName : conditionsInverted.keySet()) {
                // check every condition from the map
                Debug.info("  Checking condition " + invertedName);
                String duplicateName = null;
                for (String normalName : conditionsConfig.getKeys(false)) {
                    // against every condition that is still in the config
                    if (conditionsConfig.getString(normalName).equals(
                            conditionsInverted.get(invertedName))) {
                        // if it is the same, then we have a match; we need to
                        // mark it as a duplicate
                        Debug.info("    Found a duplicate: " + normalName);
                        duplicateName = normalName;
                    }
                }
                if (duplicateName != null) {
                    // if it still exists in config, put it into map <old
                    // name, new name> as duplicate and !original
                    Debug.info("    Inserting into name changing map, from " + invertedName
                        + " to !" + duplicateName);
                    nameChanging.put(invertedName, "!" + duplicateName);
                } else {
                    // if it doesn't, put into a map as original and !original,
                    // and reinsert into config
                    Debug.info("    Inserting into name changing map, from " + invertedName
                        + " to !" + invertedName);
                    Debug.info("    Readding to configuration!");
                    nameChanging.put(invertedName, "!" + invertedName);
                    conditionsConfig.set(invertedName, conditionsInverted.get(invertedName));
                }
            }
            Debug.info("Starting conditions updating!");
            for (String key : conditionsConfig.getKeys(false)) {
                String instruction = conditionsConfig.getString(key).trim();
                Debug.info("  Processing condition " + key);
                if (instruction.startsWith("or ") || instruction.startsWith("and ")) {
                    String type = instruction.substring(0, instruction.indexOf(" "));
                    Debug.info("    Found " + type + " condition!");
                    int index = instruction.indexOf(" conditions:") + 12;
                    String firstPart = instruction.substring(0, index);
                    Debug.info("    First part is '" + firstPart + "'");
                    int secondIndex = index + instruction.substring(index).indexOf(" ");
                    if (secondIndex <= index) {
                        secondIndex = instruction.length();
                    }
                    String conditionList = instruction.substring(index, secondIndex);
                    Debug.info("    List of conditions is '" + conditionList + "'");
                    String lastPart = instruction.substring(secondIndex);
                    Debug.info("    Last part is '" + lastPart + "'");
                    String[] parts = conditionList.split(",");
                    for (int i = 0; i < parts.length; i++) {
                        // check each of them if it should be replaced
                        String replacement = nameChanging.get(parts[i]);
                        if (replacement != null) {
                            Debug.info("        Replacing " + parts[i] + " with " + replacement);
                            parts[i] = replacement;
                        }
                    }
                    StringBuilder newConditionsList = new StringBuilder();
                    for (String part : parts) {
                        newConditionsList.append(part + ",");
                    }
                    String newInstruction = firstPart + newConditionsList.toString()
                            .substring(0, newConditionsList.length() - 1) + lastPart;
                    Debug.info("    New instruction is '" + newInstruction + "'");
                    conditionsConfig.set(key, newInstruction);
                }
            }
            // save conditions so the changes persist
            conditionsAccessor.saveConfig();
            // now we have a map with names which need to be changed across all
            // configuration; for each conversation, for each NPC option and
            // player option, replace old names from the map with new names
            Debug.info("Starting conversation updating");
            // get every conversation accessor
            HashMap<String, ConfigAccessor> conversations = ch.getConversations();
            for (String conversationName : conversations.keySet()) {
                Debug.info("  Processing conversation " + conversationName);
                ConfigAccessor conversation = conversations.get(conversationName);
                // this list will store every path to condition list in this
                // conversation
                List<String> paths = new ArrayList<>();
                // for every npc option, check if it contains conditions
                // variable and add it to the list
                Debug.info("    Extracting conditions from NPC options");
                ConfigurationSection npcOptions = conversation.getConfig().getConfigurationSection(
                        "NPC_options");
                for (String npcPath : npcOptions.getKeys(false)) {
                    String conditionPath = "NPC_options." + npcPath + ".conditions";
                    if (conversation.getConfig().isSet(conditionPath)
                        && !conversation.getConfig().getString(conditionPath).equals("")) {
                        Debug.info("      Adding " + conditionPath + " to the list");
                        paths.add(conditionPath);
                    }
                }
                // for every player option, check if it contains conditions
                // variable and add it to the list
                Debug.info("    Extracting conditions from player options");
                ConfigurationSection playerOptions = conversation.getConfig()
                        .getConfigurationSection("player_options");
                for (String playerPath : playerOptions.getKeys(false)) {
                    String conditionPath = "player_options." + playerPath + ".conditions";
                    if (conversation.getConfig().isSet(conditionPath)
                        && !conversation.getConfig().getString(conditionPath).equals("")) {
                        Debug.info("      Adding " + conditionPath + " to the list");
                        paths.add(conditionPath);
                    }
                }
                // now we have a list of valid paths to condition variables
                // in this conversation
                for (String path : paths) {
                    Debug.info("    Processing path " + path);
                    // get the list of conditions (as a single string, separated
                    // by commas)
                    String list = conversation.getConfig().getString(path);
                    Debug.info("      Original conditions list is: " + list);
                    // split it into an array
                    String[] conditionArr = list.split(",");
                    for (int i = 0; i < conditionArr.length; i++) {
                        // for every condition name in array check if it should
                        // be replaced
                        String replacement = nameChanging.get(conditionArr[i]);
                        if (replacement != null) {
                            // and replace it
                            Debug.info("      Replacing " + conditionArr[i] + " with "
                                + replacement);
                            conditionArr[i] = replacement;
                        }
                    }
                    // now when everything is replaced generate new list (as a
                    // single string)
                    StringBuilder newListBuilder = new StringBuilder();
                    for (String condition : conditionArr) {
                        newListBuilder.append(condition + ",");
                    }
                    String newList = newListBuilder.toString().substring(0,
                            newListBuilder.length() - 1);
                    Debug.info("      Saving new list: " + newList);
                    // and set it
                    conversation.getConfig().set(path, newList);
                }
                // save conversation so the changes persist
                conversation.saveConfig();
            }
            // now every conversation is processed, time for events
            // for each event_conditions: and conditions: in events.yml, replace
            // old names from the map with new names
            Debug.info("Starting events updating");
            ConfigAccessor eventsAccessor = ch.getConfigs().get("events");
            for (String eventName : eventsAccessor.getConfig().getKeys(false)) {
                Debug.info("  Processing event " + eventName);
                // extract event's instruction
                String instruction = eventsAccessor.getConfig().getString(eventName);
                // check if it contains event conditions
                if (instruction.contains(" event_conditions:")) {
                    Debug.info("    Found event conditions!");
                    // extract first half (to the start of condition list
                    int index = instruction.indexOf(" event_conditions:") + 18;
                    String firstHalf = instruction.substring(0, index);
                    Debug.info("      First half is '" + firstHalf + "'");
                    // extract condition list
                    int secondIndex = index + instruction.substring(index).indexOf(" ");
                    if (secondIndex <= index) {
                        secondIndex = instruction.length();
                    }
                    String conditionList = instruction.substring(index, secondIndex);
                    Debug.info("      Condition list is '" + conditionList + "'");
                    // extract last half (from the end of condition list)
                    String lastHalf = instruction.substring(secondIndex, instruction.length());
                    Debug.info("      Last half is '" + lastHalf + "'");
                    // split conditions into an array
                    String[] parts = conditionList.split(",");
                    for (int i = 0; i < parts.length; i++) {
                        // check each of them if it should be replaced
                        String replacement = nameChanging.get(parts[i]);
                        if (replacement != null) {
                            Debug.info("        Replacing " + parts[i] + " with " + replacement);
                            parts[i] = replacement;
                        }
                    }
                    // put it all together
                    StringBuilder newListBuilder = new StringBuilder();
                    for (String part : parts) {
                        newListBuilder.append(part + ",");
                    }
                    String newList = newListBuilder.toString().substring(0,
                            newListBuilder.length() - 1);
                    Debug.info("      New condition list is '" + newList + "'");
                    // put the event together and save it
                    String newEvent = firstHalf + newList + lastHalf;
                    Debug.info("      Saving instruction '" + newEvent + "'");
                    eventsAccessor.getConfig().set(eventName, newEvent);
                }
                // read the instruction again, it could've changed
                instruction = eventsAccessor.getConfig().getString(eventName);
                // check if it containt objective conditions
                if (instruction.contains(" conditions:")) {
                    Debug.info("    Found objective conditions!");
                    // extract first half (to the start of condition list
                    int index = instruction.indexOf(" conditions:") + 12;
                    String firstHalf = instruction.substring(0, index);
                    Debug.info("      First half is '" + firstHalf + "'");
                    // extract condition list
                    int secondIndex = index + instruction.substring(index).indexOf(" ");
                    String conditionList = instruction.substring(index, secondIndex);
                    Debug.info("      Condition list is '" + conditionList + "'");
                    // extract last half (from the end of condition list)
                    String lastHalf = instruction.substring(secondIndex, instruction.length());
                    Debug.info("      Last half is '" + lastHalf + "'");
                    // split conditions into an array
                    String[] parts = conditionList.split(",");
                    for (int i = 0; i < parts.length; i++) {
                        // check each of them if it should be replaced
                        String replacement = nameChanging.get(parts[i]);
                        if (replacement != null) {
                            Debug.info("        Replacing " + parts[i] + " with " + replacement);
                            parts[i] = replacement;
                        }
                    }
                    // put it all together
                    StringBuilder newListBuilder = new StringBuilder();
                    for (String part : parts) {
                        newListBuilder.append(part + ",");
                    }
                    String newList = newListBuilder.toString().substring(0,
                            newListBuilder.length() - 1);
                    Debug.info("      New condition list is '" + newList + "'");
                    // put the event together and save it
                    String newEvent = firstHalf + newList + lastHalf;
                    Debug.info("      Saving instruction '" + newEvent + "'");
                    eventsAccessor.getConfig().set(eventName, newEvent);
                }
                // at this point we finished modifying this one event
            }
            // at this point we finished modifying every event, need to save
            // events
            eventsAccessor.saveConfig();
            // every place where conditions are is now updated, finished!
            Debug.broadcast("Converted inverted conditions to a new format using exclamation marks!");
            Debug.info("Converting took " + (new Date().getTime() - time) + "ms");
        } catch (Exception e) {
            // try-catch block is required - if there is some exception,
            // the version wouldn't get changed and updater would fall into
            // an infinite loop of endless exceptiorns
            e.printStackTrace();
            Debug.error(ERROR);
        }
        // set v3 version
        config.set("version", "v3");
        instance.saveConfig();
        // done
    }

    @SuppressWarnings("unused")
    private void update_from_v1() {
        config.set("debug", "false");
        Debug.broadcast("Added debug option to configuration!");
        config.set("version", "v2");
        instance.saveConfig();
    }

    private void updateTo1_6() {
        config.set("version", "v1");
        instance.saveConfig();
        performUpdate();
    }

    private void updateTo1_5_3() {
        // nothing to update
        config.set("version", "1.5.3");
        updateTo1_6();
    }

    private void updateTo1_5_2() {
        // nothing to update
        config.set("version", "1.5.2");
        updateTo1_5_3();
    }

    private void updateTo1_5_1() {
        // nothing to update
        config.set("version", "1.5.1");
        updateTo1_5_2();
    }

    private void updateTo1_5() {
        Debug.broadcast("Started converting configuration files from v1.4 to v1.5!");
        // add sound settings
        String[] array1 = new String[] { "start", "end", "journal", "update", "full" };
        for (String string : array1) {
            config.set("sounds." + string, config.getDefaults().getString("sounds." + string));
        }
        Debug.broadcast("Added new sound options!");
        // add colors for journal
        String[] array2 = new String[] { "date.day", "date.hour", "line", "text" };
        for (String string : array2) {
            config.set("journal_colors." + string,
                    config.getDefaults().getString("journal_colors." + string));
        }
        Debug.broadcast("Added new journal color options!");
        // convert conditions in events to event_condition: format
        Debug.info("Starting updating 'conditions:' argument to 'event_conditions:' in events.yml");
        ConfigAccessor events = ch.getConfigs().get("events");
        for (String key : events.getConfig().getKeys(false)) {
            Debug.info("  Processing event " + key);
            if (events.getConfig().getString(key).contains("conditions:")) {
                StringBuilder parts = new StringBuilder();
                for (String part : events.getConfig().getString(key).split(" ")) {
                    if (part.startsWith("conditions:")) {
                        parts.append("event_conditions:" + part.substring(11) + " ");
                    } else {
                        parts.append(part + " ");
                    }
                }
                Debug.info("    Found 'conditions:' option, replacing!");
                events.getConfig().set(key, parts.substring(0, parts.length() - 1));
            }
        }
        Debug.broadcast("Events now use 'event_conditions:' for conditioning.");
        // convert objectives to new format
        Debug.info("Converting objectives to new format...");
        ConfigAccessor objectives = ch.getConfigs().get("objectives");
        for (String key : events.getConfig().getKeys(false)) {
            Debug.info("  Processing objective " + key);
            if (events.getConfig().getString(key).split(" ")[0].equalsIgnoreCase("objective")) {
                events.getConfig().set(
                        key,
                        "objective "
                            + objectives.getConfig().getString(
                                    events.getConfig().getString(key).split(" ")[1]));
                Debug.info("      Event " + key + " converted!");
            }
        }
        Debug.broadcast("Objectives converted to new, event-powered format!");
        // convert global locations
        String globalLocations = config.getString("global_locations");
        if (globalLocations != null && !globalLocations.equals("")) {
            StringBuilder configGlobalLocs = new StringBuilder();
            Debug.broadcast("Converting global locations to use events...");
            int i = 0;
            for (String globalLoc : config.getString("global_locations").split(",")) {
                i++;
                events.getConfig().set("global_location_" + i,
                        "objective " + objectives.getConfig().getString(globalLoc));
                configGlobalLocs.append("global_location_" + i + ",");
                Debug.broadcast("Converted " + globalLoc + " objective.");
            }
            config.set("global_locations",
                    configGlobalLocs.substring(0, configGlobalLocs.length() - 1));
            Debug.broadcast("All " + i + " global locations have been converted.");
        }
        events.saveConfig();
        Debug.broadcast("Removing old file.");
        new File(instance.getDataFolder(), "objectives.yml").delete();
        // convert books to new format
        Debug.broadcast("Converting books to new format!");
        ConfigAccessor items = ch.getConfigs().get("items");
        for (String key : items.getConfig().getKeys(false)) {
            String string = items.getConfig().getString(key);
            if (string.split(" ")[0].equalsIgnoreCase("WRITTEN_BOOK")) {
                String text = null;
                LinkedList<String> parts = new LinkedList<String>(Arrays.asList(string.split(" ")));
                for (Iterator<String> iterator = parts.iterator(); iterator.hasNext();) {
                    String part = (String) iterator.next();
                    if (part.startsWith("text:")) {
                        text = part.substring(5);
                        iterator.remove();
                        break;
                    }
                }
                if (text != null) {
                    StringBuilder pages = new StringBuilder();
                    for (String page : Utils.pagesFromString(text.replace("_", " "), true)) {
                        pages.append(page.replaceAll(" ", "_") + "|");
                    }
                    parts.add("text:" + pages.substring(0, pages.length() - 2));
                    StringBuilder instruction = new StringBuilder();
                    for (String part : parts) {
                        instruction.append(part + " ");
                    }
                    items.getConfig().set(key,
                            instruction.toString().trim().replaceAll("\\n", "\\\\n"));
                    Debug.broadcast("Converted book " + key + ".");
                }
            }
        }
        items.saveConfig();
        Debug.broadcast("All books converted!");
        // JournalBook.pagesFromString(questItem.getText(), false);
        config.set("tellraw", "false");
        Debug.broadcast("Tellraw option added to config.yml!");
        config.set("autoupdate", "true");
        Debug.broadcast("AutoUpdater is now enabled by default! You can change this if you"
            + " want and reload the plugin, nothing will be downloaded in that case.");
        // end of update
        config.set("version", "1.5");
        Debug.broadcast("Conversion to v1.5 finished.");
        updateTo1_5_1();
    }

    private void updateTo1_4_3() {
        // nothing to update
        config.set("version", "1.4.3");
        updateTo1_5();
    }

    private void updateTo1_4_2() {
        // nothing to update
        config.set("version", "1.4.2");
        updateTo1_4_3();
    }

    private void updateTo1_4_1() {
        // nothing to update
        config.set("version", "1.4.1");
        updateTo1_4_2();
    }

    private void updateTo1_4() {
        Debug.broadcast("Started converting configuration files from v1.3 to v1.4!");
        instance.getConfig().set("autoupdate", "false");
        Debug.broadcast("Added AutoUpdate option to config. It's DISABLED by default!");
        Debug.broadcast("Moving conversation to separate files...");
        ConfigAccessor convOld = ch.getConfigs().get("conversations");
        Set<String> keys = convOld.getConfig().getKeys(false);
        File folder = new File(instance.getDataFolder(), "conversations");
        for (File file : folder.listFiles()) {
            file.delete();
        }
        for (String convID : keys) {
            File convFile = new File(folder, convID + ".yml");
            Map<String, Object> convSection = convOld.getConfig().getConfigurationSection(convID)
                    .getValues(true);
            YamlConfiguration convNew = YamlConfiguration.loadConfiguration(convFile);
            for (String key : convSection.keySet()) {
                convNew.set(key, convSection.get(key));
            }
            try {
                convNew.save(convFile);
                Debug.broadcast("Conversation " + convID + " moved to it's own file!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Debug.broadcast("All conversations moved, deleting old file.");
        new File(instance.getDataFolder(), "conversations.yml").delete();

        // updating items
        Debug.broadcast("Starting conversion of items...");
        // this map will contain all QuestItem objects extracted from
        // configs
        HashMap<String, QuestItem> items = new HashMap<>();
        // this is counter for a number in item names (in items.yml)
        int number = 0;
        // check every event
        for (String key : ch.getConfigs().get("events").getConfig().getKeys(false)) {
            String instructions = ch.getString("events." + key);
            String[] parts = instructions.split(" ");
            String type = parts[0];
            // if this event has items in it do the thing
            if (type.equals("give") || type.equals("take")) {
                // define all required variables
                String amount = "";
                String conditions = "";
                String material = null;
                int data = 0;
                Map<String, Integer> enchants = null;
                List<String> lore = null;
                String name = null;
                // for each part of the instruction string check if it
                // contains some data and if so pu it in variables
                for (String part : parts) {
                    if (part.contains("type:")) {
                        material = part.substring(5);
                    } else if (part.contains("data:")) {
                        data = Byte.valueOf(part.substring(5));
                    } else if (part.contains("enchants:")) {
                        enchants = new HashMap<>();
                        for (String enchant : part.substring(9).split(",")) {
                            enchants.put(enchant.split(":")[0],
                                    Integer.decode(enchant.split(":")[1]));
                        }
                    } else if (part.contains("lore:")) {
                        lore = new ArrayList<>();
                        for (String loreLine : part.substring(5).split(";")) {
                            lore.add(loreLine.replaceAll("_", " "));
                        }
                    } else if (part.contains("name:")) {
                        name = part.substring(5).replaceAll("_", " ");
                    } else if (part.contains("amount:")) {
                        amount = part;
                    } else if (part.contains("conditions:")) {
                        conditions = part;
                    }
                }
                // create an item
                String newItemID = null;
                QuestItem item = new QuestItem(material, data, enchants, name, lore);
                boolean contains = false;
                for (String itemKey : items.keySet()) {
                    if (items.get(itemKey).equalsQ(item)) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    // generate new name for an item
                    newItemID = "item" + number;
                    number++;
                    items.put(newItemID, item);
                } else {
                    for (String itemName : items.keySet()) {
                        if (items.get(itemName).equalsQ(item)) {
                            newItemID = itemName;
                        }
                    }
                }
                ch.getConfigs().get("events").getConfig().set(key, (type + " " + newItemID + " "
                        + amount + " " + conditions).trim());

                // replace event with updated version
                Debug.broadcast("Extracted " + newItemID + " from " + key + " event!");
            }
        }
        // check every condition (it's almost the same code, I didn't know how
        // to do
        // it better
        for (String key : ch.getConfigs().get("conditions").getConfig().getKeys(false)) {
            String instructions = ch.getString("conditions." + key);
            String[] parts = instructions.split(" ");
            String type = parts[0];
            // if this condition has items do the thing
            if (type.equals("hand") || type.equals("item")) {
                // define all variables
                String amount = "";
                String material = null;
                int data = 0;
                Map<String, Integer> enchants = new HashMap<>();
                List<String> lore = new ArrayList<>();
                String name = null;
                String inverted = "";
                // for every part check if it has some data and place it in
                // variables
                for (String part : parts) {
                    if (part.contains("type:")) {
                        material = part.substring(5);
                    } else if (part.contains("data:")) {
                        data = Byte.valueOf(part.substring(5));
                    } else if (part.contains("enchants:")) {
                        for (String enchant : part.substring(9).split(",")) {
                            enchants.put(enchant.split(":")[0],
                                    Integer.decode(enchant.split(":")[1]));
                        }
                    } else if (part.contains("lore:")) {
                        for (String loreLine : part.substring(5).split(";")) {
                            lore.add(loreLine.replaceAll("_", " "));
                        }
                    } else if (part.contains("name:")) {
                        name = part.substring(5).replaceAll("_", " ");
                    } else if (part.contains("amount:")) {
                        amount = part;
                    } else if (part.equalsIgnoreCase("--inverted")) {
                        inverted = part;
                    }
                }
                // create an item
                String newItemID = null;
                QuestItem item = new QuestItem(material, data, enchants, name, lore);
                boolean contains = false;
                for (String itemKey : items.keySet()) {
                    if (items.get(itemKey).equalsQ(item)) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    // generate new name for an item
                    newItemID = "item" + number;
                    number++;
                    items.put(newItemID, item);
                } else {
                    for (String itemName : items.keySet()) {
                        if (items.get(itemName).equalsQ(item)) {
                            newItemID = itemName;
                        }
                    }
                }
                ch.getConfigs().get("conditions").getConfig().set(key,(type + " item:" + newItemID
                        + " " + amount + " " + inverted).trim());
                Debug.broadcast("Extracted " + newItemID + " from " + key + " condition!");
            }
        }
        // generated all items, now place them in items.yml
        for (String key : items.keySet()) {
            QuestItem item = items.get(key);
            String instruction = item.getMaterial() + " data:" + item.getData();
            if (item.getName() != null) {
                instruction = instruction + " name:" + item.getName().replace(" ", "_");
            }
            if (!item.getLore().isEmpty()) {
                StringBuilder lore = new StringBuilder();
                for (String line : item.getLore()) {
                    lore.append(line + ";");
                }
                instruction = instruction + " lore:"
                    + (lore.substring(0, lore.length() - 1).replace(" ", "_"));
            }
            if (!item.getEnchants().isEmpty()) {
                StringBuilder enchants = new StringBuilder();
                for (Enchantment enchant : item.getEnchants().keySet()) {
                    enchants.append(enchant.toString() + ":" + item.getEnchants().get(enchant)
                        + ",");
                }
                instruction = instruction + " enchants:"
                    + enchants.substring(0, enchants.length() - 1);
            }
            ch.getConfigs().get("items").getConfig().set(key, instruction);
        }
        ch.getConfigs().get("items").saveConfig();
        ch.getConfigs().get("events").saveConfig();
        ch.getConfigs().get("conditions").saveConfig();
        Debug.broadcast("All extracted items has been successfully saved to items.yml!");
        // end of updating to 1.4
        instance.getConfig().set("version", "1.4");
        Debug.broadcast("Conversion to v1.4 finished.");
        updateTo1_4_1();
    }

    private void updateTo1_3() {
        Debug.broadcast("Started converting configuration files from unknown version to v1.3!");
        // add conversion options
        Debug.broadcast("Using Names by for safety. If you run UUID compatible server and "
            + "want to use UUID, change it manually in the config file and reload the plugin.");
        config.set("uuid", "false");
        // this will alert the plugin that the conversion should be done if UUID
        // is
        // set to true
        config.set("convert", "true");
        // add metrics if they are not set yet
        if (!config.isSet("metrics")) {
            Debug.broadcast("Added metrics option.");
            config.set("metrics", "true");
        }
        // add stop to conversation if not done already
        Debug.broadcast("Adding stop nodes to conversations...");
        int count = 0;
        ConfigAccessor conversations = ch.getConfigs().get("conversations");
        Set<String> convNodes = conversations.getConfig().getKeys(false);
        for (String convNode : convNodes) {
            if (!conversations.getConfig().isSet(convNode + ".stop")) {
                conversations.getConfig().set(convNode + ".stop", "false");
                count++;
            }
        }
        conversations.saveConfig();
        Debug.broadcast("Done, modified " + count + " conversations!");
        // end of updating to 1.3
        config.set("version", "1.3");
        Debug.broadcast("Conversion to v1.3 finished.");
        updateTo1_4();
    }

    /**
     * Updates language file, so it contains all required messages.
     */
    private void updateLanguages() {
        // add new languages
        boolean isUpdated = false;
        ConfigAccessor messages = ch.getConfigs().get("messages");
        // check every language if it exists
        for (String path : messages.getConfig().getDefaultSection().getKeys(false)) {
            if (messages.getConfig().isSet(path)) {
                // if it exists check every message if it exists
                for (String messageNode : messages.getConfig().getDefaults()
                        .getConfigurationSection(path).getKeys(false)) {
                    if (!messages.getConfig().isSet(path + "." + messageNode)) {
                        // if message doesn't exist then add it from defaults
                        messages.getConfig().set(path + "." + messageNode,
                                messages.getConfig().getDefaults().get(path + "." + messageNode));
                        isUpdated = true;
                    }
                }
            } else {
                // if language does not exist then add every message to it
                for (String messageNode : messages.getConfig().getDefaults()
                        .getConfigurationSection(path).getKeys(false)) {
                    messages.getConfig().set(path + "." + messageNode,
                            messages.getConfig().getDefaults().get(path + "." + messageNode));
                    isUpdated = true;
                }
            }
        }
        // if we updated config filse then print the message
        if (isUpdated) {
            messages.saveConfig();
            Debug.broadcast("Updated language files!");
        }
    }

    /**
     * As the name says, converts all names to UUID in database
     */
    @SuppressWarnings("deprecation")
    private void convertNamesToUUID() {
        Debug.broadcast("Converting names to UUID...");
        // loop all tables
        HashMap<String, String> list = new HashMap<>();
        String[] tables = new String[] { "OBJECTIVES", "TAGS", "POINTS", "JOURNAL", "BACKPACK" };
        Connector con = new Connector();
        for (String table : tables) {
            ResultSet res = con.querySQL(QueryType.valueOf("SELECT_PLAYERS_" + table),
                    new String[] {});
            try {
                while (res.next()) {
                    // and extract from them list of player names
                    String playerID = res.getString("playerID");
                    if (!list.containsKey(playerID)) {
                        list.put(playerID, Bukkit.getOfflinePlayer(playerID).getUniqueId()
                                .toString());
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        // convert all player names in all tables
        for (String table : tables) {
            for (String playerID : list.keySet()) {
                con.updateSQL(UpdateType.valueOf("UPDATE_PLAYERS_" + table),
                        new String[] { list.get(playerID), playerID });
            }
        }
        con.close();
        Debug.broadcast("Names conversion finished!");
    }

    /**
     * Adds the changelog file.
     */
    private void addChangelog() {
        try {
            File changelog = new File(BetonQuest.getInstance().getDataFolder(), "changelog.txt");
            if (changelog.exists()) {
                changelog.delete();
            }
            Files.copy(BetonQuest.getInstance().getResource("changelog.txt"), changelog.toPath());
            Debug.broadcast("Changelog added!");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    
    private String convertObjective(String obj) {
        StringBuilder builder = new StringBuilder();
        for (String part : obj.split(" ")) {
            if (part.startsWith("tag:")) {
                builder.append("label:" + part.substring(4));
            } else {
                builder.append(part);
            }
            builder.append(' ');
        }
        return builder.toString().trim();
    }
    
    /**
     * Deprecated config handler, used only for configuration updating process
     * 
     * @author Jakub Sapalski
     */
    private class ConfigHandler {

        /**
         * Map containing accessors for every conversation.
         */
        private HashMap<String, ConfigAccessor> conversationsMap = new HashMap<>();
        /**
         * Deprecated accessor for single conversations file, used only for updating
         * configuration.
         */
        private ConfigAccessor conversations;
        /**
         * Deprecated accessor for objectives file, used only for updating
         * configuration.
         */
        private ConfigAccessor objectives;
        /**
         * Accessor for conditions file.
         */
        private ConfigAccessor conditions;
        /**
         * Accessor for events file.
         */
        private ConfigAccessor events;
        /**
         * Accessor for messages file.
         */
        private ConfigAccessor messages;
        /**
         * Accessor for npcs file.
         */
        private ConfigAccessor npcs;
        /**
         * Accessor for journal file.
         */
        private ConfigAccessor journal;
        /**
         * Accessor for items file.
         */
        private ConfigAccessor items;

        /**
         * Legacy configuration handler, only used for updating purposes. Do not use!!!
         */
        public ConfigHandler() {
            // put config accesors in fields
            conversations = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest
                    .getInstance().getDataFolder(), "conversations.yml"), "conversations.yml");
            objectives = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "objectives.yml"), "objectives.yml");
            conditions = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "conditions.yml"), "conditions.yml");
            events = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "events.yml"), "events.yml");
            npcs = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "npcs.yml"), "npcs.yml");
            journal = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "journal.yml"), "journal.yml");
            items = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "items.yml"), "items.yml");
            messages = new ConfigAccessor(BetonQuest.getInstance(), new File(BetonQuest.getInstance()
                    .getDataFolder(), "messages.yml"), "messages.yml");
            // put conversations accessors in the hashmap
            for (File file : new File(BetonQuest.getInstance().getDataFolder(), "conversations").listFiles()) {
                conversationsMap.put(file.getName().substring(0, file.getName().indexOf(".")),
                        new ConfigAccessor(BetonQuest.getInstance(), file, file.getName()));
            }
        }

        /**
         * Retireves from configuration the string at supplied path. The path should
         * follow this syntax: "filename.branch.(moreBranches).branch.variable". For
         * example getting color for day in journal date would be
         * "config.journal_colors.date.day". Everything should be handled as a
         * string for simplicity's sake.
         *
         * @param rawPath
         *            path for the variable
         * @return the String object representing requested variable
         */
        public String getString(String rawPath) {
            
            // get parts of path
            String[] parts = rawPath.split("\\.");
            String first = parts[0];
            String path = rawPath.substring(first.length() + 1);
            String object;
            // for every possible file try to access the path and return String
            // object
            switch (first) {
                case "config":
                    object = BetonQuest.getInstance().getConfig().getString(path);
                    if (object == null) {
                        // if object is null then there is no such variable at
                        // specified path
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "conversations":
                    object = null;
                    // conversations should be handled with one more level, as they
                    // are in
                    // multiple files
                    String conversationID = path.split("\\.")[0];
                    String rest = path.substring(path.indexOf(".") + 1);
                    if (conversationsMap.get(conversationID) != null) {
                        object = conversationsMap.get(conversationID).getConfig()
                                .getString(rest);
                    }
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "objectives":
                    object = objectives.getConfig().getString(path);
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "conditions":
                    object = conditions.getConfig().getString(path);
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "events":
                    object = events.getConfig().getString(path);
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "messages":
                    object = messages.getConfig().getString(path);
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "npcs":
                    object = npcs.getConfig().getString(path);
                    return object;
                case "journal":
                    object = journal.getConfig().getString(path);
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                case "items":
                    object = items.getConfig().getString(path);
                    if (object == null) {
                        Debug.info("Error while accessing path: " + rawPath);
                    }
                    return object;
                default:
                    Debug.info("Fatal error while accessing path: " + rawPath
                        + " (there is no such file)");
                    return null;
            }
        }

        /**
         * Retrieves a map containing all config accessors. Should be used for more
         * advanced tasks than simply getting a String. Note that conversations are
         * not included in this map. See {@link #getConversations()
         * getConversations} method for that. Conversations accessor included in
         * this map is just a deprecated old conversations file. The same situation
         * is with unused objectives accessor.
         *
         * @return HashMap containing all config accessors
         */
        public HashMap<String, ConfigAccessor> getConfigs() {
            HashMap<String, ConfigAccessor> map = new HashMap<>();
            map.put("conversations", conversations);
            map.put("conditions", conditions);
            map.put("events", events);
            map.put("objectives", objectives);
            map.put("journal", journal);
            map.put("messages", messages);
            map.put("npcs", npcs);
            map.put("items", items);
            return map;
        }

        /**
         * Retrieves map containing all conversation accessors.
         *
         * @return HashMap containing conversation accessors
         */
        public HashMap<String, ConfigAccessor> getConversations() {
            return conversationsMap;
        }
    }
}
