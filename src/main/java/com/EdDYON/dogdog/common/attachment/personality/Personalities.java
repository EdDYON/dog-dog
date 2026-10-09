package com.EdDYON.dogdog.common.attachment.personality;

import com.EdDYON.dogdog.common.attachment.personality.impl.*;
import java.util.HashMap;
import java.util.Map;

public class Personalities {
    private static final Map<String, Personality> REGISTRY = new HashMap<>();

    public static final Personality NONE = register(new Personality() {
        @Override public String getId() { return "none"; }
    });

    public static final Personality GLUTTON = register(new GluttonPersonality());
    public static final Personality COWARD = register(new CowardPersonality());
    public static final Personality AGGRESSIVE = register(new AggressivePersonality());
    public static final Personality SENTRY = register(new SentryPersonality());
    public static final Personality AMPHIBIAN = register(new AmphibianPersonality());
    public static final Personality VAMPIRE = register(new VampirePersonality());
    public static final Personality TANK = register(new TankPersonality());
    public static final Personality ASSASSIN = register(new AssassinPersonality());
    public static final Personality HUSKY = register(new HuskyPersonality());
    public static final Personality LAZY = register(new LazyPersonality());
    public static final Personality HEALER = register(new HealerPersonality());
    public static final Personality DIGGER = register(new DiggerPersonality());
    public static final Personality CLINGY = register(new ClingyPersonality());

    private static Personality register(Personality p) {
        REGISTRY.put(p.getId(), p);
        return p;
    }

    public static Personality get(String id) {
        return REGISTRY.getOrDefault(id, NONE);
    }
}