package life.shizune.mistinn.client.gear;

import net.minecraft.client.model.geom.builders.CubeListBuilder;

/**
 * The worn shapes of the light and heavy lines' backpacks and the heavy chest rigs, written by the
 * gear lines' catalog (box by box, each on its own sheet in the vanilla box layout); index 0 is the
 * first piece after the standard issue in {@code Gear.BACKPACKS} or {@code Gear.RIGS}.
 */
final class GearLines {
	private GearLines() {
	}

	/** The new backpacks' shapes: the light line N to VI, then the heavy line. */
	static CubeListBuilder backpack(final int index) {
		return switch (index) {
			// 拾荒腰包 (backpack_light_n)
			case 0 -> CubeListBuilder.create()
				// pouch
				.texOffs(0, 0).addBox(-2.5F, 8.0F, 2.5F, 5, 3, 2)
				// cup
				.texOffs(14, 0).addBox(2.5F, 7.5F, 2.5F, 2, 2, 2);
			// 漁夫斜背包 (backpack_light_1)
			case 1 -> CubeListBuilder.create()
				// bag
				.texOffs(0, 0).addBox(-2.0F, 4.0F, 2.5F, 5, 5, 2)
				// flap
				.texOffs(14, 0).addBox(-2.0F, 3.0F, 2.5F, 5, 1, 3)
				// float
				.texOffs(30, 0).addBox(3.0F, 3.5F, 3.0F, 2, 2, 1);
			// 斥潮快取包 (backpack_light_2)
			case 2 -> CubeListBuilder.create()
				// main
				.texOffs(0, 0).addBox(-3.0F, 2.0F, 2.5F, 6, 7, 2)
				// top_left
				.texOffs(24, 0).addBox(-3.0F, 1.0F, 2.5F, 3, 1, 2)
				// top_right
				.texOffs(34, 0).addBox(0.0F, 1.0F, 2.5F, 3, 1, 2)
				// bottle
				.texOffs(16, 0).addBox(3.0F, 4.5F, 2.5F, 2, 4, 2);
			// 錨衛突擊包 (backpack_light_3)
			case 3 -> CubeListBuilder.create()
				// main
				.texOffs(0, 0).addBox(-3.5F, 1.5F, 2.5F, 7, 8, 3)
				// front
				.texOffs(20, 0).addBox(-2.5F, 4.5F, 5.5F, 5, 4, 1)
				// handle
				.texOffs(32, 0).addBox(-1.0F, 0.5F, 3.0F, 2, 1, 2);
			// 霧隱輕裝包 (backpack_light_4)
			case 4 -> CubeListBuilder.create()
				// main
				.texOffs(0, 0).addBox(-3.5F, 1.0F, 2.5F, 7, 9, 2)
				// top_left
				.texOffs(18, 0).addBox(-3.5F, 0.0F, 2.5F, 2, 1, 2)
				// top_middle
				.texOffs(26, 0).addBox(-1.0F, 0.0F, 2.5F, 2, 1, 2)
				// top_right
				.texOffs(34, 0).addBox(1.5F, 0.0F, 2.5F, 2, 1, 2);
			// 鐘聲傳令包 (backpack_light_5)
			case 5 -> CubeListBuilder.create()
				// satchel
				.texOffs(8, 0).addBox(-4.0F, 3.0F, 2.5F, 8, 7, 3)
				// tube
				.texOffs(0, 0).addBox(3.5F, -1.0F, 3.0F, 2, 9, 2)
				// bell
				.texOffs(30, 0).addBox(-1.0F, 10.0F, 5.5F, 2, 2, 1);
			// 鏽羽骸囊 (backpack_light_6)
			case 6 -> CubeListBuilder.create()
				// pouch
				.texOffs(0, 0).addBox(-3.5F, 2.0F, 2.5F, 7, 8, 2)
				// feather_left
				.texOffs(22, 0).addBox(-4.5F, -3.0F, 3.0F, 1, 8, 1)
				// feather_middle
				.texOffs(18, 0).addBox(-0.5F, -4.0F, 3.0F, 1, 9, 1)
				// feather_right
				.texOffs(26, 0).addBox(3.5F, -3.0F, 3.0F, 1, 8, 1);
			// 廢鐵貨架 (backpack_heavy_n)
			case 7 -> CubeListBuilder.create()
				// frame
				.texOffs(0, 0).addBox(-4.0F, -1.0F, 2.5F, 8, 13, 1)
				// load
				.texOffs(18, 0).addBox(-3.5F, 0.0F, 3.5F, 7, 9, 4)
				// pipe
				.texOffs(40, 0).addBox(3.5F, -3.0F, 4.0F, 1, 6, 1);
			// 裝卸工大麻袋 (backpack_heavy_1)
			case 8 -> CubeListBuilder.create()
				// sack
				.texOffs(0, 0).addBox(-4.5F, -1.0F, 2.5F, 9, 12, 5)
				// tie
				.texOffs(32, 0).addBox(-1.5F, -2.0F, 4.0F, 3, 1, 2)
				// hook
				.texOffs(28, 0).addBox(4.5F, 2.0F, 4.5F, 1, 3, 1);
			// 堤防補給包 (backpack_heavy_2)
			case 9 -> CubeListBuilder.create()
				// main
				.texOffs(0, 0).addBox(-4.5F, -0.5F, 2.5F, 9, 11, 4)
				// bedroll
				.texOffs(34, 0).addBox(-4.0F, -2.5F, 3.0F, 8, 2, 3)
				// can
				.texOffs(26, 0).addBox(4.5F, 3.0F, 3.0F, 2, 5, 2);
			// 錨衛攻城包 (backpack_heavy_3)
			case 10 -> CubeListBuilder.create()
				// main
				.texOffs(0, 0).addBox(-4.5F, -1.0F, 2.5F, 9, 12, 5)
				// side_left
				.texOffs(28, 0).addBox(-6.5F, 3.0F, 3.5F, 2, 5, 3)
				// side_right
				.texOffs(38, 0).addBox(4.5F, 3.0F, 3.5F, 2, 5, 3)
				// lid
				.texOffs(0, 17).addBox(-4.0F, -2.0F, 3.0F, 8, 1, 4);
			// 霧牆遠征包 (backpack_heavy_4)
			case 11 -> CubeListBuilder.create()
				// main
				.texOffs(0, 0).addBox(-5.0F, -2.0F, 2.5F, 10, 13, 5)
				// bedroll
				.texOffs(30, 0).addBox(-4.5F, -4.0F, 3.0F, 9, 2, 3)
				// lamp
				.texOffs(54, 0).addBox(-1.0F, 4.0F, 7.5F, 2, 3, 1);
			// 鐘樓負重架 (backpack_heavy_5)
			case 12 -> CubeListBuilder.create()
				// rail_left
				.texOffs(0, 0).addBox(-5.0F, -4.0F, 2.5F, 1, 17, 1)
				// rail_right
				.texOffs(4, 0).addBox(4.0F, -4.0F, 2.5F, 1, 17, 1)
				// bar
				.texOffs(0, 18).addBox(-5.0F, -5.0F, 2.5F, 10, 1, 1)
				// load
				.texOffs(8, 0).addBox(-4.0F, -2.0F, 3.5F, 8, 11, 5)
				// bell
				.texOffs(34, 0).addBox(-1.5F, 9.5F, 8.5F, 3, 3, 2);
			// 鏽棺 (backpack_heavy_6)
			case 13 -> CubeListBuilder.create()
				// coffin
				.texOffs(0, 0).addBox(-4.5F, -4.0F, 2.5F, 9, 17, 4)
				// lid
				.texOffs(26, 0).addBox(-4.0F, -3.5F, 6.5F, 8, 16, 1)
				// chain
				.texOffs(44, 0).addBox(4.5F, 2.0F, 3.0F, 1, 6, 1);
			default -> CubeListBuilder.create();
		};
	}

