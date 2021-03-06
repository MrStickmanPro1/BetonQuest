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
package pl.betoncraft.betonquest.compatibility;

import net.citizensnpcs.api.npc.NPC;

import org.bukkit.Location;

import pl.betoncraft.betonquest.core.Conversation;

/**
 * Represents a conversation with NPC
 * 
 * @author Coosh
 */
public class CitizensConversation extends Conversation {
    
    private final NPC n;

    public CitizensConversation(String playerID, String packName, String conversationID, Location location, NPC npc) {
        super(playerID, packName, conversationID, location);
        this.n = npc;
    }
    
    /**
     * This will return the NPC associated with this conversation only
     * after the coversation is created (all player options are listed
     * and ready to receive player input)
     * 
     * @return the NPC or null if it's too early
     */
    public NPC getNPC() {
        return n;
    }

}
