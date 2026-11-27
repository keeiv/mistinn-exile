package life.shizune.mistinn.path;

import java.util.List;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.terra.Settlements;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The way, laid in stone: for each tiller who falls on it, a post with a soul lantern and a sign
 * bearing their name is set at the homestead, one after another from the hub out toward the
 * highland's edge (the way out, toward the sea). The more who fall, the longer it runs.
 */
public final class PathMemorials {
	/** From the hub: where the first stands (past the cleared ground round the deck), and the gap between them. */
	public static final int FIRST = Settlements.CLEAR + 2;
	public static final int GAP = 3;
	/** How far along a memorial may shift to find open ground. */
	private static final int SHIFT = 4;

	private PathMemorials() {
	}

	/** Which way the memorials run from the hub: away from the highland's heart. */
	public static double[] outward(final Settlements.Settlement home, final Domain domain) {
		double dx = home.hub().getX() - domain.centre().getX();
		double dz = home.hub().getZ() - domain.centre().getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		return length < 1.0 ? new double[] {1.0, 0.0} : new double[] {dx / length, dz / length};
	}

	/** Sets up the memorial of the {@code index}th to fall (from 0), who was {@code walker}; where its sign stands, or null if no ground would take it. */
	public static @Nullable BlockPos raise(final ServerLevel level, final Settlements.Settlement home, final Domain domain, final int index, final int walker) {
		double[] out = outward(home, domain);
		for (int shift = 0; shift <= SHIFT; shift++) {
			double far = FIRST + index * GAP + shift;
			int x = Mth.floor(home.hub().getX() + 0.5 + out[0] * far);
			int z = Mth.floor(home.hub().getZ() + 0.5 + out[1] * far);
			level.getChunk(x >> 4, z >> 4);
			BlockPos post = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			// the sign stands a step back toward the hub, read on the way out
			int sx = Mth.floor(home.hub().getX() + 0.5 + out[0] * (far - 1.0));
			int sz = Mth.floor(home.hub().getZ() + 0.5 + out[1] * (far - 1.0));
			BlockPos sign = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(sx, 0, sz));
			if (sign.equals(post) || !open(level, post) || !open(level, post.above()) || !open(level, sign) || Math.abs(sign.getY() - post.getY()) > 2) {
				continue;
			}
			level.setBlock(post, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState(), 3);
			level.setBlock(post.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 3);
			float facing = (float) Math.toDegrees(Math.atan2(-out[0], out[1])) + 180.0F;
			level.setBlock(sign, Blocks.PALE_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing)), 3);
			if (level.getBlockEntity(sign) instanceof SignBlockEntity board) {
				board.setText(text(index, walker), SignTextSlot.FRONT);
				board.setText(text(index, walker), SignTextSlot.BACK);
				board.setWaxed(true);
				board.setChanged();
				level.sendBlockUpdated(sign, level.getBlockState(sign), level.getBlockState(sign), 3);
			}
			return sign;
		}
		return null;
	}

	/** What a memorial says: whose it is, and how many fell before. */
	public static SignText text(final int index, final int walker) {
		List<Component> lines = List.of(Component.empty(), PathService.name(walker).copy(), Component.translatable("path.mistinn.memorial", index + 1),
			Component.empty());
		return new SignText(lines, lines, DyeColor.LIGHT_BLUE, true);
	}

	private static boolean open(final ServerLevel level, final BlockPos pos) {
		return level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos).getFluidState().isEmpty();
	}
}
