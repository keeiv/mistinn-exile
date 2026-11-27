package life.shizune.mistinn.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 工坊牆: a piece of a crystal tillers' workshop, its detail on the side it faces: a ribbed panel
 * with a vent, a framed window with a sill and a shade, a chamfered corner post, or the lintel
 * over the doorway with its canopy and sign. The windows, the corner strips and the canopy light
 * with the hub.
 */
public class WorkshopWallBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** The pieces a workshop's walls are put up from. */
	public enum Part implements StringRepresentable {
		WALL("wall"), WINDOW("window"), CORNER("corner"), LINTEL("lintel");

		private final String id;

		Part(final String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}
	}

	public WorkshopWallBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.WALL).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART, LIT);
	}
}
