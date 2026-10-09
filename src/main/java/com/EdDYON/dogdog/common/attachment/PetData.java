package com.EdDYON.dogdog.common.attachment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

public class PetData {
    private PetPersonality personality = PetPersonality.NONE;
    private int appearanceStyle = -1;
    private String generatedName = "";
    private String playerGivenName = "";
    private boolean isHomeMode = false;
    private BlockPos homePos = BlockPos.ZERO;
    private int affinity = 0;
    private int mood = 55;
    private int strain = 0;
    private boolean awakened = false;
    private boolean isCritical = false;
    private boolean petrified = false;
    private int criticalTicks = 0;
    private long diggerHeistCooldownUntil = 0L;

    public PetPersonality getPersonality() { return personality; }
    public void setPersonality(PetPersonality personality) { this.personality = personality; }

    public int getAppearanceStyle() { return appearanceStyle; }
    public void setAppearanceStyle(int appearanceStyle) { this.appearanceStyle = Math.max(-1, appearanceStyle); }

    public String getGeneratedName() { return generatedName; }
    public void setGeneratedName(String generatedName) { this.generatedName = generatedName == null ? "" : generatedName.trim(); }

    public String getPlayerGivenName() { return playerGivenName; }
    public void setPlayerGivenName(String playerGivenName) { this.playerGivenName = playerGivenName == null ? "" : playerGivenName.trim(); }
    public boolean hasPlayerGivenName() { return !playerGivenName.isBlank(); }

    public boolean isHomeMode() { return isHomeMode; }
    public void setHomeMode(boolean homeMode) { this.isHomeMode = homeMode; }

    public BlockPos getHomePos() { return homePos; }
    public void setHomePos(BlockPos homePos) { this.homePos = homePos; }

    public int getAffinity() { return affinity; }
    public void setAffinity(int affinity) { this.affinity = Mth.clamp(affinity, 0, 100); }
    public void increaseAffinity(int amount) { setAffinity(this.affinity + amount); }
    public void decreaseAffinity(int amount) { setAffinity(this.affinity - amount); }

    public int getMood() { return mood; }
    public void setMood(int mood) { this.mood = Mth.clamp(mood, 0, 100); }
    public void increaseMood(int amount) { setMood(this.mood + amount); }
    public void decreaseMood(int amount) { setMood(this.mood - amount); }

    public int getStrain() { return strain; }
    public void setStrain(int strain) { this.strain = Mth.clamp(strain, 0, 100); }
    public void increaseStrain(int amount) { setStrain(this.strain + amount); }
    public void decreaseStrain(int amount) { setStrain(this.strain - amount); }

    public boolean isAwakened() { return awakened; }
    public void setAwakened(boolean awakened) { this.awakened = awakened; }

    public boolean isCritical() { return isCritical; }
    public void setCritical(boolean critical) {
        this.isCritical = critical;
        if (!critical) {
            this.criticalTicks = 0;
        }
    }

    public boolean isPetrified() { return petrified; }
    public void setPetrified(boolean petrified) { this.petrified = petrified; }

    public int getCriticalTicks() { return criticalTicks; }
    public void tickCritical() { this.criticalTicks++; }

    public long getDiggerHeistCooldownUntil() { return diggerHeistCooldownUntil; }
    public void setDiggerHeistCooldownUntil(long diggerHeistCooldownUntil) {
        this.diggerHeistCooldownUntil = Math.max(0L, diggerHeistCooldownUntil);
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Personality", personality.getSerializedName());
        tag.putInt("AppearanceStyle", appearanceStyle);
        tag.putString("GeneratedName", generatedName);
        tag.putString("PlayerGivenName", playerGivenName);
        tag.putBoolean("IsHomeMode", isHomeMode);
        tag.putInt("HomeX", homePos.getX());
        tag.putInt("HomeY", homePos.getY());
        tag.putInt("HomeZ", homePos.getZ());
        tag.putInt("Affinity", affinity);
        tag.putInt("Mood", mood);
        tag.putInt("Strain", strain);
        tag.putBoolean("Awakened", awakened);
        tag.putBoolean("IsCritical", isCritical);
        tag.putBoolean("Petrified", petrified);
        tag.putInt("CriticalTicks", criticalTicks);
        tag.putLong("DiggerHeistCooldownUntil", diggerHeistCooldownUntil);
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("Personality")) this.personality = PetPersonality.byName(tag.getString("Personality"));
        if (tag.contains("AppearanceStyle")) this.appearanceStyle = Math.max(-1, tag.getInt("AppearanceStyle"));
        if (tag.contains("GeneratedName")) this.generatedName = tag.getString("GeneratedName");
        if (tag.contains("PlayerGivenName")) this.playerGivenName = tag.getString("PlayerGivenName");
        if (tag.contains("IsHomeMode")) this.isHomeMode = tag.getBoolean("IsHomeMode");
        if (tag.contains("HomeX")) this.homePos = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        if (tag.contains("Affinity")) this.affinity = tag.getInt("Affinity");
        if (tag.contains("Mood")) this.mood = Mth.clamp(tag.getInt("Mood"), 0, 100);
        if (tag.contains("Strain")) this.strain = Mth.clamp(tag.getInt("Strain"), 0, 100);
        if (tag.contains("Awakened")) this.awakened = tag.getBoolean("Awakened");
        if (tag.contains("IsCritical")) this.isCritical = tag.getBoolean("IsCritical");
        if (tag.contains("Petrified")) this.petrified = tag.getBoolean("Petrified");
        if (tag.contains("CriticalTicks")) this.criticalTicks = tag.getInt("CriticalTicks");
        if (tag.contains("DiggerHeistCooldownUntil")) this.diggerHeistCooldownUntil = Math.max(0L, tag.getLong("DiggerHeistCooldownUntil"));
    }
}
