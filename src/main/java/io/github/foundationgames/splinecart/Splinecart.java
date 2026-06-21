package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.component.OriginComponent;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.util.SUtil;
import io.github.foundationgames.splinecart.util.TrackProgress;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Splinecart implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("splinecart");

	public static final TrackTiesBlock TRACK_TIES = SUtil.register(BuiltInRegistries.BLOCK, id("track_ties"),
			(i, k) -> new TrackTiesBlock(BlockBehaviour.Properties.ofLegacyCopy(Blocks.RAIL).setId(k)));
	public static final BlockEntityType<TrackTiesBlockEntity> TRACK_TIES_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("track_ties"),
			FabricBlockEntityTypeBuilder.create(TrackTiesBlockEntity::new, TRACK_TIES).build());

	public static final TrackItem TRACK = SUtil.register(BuiltInRegistries.ITEM, id("track"),
			(i, k) -> new TrackItem(TrackType.DEFAULT, new Item.Properties().component(DataComponents.LORE,
					lore(Component.translatable("item.splinecart.track.desc").withStyle(ChatFormatting.GRAY))
			).setId(k)));
	public static final TrackItem CHAIN_DRIVE_TRACK = SUtil.register(BuiltInRegistries.ITEM, id("chain_drive_track"),
			(i, k) -> new TrackItem(TrackType.CHAIN_DRIVE, new Item.Properties().component(DataComponents.LORE,
					lore(Component.translatable("item.splinecart.chain_drive_track.desc").withStyle(ChatFormatting.GRAY))
			).setId(k)));
	public static final TrackItem MAGNETIC_TRACK = SUtil.register(BuiltInRegistries.ITEM, id("magnetic_track"),
			(i, k) -> new TrackItem(TrackType.MAGNETIC, new Item.Properties().component(DataComponents.LORE,
					lore(Component.translatable("item.splinecart.magnetic_track.desc").withStyle(ChatFormatting.GRAY))
			).setId(k)));

	public static final DataComponentType<OriginComponent> ORIGIN_POS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("origin"),
			DataComponentType.<OriginComponent>builder().persistent(OriginComponent.CODEC).build());

	public static final EntityType<TrackFollowerEntity> TRACK_FOLLOWER = SUtil.register(BuiltInRegistries.ENTITY_TYPE, id("track_follower"),
			(i, k) -> EntityType.Builder.<TrackFollowerEntity>of(TrackFollowerEntity::new, MobCategory.MISC).updateInterval(2).sized(0.25f, 0.25f).build(k));

	public static final TagKey<EntityType<?>> CARTS = TagKey.create(Registries.ENTITY_TYPE, id("carts"));

	public static final CreativeModeTab SPLINECART_GROUP = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.splinecart"))
			.displayItems((params, output) -> {
				output.accept(TRACK_TIES.asItem().getDefaultInstance());
				output.accept(TRACK.getDefaultInstance());
				output.accept(CHAIN_DRIVE_TRACK.getDefaultInstance());
				output.accept(MAGNETIC_TRACK.getDefaultInstance());
			})
			.icon(CHAIN_DRIVE_TRACK::getDefaultInstance)
			.build();

	@Override
	public void onInitialize() {
		var tieItem = SUtil.register(BuiltInRegistries.ITEM, id("track_ties"),
				(i, k) -> new BlockItem(TRACK_TIES, new Item.Properties()
						.component(DataComponents.LORE,
								lore(Component.translatable("item.splinecart.track_ties.desc").withStyle(ChatFormatting.GRAY))
						).useBlockDescriptionPrefix().setId(k)));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.accept(tieItem.getDefaultInstance());
			output.accept(TRACK.getDefaultInstance());
			output.accept(CHAIN_DRIVE_TRACK.getDefaultInstance());
			output.accept(MAGNETIC_TRACK.getDefaultInstance());
		});

		FabricEntityDataRegistry.register(id("track_progress"), TrackProgress.DATA_HANDLER);

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("splinecart"), SPLINECART_GROUP);
	}

	public static ItemLore lore(Component lore) {
		return new ItemLore(List.of(lore));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("splinecart", path);
	}
}