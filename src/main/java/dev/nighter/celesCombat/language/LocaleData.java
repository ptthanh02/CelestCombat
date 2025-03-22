package dev.nighter.celesCombat.language;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;

@Getter
public class LocaleData {
    private final YamlConfiguration messages;
    private final YamlConfiguration gui;
    private final YamlConfiguration formatting;

    public LocaleData(YamlConfiguration messages, YamlConfiguration gui, YamlConfiguration formatting) {
        this.messages = messages;
        this.gui = gui;
        this.formatting = formatting;
    }
}