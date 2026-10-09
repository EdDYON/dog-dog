package com.EdDYON.dogdog.common.util;

import com.EdDYON.dogdog.common.attachment.PetPersonality;
import net.minecraft.util.RandomSource;

public final class DogNameGenerator {
    private static final String[] GENERAL_FOODY = {
            "团子", "奶糖", "麻薯", "布丁", "豆包", "糯米", "可可", "栗子", "泡芙", "年糕",
            "奶豆", "饭团", "桃桃", "云朵", "点点", "斑斑"
    };

    private static final String[] GENERAL_SIGNATURE = {
            "星点", "霜尾", "云岚", "雪团", "月牙", "琥珀", "青团", "墨灯", "栗风", "蓝豆"
    };

    private DogNameGenerator() {
    }

    public static String generateName(PetPersonality personality, int appearanceStyle, RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 32) {
            return duplicateRoot(pickRoot(personality, appearanceStyle, random));
        }
        if (roll < 57) {
            return withPrefix(pickRoot(personality, appearanceStyle, random), random);
        }
        if (roll < 82) {
            return pick(foodPool(personality, appearanceStyle), random);
        }
        if (roll < 95) {
            return pick(personalityPool(personality), random);
        }
        return pick(GENERAL_SIGNATURE, random);
    }

    private static String duplicateRoot(String root) {
        return root + root;
    }

    private static String withPrefix(String root, RandomSource random) {
        return (random.nextBoolean() ? "小" : "阿") + root;
    }

    private static String pickRoot(PetPersonality personality, int appearanceStyle, RandomSource random) {
        String[] styleRoots = appearanceRoots(appearanceStyle);
        String[] personalityRoots = personalityRoots(personality);
        if (random.nextInt(5) < 3) {
            return pick(styleRoots, random);
        }
        return pick(personalityRoots, random);
    }

    private static String[] appearanceRoots(int appearanceStyle) {
        return switch (Math.max(0, appearanceStyle)) {
            case 0 -> new String[]{"团", "糯", "豆", "云", "雪", "糖"};
            case 1 -> new String[]{"栗", "茶", "麦", "糖", "可", "米"};
            case 2 -> new String[]{"雪", "霜", "云", "蓝", "银", "灰"};
            case 3 -> new String[]{"墨", "灰", "炭", "乌", "影", "煤"};
            case 4 -> new String[]{"星", "月", "琥", "霜", "银", "岚"};
            case 5 -> new String[]{"斑", "点", "豆", "花", "星", "栗"};
            default -> new String[]{"琥", "星", "月", "蓝", "墨", "霜"};
        };
    }

    private static String[] personalityRoots(PetPersonality personality) {
        return switch (personality) {
            case AGGRESSIVE -> new String[]{"牙", "炭", "乌", "烈", "锋", "墨"};
            case TANK -> new String[]{"墩", "石", "厚", "熊", "麦", "盾"};
            case ASSASSIN -> new String[]{"影", "夜", "墨", "乌", "岚", "刃"};
            case VAMPIRE -> new String[]{"月", "夜", "霜", "赤", "红", "墨"};
            case SENTRY -> new String[]{"守", "巡", "铃", "哨", "灯", "垒"};
            case HEALER -> new String[]{"奶", "暖", "糯", "晴", "云", "露"};
            case COWARD -> new String[]{"软", "乖", "团", "慢", "豆", "云"};
            case AMPHIBIAN -> new String[]{"潮", "泡", "浪", "蓝", "霜", "雨"};
            case HUSKY -> new String[]{"闹", "哈", "雪", "豆", "团", "风"};
            case GLUTTON -> new String[]{"团", "饭", "糖", "糯", "豆", "栗"};
            case DIGGER -> new String[]{"土", "煤", "栗", "砂", "灰", "矿"};
            case LAZY -> new String[]{"呼", "慢", "团", "暖", "糯", "云"};
            case CLINGY -> new String[]{"抱", "贴", "乖", "奶", "团", "宝"};
            case NONE -> new String[]{"团", "灰", "雪", "栗", "豆", "云"};
        };
    }

    private static String[] foodPool(PetPersonality personality, int appearanceStyle) {
        String[] stylePool = switch (Math.max(0, appearanceStyle)) {
            case 0 -> new String[]{"团子", "糯糯", "奶豆", "豆包", "云朵"};
            case 1 -> new String[]{"栗子", "茶茶", "可可", "麦麦", "奶糖"};
            case 2 -> new String[]{"小雪", "雪团", "霜霜", "云朵", "蓝豆"};
            case 3 -> new String[]{"阿灰", "灰灰", "墨墨", "煤球", "乌豆"};
            case 4 -> new String[]{"星点", "月牙", "琥珀", "银铃", "霜尾"};
            case 5 -> new String[]{"点点", "斑斑", "星豆", "花花", "豆点"};
            default -> new String[]{"霜尾", "月牙", "蓝豆", "墨灯", "琥珀"};
        };
        String[] personalityPool = personalityPool(personality);
        return merge(stylePool, personalityPool, GENERAL_FOODY);
    }

    private static String[] personalityPool(PetPersonality personality) {
        return switch (personality) {
            case AGGRESSIVE -> new String[]{"牙牙", "炭炭", "乌豆", "阿锋", "烈烈"};
            case TANK -> new String[]{"墩墩", "阿石", "熊熊", "厚厚", "麦墩"};
            case ASSASSIN -> new String[]{"影子", "阿影", "墨影", "小夜", "乌豆"};
            case VAMPIRE -> new String[]{"月牙", "红豆", "夜豆", "霜牙", "墨月"};
            case SENTRY -> new String[]{"守守", "阿巡", "铃铛", "小哨", "巡巡"};
            case HEALER -> new String[]{"奶糖", "暖暖", "糯糯", "团子", "小晴"};
            case COWARD -> new String[]{"乖乖", "软软", "慢慢", "团团", "小云"};
            case AMPHIBIAN -> new String[]{"泡泡", "小潮", "蓝豆", "阿浪", "雨点"};
            case HUSKY -> new String[]{"闹闹", "雪球", "哈哈", "团团", "豆包"};
            case GLUTTON -> new String[]{"团子", "饭团", "豆包", "奶糖", "糯米"};
            case DIGGER -> new String[]{"煤球", "阿土", "栗子", "土豆", "灰豆"};
            case LAZY -> new String[]{"呼呼", "慢慢", "团团", "糯糯", "暖暖"};
            case CLINGY -> new String[]{"抱抱", "贴贴", "乖宝", "团团", "奶豆"};
            case NONE -> new String[]{"团子", "阿灰", "栗栗", "奶糖", "小雪"};
        };
    }

    private static String[] merge(String[] first, String[] second, String[] third) {
        String[] merged = new String[first.length + second.length + third.length];
        int index = 0;
        for (String value : first) merged[index++] = value;
        for (String value : second) merged[index++] = value;
        for (String value : third) merged[index++] = value;
        return merged;
    }

    private static String pick(String[] pool, RandomSource random) {
        return pool[random.nextInt(pool.length)];
    }
}