	/** The heavy chest rigs' shapes, N to VI. */
	static CubeListBuilder rig(final int index) {
		return switch (index) {
			// 廢鐵工具掛 (rig_heavy_n)
			case 0 -> CubeListBuilder.create()
				// strap_left
				.texOffs(0, 0).addBox(-3.5F, 0.0F, -3.0F, 1, 10, 1)
				// strap_right
				.texOffs(4, 0).addBox(2.5F, 0.0F, -3.0F, 1, 10, 1)
				// belt
				.texOffs(30, 0).addBox(-4.5F, 9.0F, -3.0F, 9, 2, 1)
				// mag_0
				.texOffs(12, 0).addBox(-3.5F, 6.0F, -4.0F, 2, 3, 1)
				// mag_1
				.texOffs(18, 0).addBox(-1.0F, 6.0F, -4.0F, 2, 3, 1)
				// mag_2
				.texOffs(24, 0).addBox(1.5F, 6.0F, -4.0F, 2, 3, 1)
				// wrench
				.texOffs(8, 0).addBox(3.5F, 2.5F, -4.0F, 1, 4, 1);
			// 裝卸工工具背心 (rig_heavy_1)
			case 1 -> CubeListBuilder.create()
				// front
				.texOffs(0, 0).addBox(-4.5F, 0.5F, -3.0F, 9, 10, 1)
				// back
				.texOffs(20, 0).addBox(-4.5F, 0.5F, 2.0F, 9, 10, 1)
				// strap_left
				.texOffs(40, 0).addBox(-4.5F, -0.5F, -3.0F, 2, 1, 6)
				// strap_right
				.texOffs(0, 11).addBox(2.5F, -0.5F, -3.0F, 2, 1, 6)
				// square_left
				.texOffs(16, 11).addBox(-3.5F, 1.5F, -4.0F, 3, 3, 1)
				// square_right
				.texOffs(24, 11).addBox(0.5F, 1.5F, -4.0F, 3, 3, 1)
				// mag_0
				.texOffs(32, 11).addBox(-4.0F, 5.5F, -4.0F, 2, 3, 1)
				// mag_1
				.texOffs(38, 11).addBox(-2.0F, 5.5F, -4.0F, 2, 3, 1)
				// mag_2
				.texOffs(44, 11).addBox(0.0F, 5.5F, -4.0F, 2, 3, 1)
				// mag_3
				.texOffs(50, 11).addBox(2.0F, 5.5F, -4.0F, 2, 3, 1)
				// hook
				.texOffs(56, 11).addBox(4.5F, 7.0F, -3.5F, 1, 2, 1);
			// 堤防彈藥背心 (rig_heavy_2)
			case 2 -> CubeListBuilder.create()
				// front
				.texOffs(0, 0).addBox(-4.5F, 0.5F, -3.0F, 9, 10, 1)
				// back
				.texOffs(20, 0).addBox(-4.5F, 0.5F, 2.0F, 9, 10, 1)
				// strap_left
				.texOffs(40, 0).addBox(-4.5F, -0.5F, -3.0F, 2, 1, 6)
				// strap_right
				.texOffs(0, 11).addBox(2.5F, -0.5F, -3.0F, 2, 1, 6)
				// square_left
				.texOffs(24, 11).addBox(-3.5F, 1.5F, -4.0F, 3, 3, 1)
				// square_right
				.texOffs(32, 11).addBox(0.5F, 1.5F, -4.0F, 3, 3, 1)
				// mag_0
				.texOffs(40, 11).addBox(-4.0F, 5.5F, -4.0F, 2, 3, 1)
				// mag_1
				.texOffs(46, 11).addBox(-2.0F, 5.5F, -4.0F, 2, 3, 1)
				// mag_2
				.texOffs(52, 11).addBox(0.0F, 5.5F, -4.0F, 2, 3, 1)
				// mag_3
				.texOffs(58, 11).addBox(2.0F, 5.5F, -4.0F, 2, 3, 1)
				// side
				.texOffs(16, 11).addBox(4.5F, 4.0F, -2.0F, 1, 3, 3);
			// 錨衛突擊背心 (rig_heavy_3)
			case 3 -> CubeListBuilder.create()
				// front
				.texOffs(0, 0).addBox(-4.5F, 0.5F, -3.0F, 9, 10, 1)
				// back
				.texOffs(20, 0).addBox(-4.5F, 0.5F, 2.0F, 9, 10, 1)
				// strap_left
				.texOffs(40, 0).addBox(-4.5F, -0.5F, -3.0F, 2, 1, 6)
				// strap_right
				.texOffs(0, 11).addBox(2.5F, -0.5F, -3.0F, 2, 1, 6)
				// square_left
				.texOffs(40, 11).addBox(-3.5F, 1.0F, -4.0F, 3, 3, 1)
				// square_right
				.texOffs(48, 11).addBox(0.5F, 1.0F, -4.0F, 3, 3, 1)
				// mag_0
				.texOffs(16, 11).addBox(-4.0F, 4.5F, -4.0F, 2, 4, 1)
				// mag_1
				.texOffs(22, 11).addBox(-2.0F, 4.5F, -4.0F, 2, 4, 1)
				// mag_2
				.texOffs(28, 11).addBox(0.0F, 4.5F, -4.0F, 2, 4, 1)
				// mag_3
				.texOffs(34, 11).addBox(2.0F, 4.5F, -4.0F, 2, 4, 1)
				// anchor
				.texOffs(56, 11).addBox(-1.0F, 1.5F, -4.5F, 2, 2, 1);
			// 霧牆彈藥背心 (rig_heavy_4)
			case 4 -> CubeListBuilder.create()
				// front
				.texOffs(0, 0).addBox(-4.5F, 0.5F, -3.0F, 9, 10, 1)
				// back
				.texOffs(20, 0).addBox(-4.5F, 0.5F, 2.0F, 9, 10, 1)
				// strap_left
				.texOffs(40, 0).addBox(-4.5F, -0.5F, -3.0F, 2, 1, 6)
				// strap_right
				.texOffs(0, 11).addBox(2.5F, -0.5F, -3.0F, 2, 1, 6)
				// square_left
				.texOffs(40, 11).addBox(-4.0F, 1.0F, -4.0F, 2, 3, 1)
				// square_middle
				.texOffs(46, 11).addBox(-1.0F, 1.0F, -4.0F, 2, 3, 1)
				// square_right
				.texOffs(52, 11).addBox(2.0F, 1.0F, -4.0F, 2, 3, 1)
				// mag_0
				.texOffs(16, 11).addBox(-4.0F, 4.5F, -4.0F, 2, 4, 1)
				// mag_1
				.texOffs(22, 11).addBox(-2.0F, 4.5F, -4.0F, 2, 4, 1)
				// mag_2
				.texOffs(28, 11).addBox(0.0F, 4.5F, -4.0F, 2, 4, 1)
				// mag_3
				.texOffs(34, 11).addBox(2.0F, 4.5F, -4.0F, 2, 4, 1)
				// lamp
				.texOffs(58, 11).addBox(4.5F, 2.0F, -3.5F, 1, 2, 1);
			// 鐘樓重型背心 (rig_heavy_5)
			case 5 -> CubeListBuilder.create()
				// front
				.texOffs(0, 0).addBox(-4.5F, 0.5F, -3.0F, 9, 10, 1)
				// back
				.texOffs(20, 0).addBox(-4.5F, 0.5F, 2.0F, 9, 10, 1)
				// strap_left
				.texOffs(32, 11).addBox(-4.5F, -0.5F, -3.0F, 2, 1, 6)
				// strap_right
				.texOffs(48, 11).addBox(2.5F, -0.5F, -3.0F, 2, 1, 6)
				// collar
				.texOffs(0, 11).addBox(-4.5F, -1.0F, -3.5F, 9, 1, 7)
				// square_0
				.texOffs(24, 19).addBox(-4.0F, 1.0F, -4.0F, 2, 2, 1)
				// square_1
				.texOffs(30, 19).addBox(-2.0F, 1.0F, -4.0F, 2, 2, 1)
				// square_2
				.texOffs(36, 19).addBox(0.0F, 1.0F, -4.0F, 2, 2, 1)
				// square_3
				.texOffs(42, 19).addBox(2.0F, 1.0F, -4.0F, 2, 2, 1)
				// mag_0
				.texOffs(0, 19).addBox(-4.0F, 3.5F, -4.0F, 2, 4, 1)
				// mag_1
				.texOffs(6, 19).addBox(-2.0F, 3.5F, -4.0F, 2, 4, 1)
				// mag_2
				.texOffs(12, 19).addBox(0.0F, 3.5F, -4.0F, 2, 4, 1)
				// mag_3
				.texOffs(18, 19).addBox(2.0F, 3.5F, -4.0F, 2, 4, 1)
				// bell_left
				.texOffs(48, 19).addBox(-3.5F, 7.5F, -5.0F, 1, 1, 1)
				// bell_right
				.texOffs(52, 19).addBox(2.5F, 7.5F, -5.0F, 1, 1, 1);
			// 鏽棺骸掛 (rig_heavy_6)
			case 6 -> CubeListBuilder.create()
				// front
				.texOffs(0, 0).addBox(-4.5F, 0.5F, -3.0F, 9, 10, 1)
				// back
				.texOffs(20, 0).addBox(-4.5F, 0.5F, 2.0F, 9, 10, 1)
				// strap_left
				.texOffs(40, 0).addBox(-4.5F, -0.5F, -3.0F, 2, 1, 6)
				// strap_right
				.texOffs(0, 11).addBox(2.5F, -0.5F, -3.0F, 2, 1, 6)
				// plate
				.texOffs(44, 11).addBox(-3.5F, 0.5F, -4.0F, 7, 3, 1)
				// sternum
				.texOffs(16, 11).addBox(-0.5F, 3.5F, -4.0F, 1, 5, 1)
				// mag_0
				.texOffs(20, 11).addBox(-4.0F, 5.0F, -4.0F, 2, 4, 1)
				// mag_1
				.texOffs(26, 11).addBox(-2.0F, 5.0F, -4.0F, 2, 4, 1)
				// mag_2
				.texOffs(32, 11).addBox(0.0F, 5.0F, -4.0F, 2, 4, 1)
				// mag_3
				.texOffs(38, 11).addBox(2.0F, 5.0F, -4.0F, 2, 4, 1)
				// chain_left
				.texOffs(60, 11).addBox(-4.5F, 9.0F, -3.5F, 1, 3, 1)
				// chain_right
				.texOffs(0, 18).addBox(3.5F, 9.0F, -3.5F, 1, 3, 1);
			default -> CubeListBuilder.create();
		};
	}
}
