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
package pl.betoncraft.betonquest.conditions;

import pl.betoncraft.betonquest.api.Condition;
import pl.betoncraft.betonquest.core.InstructionParseException;
import pl.betoncraft.betonquest.utils.PlayerConverter;

/**
 * Checks Y height player is at (must be below)
 * 
 * @author BYK
 */
public class HeightCondition extends Condition {

    private final double height;

    public HeightCondition(String packName, String instructions)
            throws InstructionParseException {
        super(packName, instructions);
        String[] parts = instructions.split(" ");
        if (parts.length < 2) {
            throw new InstructionParseException("Height not defined");
        }
        try {
            height = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            throw new InstructionParseException("Could not parse height");
        }
    }

    @Override
    public boolean check(String playerID) {
        if (PlayerConverter.getPlayer(playerID).getLocation().getY() <= height) {
            return true;
        }
        return false;
    }

}
