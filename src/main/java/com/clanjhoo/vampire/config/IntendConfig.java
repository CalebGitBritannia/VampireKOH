package com.clanjhoo.vampire.config;

import org.bukkit.configuration.ConfigurationSection;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;

public class IntendConfig {
    public final boolean enabled;
    public final double infectionChance;

    public IntendConfig() {
        enabled = true;
        infectionChance = 0.05;
    }

    public IntendConfig(@Nonnull ConfigurationSection cs) {
        IntendConfig def = new IntendConfig();

        enabled = cs.getBoolean("enabled", def.enabled);
        infectionChance = cs.getDouble("infectionChance", def.infectionChance);
    }

    protected boolean saveConfigToFile(BufferedWriter configWriter, String indent, int level) {
        boolean result = PluginConfig.writeLine(configWriter, "enabled: " + this.enabled, indent, level);
        result = result && PluginConfig.writeLine(configWriter, "infectionChance: " + this.infectionChance, indent, level);

        return result;
    }

    @Override
    public String toString() {
        return "IntendConfig{" +
                "enabled=" + enabled +
                ", infectionChance=" + infectionChance +
                '}';
    }
}
