package de.intranda.goobi;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 */
import java.text.DecimalFormat;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Step;

import de.sub.goobi.helper.VariableReplacer;
import lombok.Data;

@Data
public class NamePart {

    private String value;
    private final boolean number;
    private final String format;

    public NamePart(String string) {
        this.number = false;
        this.format = "";
        setValue(string);
    }

    public NamePart(Configuration config, Step step) {
        this.number = config.getBoolean("number", false);
        this.format = config.getString("number/@format", "");
        setValue(config.getString("defaultValue", ""));
        replaceVariables(step);
    }

    public NamePart(NamePart blueprint) {
        this.number = blueprint.number;
        this.format = blueprint.format;
        this.value = blueprint.value;
    }

    private void replaceVariables(Step step) {
        setValue(new VariableReplacer(null, null, step.getProzess(), step).replace(getValue()));

    }

    public void setValue(String value) {
        if (number && StringUtils.isNotBlank(format) && value.trim().matches("\\d+")) {
            this.value = new DecimalFormat(format).format(Integer.parseInt(value));
        } else {
            this.value = value;
        }
    }

}
