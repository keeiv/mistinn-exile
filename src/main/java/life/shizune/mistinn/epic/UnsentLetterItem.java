package life.shizune.mistinn.epic;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * 沒寄出的信: one of the twelve letters No. 3412 wrote to Xiaoling over the years and never sent. The fog carried them
 * off from the inn, and Udasru's people keep what the fog brings as letters from the fog: in the bell towers' belfries,
 * the Great Library, the towns. Read (used), it is read aloud, and the epic counts it found.
 */
public class UnsentLetterItem extends Item {
	/** How many there are. */
	public static final int LETTERS = 12;

	public UnsentLetterItem(final Properties properties) {
		super(properties);
	}

	/** The letter with this number, its words on it. */
	public static ItemStack letter(final int number) {
		ItemStack stack = new ItemStack(MistinnItems.UNSENT_LETTER);
		CompoundTag tag = new CompoundTag();
		tag.putInt("letter", number);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.mistinn.unsent_letter.numbered", number + 1));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("story.mistinn.radio.epic_letter_" + number + ".0").withStyle(s -> s.withColor(0x9A968E).withItalic(true)));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	/** Which letter a stack is, or -1. */
	public static int number(final ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? -1 : data.copyTag().getIntOr("letter", -1);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer hunter) {
			int number = number(player.getItemInHand(hand));
			if (number >= 0 && number < LETTERS) {
				EpicService.letterRead(hunter, number);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
