package life.shizune.mistinn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 中繼塔: a crystal tillers' relay pylon, stood in three kinds of piece: a base on four struts
 * with glowing feet, a lattice mast (stacked as tall as the pylon is), and a head of crossarms
 * hung with ceramic insulators round a crystal emitter. Its crystal light wakes with the hub.
 */
public class RelayPylonBlock extends Block {
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape BASE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
	private static final VoxelShape MAST = Block.box(4.5, 0.0, 4.5, 11.5, 16.0, 11.5);
	private static final VoxelShape HEAD = Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0);

	/** The pieces a pylon is stood in. */
	public enum Part implements StringRepresentable {
		BASE("base"), MAST("mast"), HEAD("head");

		private final String id;

		Part(final String id) {
			this.id = id;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}
	}

	public RelayPylonBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PART, Part.MAST).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, LIT);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return switch (state.getValue(PART)) {
			case BASE -> BASE;
			case MAST -> MAST;
			case HEAD -> HEAD;
		};
	}
}
