package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.component.OriginComponent;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.item.TrackItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Splinecart implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("splinecart");

	public static final TrackTiesBlock TRACK_TIES = Registry.register(Registries.BLOCK, id("track_ties"),
			new TrackTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)));
	public static final BlockEntityType<TrackTiesBlockEntity> TRACK_TIES_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("track_ties"),
			BlockEntityType.Builder.create(TrackTiesBlockEntity::new, TRACK_TIES).build());

	public static final TrackItem TRACK = Registry.register(Registries.ITEM, id("track"),
			new TrackItem(new Item.Settings().component(DataComponentTypes.LORE,
					lore(Text.translatable("item.splinecart.track.desc").formatted(Formatting.GRAY))
			)));

	public static final ComponentType<OriginComponent> ORIGIN_POS = Registry.register(Registries.DATA_COMPONENT_TYPE, id("origin"),
			ComponentType.<OriginComponent>builder().codec(OriginComponent.CODEC).build());

	public static final EntityType<TrackFollowerEntity> TRACK_FOLLOWER = Registry.register(Registries.ENTITY_TYPE, id("track_follower"),
			EntityType.Builder.<TrackFollowerEntity>create(TrackFollowerEntity::new, SpawnGroup.MISC).trackingTickInterval(2).dimensions(0.25f, 0.25f).build());

	public static final TagKey<EntityType<?>> CARTS = TagKey.of(RegistryKeys.ENTITY_TYPE, id("carts"));

	@Override
	public void onInitialize() {
		var tieItem = Registry.register(Registries.ITEM, id("track_ties"),
				new BlockItem(TRACK_TIES, new Item.Settings()
						.component(DataComponentTypes.LORE,
								lore(Text.translatable("item.splinecart.track_ties.desc").formatted(Formatting.GRAY))
						)));

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
			entries.add(tieItem.getDefaultStack());
			entries.add(TRACK.getDefaultStack());
		});
	}

	public static LoreComponent lore(Text lore) {
		return new LoreComponent(List.of(lore));
	}

	public static Identifier id(String path) {
		return Identifier.of("splinecart", path);
	}
}