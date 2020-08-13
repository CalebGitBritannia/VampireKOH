package com.clanjhoo.vampire.keyproviders;

import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum InfectionMessageKeys implements MessageKeyProvider {
    ALTAR("infection.altar"),
    COMBAT_MISTAKE("infection.combatMistake"),
    COMBAT_INTENDED("infection.combatIntended"),
    TRADE("infection.trade"),
    FLASK("infection.flask"),
    COMMAND("infection.command"),
    UNKNOWN("infection.unknown"),
    CURED("infection.cured"),
    FEELING("infection.feeling"),
    HINT("infection.hint")
    ;

    InfectionMessageKeys(String key) {
        this.key = MessageKey.of(key);
    }

    private MessageKey key;

    @Override
    public MessageKey getMessageKey() {
        return key;
    }

    public static MessageKey getFeeling(int index) {
        InfectionMessageKeys feel = InfectionMessageKeys.FEELING;
        int maxNum = InfectionMessageKeys.getMaxFeeling();
        index += 1;
        if (index > maxNum || index < 1)
            throw new IllegalArgumentException();

        return MessageKey.of(feel.key.getKey() + index);
    }

    public static MessageKey getHint(int index) {
        InfectionMessageKeys hint = InfectionMessageKeys.HINT;
        int maxNum = InfectionMessageKeys.getMaxHint();
        index += 1;
        if (index > maxNum || index < 1)
            throw new IllegalArgumentException();

        return MessageKey.of(hint.key.getKey() + index);
    }

    public static int getMaxFeeling() {
        return 24;
    }

    public static int getMaxHint() {
        return 4;
    }
}
