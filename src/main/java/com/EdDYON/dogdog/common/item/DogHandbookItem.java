package com.EdDYON.dogdog.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DogHandbookItem extends Item {
    private static final List<HandbookPage> PAGES = List.of(
            new HandbookPage(
                    ChatFormatting.GOLD,
                    "先认脾气",
                    "先别急着盯数值。亲密度像交情，心情像今天的状态，透支高了就该让它歇一歇。\n\n会养狗的人不是只会刷一个数字，而是知道它什么时候想跟你走，什么时候已经累了。",
                    "Read The Mood",
                    "Do not stare at the numbers first. Affinity is trust, mood is today's energy, and high strain means the dog needs a break.\n\nGood caretakers do not grind one stat. They learn when a dog is eager, and when it is already worn out."
            ),
            new HandbookPage(
                    ChatFormatting.AQUA,
                    "平时怎么带",
                    "空手摸一摸，会让它安心，也能顺手看档案。潜行空手互动会切换住家和跟随。命名牌是正式改名，金苹果是救急，黄金骨头是感情够深以后再谈觉醒。",
                    "Daily Handling",
                    "Pet with an empty hand to calm the dog and check its profile. Sneak with an empty hand to switch between home mode and follow mode. Name tags are for real names, golden apples are for emergencies, and the Golden Bone is something to use only after a deep bond."
            ),
            new HandbookPage(
                    ChatFormatting.RED,
                    "前排那几只",
                    "狂暴者喜欢狠狠干仗，重装坦克适合顶在前面吃伤害。幽灵刺客吃节奏，越是夜里越顺手，吸血伯爵则更适合拉长战线，慢慢把局势打回来。",
                    "Frontliners",
                    "Aggressive dogs like hard fights. Tanks belong in front taking hits. Assassins love timing and darkness, while Vampires are happier in longer fights where they can slowly drag things back in your favor."
            ),
            new HandbookPage(
                    ChatFormatting.GREEN,
                    "照顾人的那几只",
                    "治疗天使平时不吵，关键时候最靠谱。胆小鬼平时会缩，但真到要命的时候反而舍得扑出去。黏人精很好懂，你带它多久，它就认你多深。警卫一有家就来劲。",
                    "The Protective Ones",
                    "Healers stay quiet until the moment you really need them. Cowards hesitate early, then surprise you when things turn desperate. Clingy dogs bond through time spent together, and Sentries come alive once they have a home to watch."
            ),
            new HandbookPage(
                    ChatFormatting.BLUE,
                    "爱折腾那几只",
                    "两栖猎手一到水边就舒服，寻宝专家别老关屋里，草地和远路才让它开心。哈士奇脑子里总有怪点子，贪吃鬼最好哄，懒狗慢热，普通狗反而常常最稳。",
                    "The Restless Ones",
                    "Amphibians belong near water. Diggers should not spend life indoors. Huskies always have ideas, Gluttons are easy to win over, Lazy dogs warm up slowly, and Plain Dogs are often the steadiest companions of all."
            ),
            new HandbookPage(
                    ChatFormatting.LIGHT_PURPLE,
                    "养法其实不难",
                    "想让它认你，就多带着走。想让它状态好，就记得喂、记得摸、记得让它休息。\n\n会守家的就让它守家，爱下水的就多去水边，爱挖宝的就别闷在院子里。养到最后，记住的往往不是数值，而是它到底是什么脾气。",
                    "Raising Them Well",
                    "If you want trust, travel together. If you want good condition, feed them, pet them, and let them rest.\n\nLet home-loving dogs guard the base, take water lovers to the shore, and stop trapping treasure dogs in the yard. In the end, what you remember is not the stat line, but the personality."
            )
    );

    public DogHandbookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            openHandbook(serverPlayer, heldStack, hand);
        }
        return InteractionResultHolder.sidedSuccess(heldStack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            openHandbook(serverPlayer, context.getItemInHand(), context.getHand());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    private void openHandbook(ServerPlayer serverPlayer, ItemStack heldStack, InteractionHand hand) {
        heldStack.set(DataComponents.WRITTEN_BOOK_CONTENT, createBookContent(serverPlayer));
        serverPlayer.containerMenu.broadcastChanges();
        serverPlayer.connection.send(new ClientboundOpenBookPacket(hand));
        serverPlayer.awardStat(Stats.ITEM_USED.get(this));
    }

    private static WrittenBookContent createBookContent(ServerPlayer player) {
        List<Filterable<Component>> pages = new ArrayList<>(PAGES.size());
        boolean chinese = isChinese(player);
        for (HandbookPage page : PAGES) {
            pages.add(Filterable.passThrough(buildPage(page, chinese)));
        }

        return new WrittenBookContent(
                Filterable.passThrough(chinese ? "狗狗培养手册" : "Dog Handbook"),
                "EdDYON",
                0,
                pages,
                true
        );
    }

    private static Component buildPage(HandbookPage page, boolean chinese) {
        MutableComponent title = Component.literal(chinese ? page.zhTitle() : page.enTitle())
                .withStyle(page.color(), ChatFormatting.BOLD);
        Component body = Component.literal(chinese ? page.zhBody() : page.enBody());
        return Component.empty()
                .append(title)
                .append(Component.literal("\n\n"))
                .append(body);
    }

    private static boolean isChinese(ServerPlayer player) {
        return player.getLanguage().toLowerCase(Locale.ROOT).startsWith("zh");
    }

    private record HandbookPage(ChatFormatting color, String zhTitle, String zhBody, String enTitle, String enBody) {
    }
}
