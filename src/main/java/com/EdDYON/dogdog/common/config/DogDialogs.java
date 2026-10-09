package com.EdDYON.dogdog.common.config;

import net.minecraft.util.RandomSource;

public class DogDialogs {

    private static final String[] COWARD_SAVE_LINES = {
            "这一次换我来保护你。",
            "别怕，我在你身前。",
            "其实……我也能变得很勇敢，对吧？"
    };

    private static final String[] ANGEL_SAVE_LINES = {
            "把你的痛苦都交给我吧……",
            "只要你平安无事就好。",
            "能够治愈你，是我最大的幸福。"
    };

    private static final String[] BERSERKER_REVENGE_LINES = {
            "谁也别想碰我的主人！",
            "就算一起下地狱，我也要拖上你！",
            "我要把你撕成碎片！"
    };

    private static final String[] SCOUT_GIFT_LINES = {
            "汪，这是我能找到的最像样的战利品了。",
            "拿着吧，主人，我可是费了好大劲。",
            "别小看我，我可是会往家里带宝贝的。"
    };

    private static final String[] CRITICAL_WHISPERS = {
            "主人……我好累……",
            "小狗希望你能活下去……",
            "我还能撑一会，再陪陪我……",
            "对不起，不能再陪你冒险了……",
            "别看我现在的样子，很狼狈吧……"
    };

    private static final String[] STONE_LINES = {
            "别往心里去，我会一直陪着你。",
            "只是睡一觉而已，别哭。",
            "谢谢你给我的家。",
            "能遇到你，是我最幸运的事。",
            "带上我的那一份，继续去冒险吧。"
    };

    private static final String[] RECOVER_LINES = {
            "我还能再陪你走一段路。",
            "只要你在，我就不会倒下。",
            "谢谢你，没有放弃我。",
            "伤口一点都不痛了！",
            "我又可以保护你了！"
    };

    private static final String[] AFFINITY_MAX_LINES = {
            "从今以后，我的生命与你相连。",
            "只要你在，我便无所畏惧。",
            "我已经完全信任你了，主人。",
            "能做你的修勾，真的很开心。",
            "我们再也不会分开了，对吧？"
    };

    private static final String[] GLUTTON_SHARE_LINES = {
            "吃饱才有力气，我分你一点。",
            "别饿着，跟着我一起满血上阵。",
            "我这口福气，给主人也留了一份。"
    };

    private static final String[] HUSKY_GIFT_LINES = {
            "快看，我叼了个好东西回来！",
            "这个归你，别问我是从哪儿弄来的。",
            "我出去疯了一圈，顺便给你带了礼物。"
    };

    private static final String[] SENTRY_ALERT_LINES = {
            "我盯着呢，别让它靠近你。",
            "前面有动静，我先替你记下了。",
            "放心往前，我会替你看着四周。"
    };

    private static final String[] AMPHIBIAN_RESCUE_LINES = {
            "深呼吸，我把你带上去。",
            "别慌，水里是我的主场。",
            "抓紧我，我们一起浮上去。"
    };

    private static final String[] VAMPIRE_NIGHT_LINES = {
            "今晚先借你一点命。",
            "夜色正浓，让我替你续一口气。",
            "别倒下，月亮还没落呢。"
    };

    private static final String[] ASSASSIN_SHADOW_LINES = {
            "别出声，我先盯上它。",
            "看好，我会从影子里结束它。",
            "下一刀会很安静。"
    };

    public static String getCowardSave(RandomSource random) {
        return COWARD_SAVE_LINES[random.nextInt(COWARD_SAVE_LINES.length)];
    }

    public static String getAngelSave(RandomSource random) {
        return ANGEL_SAVE_LINES[random.nextInt(ANGEL_SAVE_LINES.length)];
    }

    public static String getBerserkerRevenge(RandomSource random) {
        return BERSERKER_REVENGE_LINES[random.nextInt(BERSERKER_REVENGE_LINES.length)];
    }

    public static String getScoutGift(RandomSource random) {
        return SCOUT_GIFT_LINES[random.nextInt(SCOUT_GIFT_LINES.length)];
    }

    public static String getCriticalWhisper(RandomSource random) {
        return CRITICAL_WHISPERS[random.nextInt(CRITICAL_WHISPERS.length)];
    }

    public static String getStone(RandomSource random) {
        return STONE_LINES[random.nextInt(STONE_LINES.length)];
    }

    public static String getRecover(RandomSource random) {
        return RECOVER_LINES[random.nextInt(RECOVER_LINES.length)];
    }

    public static String getAffinityMax(RandomSource random) {
        return AFFINITY_MAX_LINES[random.nextInt(AFFINITY_MAX_LINES.length)];
    }

    public static String getGluttonShare(RandomSource random) {
        return GLUTTON_SHARE_LINES[random.nextInt(GLUTTON_SHARE_LINES.length)];
    }

    public static String getHuskyGift(RandomSource random) {
        return HUSKY_GIFT_LINES[random.nextInt(HUSKY_GIFT_LINES.length)];
    }

    public static String getSentryAlert(RandomSource random) {
        return SENTRY_ALERT_LINES[random.nextInt(SENTRY_ALERT_LINES.length)];
    }

    public static String getAmphibianRescue(RandomSource random) {
        return AMPHIBIAN_RESCUE_LINES[random.nextInt(AMPHIBIAN_RESCUE_LINES.length)];
    }

    public static String getVampireNight(RandomSource random) {
        return VAMPIRE_NIGHT_LINES[random.nextInt(VAMPIRE_NIGHT_LINES.length)];
    }

    public static String getAssassinShadow(RandomSource random) {
        return ASSASSIN_SHADOW_LINES[random.nextInt(ASSASSIN_SHADOW_LINES.length)];
    }
}
