package de.intranda.goobi;

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
        if(number && StringUtils.isNotBlank(format) && value.trim().matches("\\d+")) {
            this.value = new DecimalFormat(format).format(Integer.parseInt(value));
        } else {
            this.value = value;
        }
    }


}
