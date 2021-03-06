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
package pl.betoncraft.betonquest.api;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.config.Config;
import pl.betoncraft.betonquest.config.ConfigPackage;
import pl.betoncraft.betonquest.core.InstructionParseException;
import pl.betoncraft.betonquest.utils.Debug;
import pl.betoncraft.betonquest.utils.PlayerConverter;

/**
 * Superclass for all events. You need to extend it in order to create new
 * custom events.
 * <p/>
 * Registering your events is done through {@link
 * pl.betoncraft.betonquest.BetonQuest#registerEvents(String, Class<? extends
 * QuestEvent>) registerEvents} method.
 * 
 * @author Jakub Sapalski
 */
public abstract class QuestEvent {

    /**
     * Stores instruction string for the event.
     */
    protected final String instructions;
    /**
     * Stores conditions that must be met when firing this event
     */
    protected final String[] conditions;
    /**
     * ConfigPackage in which this event is defined
     */
    protected final ConfigPackage pack;
    /**
     * Describes if the event is static
     */
    protected boolean staticness = false;
    /**
     * Describes if the event is persistent
     */
    protected boolean persistent = false;

    /**
     * Creates new instance of the event. The event should parse instruction
     * string without doing anything else. If anything goes wrong, throw
     * {@link InstructionParseException} with error message describing the problem.
     * 
     * @param packName
     *            ID of the player this event is related to. It will be passed
     *            at runtime, you only need to use it according to what your
     *            event does.
     * @param instructions
     *            instruction string passed at runtime. You need to extract all
     *            required data from it and display errors if there is anything
     *            wrong.
     */
    public QuestEvent(String packName, String instructions) throws InstructionParseException {
        this.instructions = instructions;
        this.pack = Config.getPackage(packName);
        String[] tempConditions = new String[]{};
        String[] parts = instructions.split(" ");
        for (String part : parts) {
            if (part.startsWith("event_conditions:")) {
        	tempConditions = part.substring(17).split(",");
            }
        }
        for (int i = 0; i < tempConditions.length; i++) {
            if (!tempConditions[i].contains(".")) {
        	tempConditions[i] = pack.getName() + "." + tempConditions[i];
            }
        }
        conditions = tempConditions;
    }
    
    /**
     * This method should contain all logic for firing the event and use the
     * data parsed by the condtructor. When this method is called
     * all the required data is present and parsed correctly.
     * 
     * @param playerID
     * 		ID of the player for whom the event will fire
     */
    abstract public void run(String playerID);
    
    /**
     * Fires an event for the player. The event conditions are checked, so it's
     * not needed to check them explicitly.
     * 
     * @param playerID
     * 		ID of the player for whom the event will fire
     */
    public final void fire(String playerID) {
        // check if playerID isn't null, this event cannot be static
        if (playerID == null) {
            if (!staticness) {
                Debug.error("This event cannot be static: " + instructions);
                return;
            }
        }
        // check if the event cannot be fired for offline players
        if (PlayerConverter.getPlayer(playerID) == null) {
            if (!persistent) {
		Debug.info("Player " + playerID + " is offline, cannot fire event");
		return;
	    }
        }
        // check event conditions before firing the event
	for (String condition : conditions) {
	    if (!BetonQuest.condition(playerID, condition)) {
		Debug.info("Event conditions were not met.");
		return;
	    }
	}
	run(playerID);
    }
}
