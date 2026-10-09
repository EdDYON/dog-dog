package com.EdDYON.dogdog.common.attachment;

import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum PetPersonality implements StringRepresentable {
    NONE("none"),
    AGGRESSIVE("aggressive"),
    TANK("tank"),
    ASSASSIN("assassin"),
    VAMPIRE("vampire"),
    SENTRY("sentry"),
    HEALER("healer"),
    COWARD("coward"),
    AMPHIBIAN("amphibian"),
    HUSKY("husky"),
    GLUTTON("glutton"),
    DIGGER("digger"),
    LAZY("lazy"),
    CLINGY("clingy");

    private final String name;

    // 缓存一个查找表，提高查找效率
    private static final Map<String, PetPersonality> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toMap(PetPersonality::getSerializedName, Function.identity()));

    PetPersonality(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    // 简单的随机获取方法
    public static PetPersonality getRandom() {
        // 排除 NONE (索引0)
        return values()[1 + (int)(Math.random() * (values().length - 1))];
    }

    // 🔥 修复：补上缺失的 byName 方法
    public static PetPersonality byName(String name) {
        return BY_NAME.getOrDefault(name, NONE);
    }
}
