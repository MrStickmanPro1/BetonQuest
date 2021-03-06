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
package pl.betoncraft.betonquest.events;

import org.bukkit.scheduler.BukkitRunnable;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.api.QuestEvent;
import pl.betoncraft.betonquest.core.InstructionParseException;
import pl.betoncraft.betonquest.database.DatabaseHandler;
import pl.betoncraft.betonquest.utils.PlayerConverter;

/**
 * Deletes the objective with specified label
 * 
 * @author Jakub Sapalski
 */
public class DeleteObjectiveEvent extends QuestEvent {

    private final String tag;

    public DeleteObjectiveEvent(String packName, String instructions)
            throws InstructionParseException {
        super(packName, instructions);
        persistent = true;
        String[] parts = instructions.split(" ");
        if (parts.length < 2) {
            throw new InstructionParseException("Not enough arguments");
        }
        tag = parts[1];
    }

    @Override
    public void run(final String playerID) {
        if (PlayerConverter.getPlayer(playerID) != null) {
            BetonQuest.getInstance().getDBHandler(playerID).deleteObjective(tag);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    DatabaseHandler dbHandler = new DatabaseHandler(playerID);
                    dbHandler.deleteObjective(tag);
                    dbHandler.saveData();
                    dbHandler.removeData();
                }
            }.runTaskAsynchronously(BetonQuest.getInstance());
        }
    }
}
