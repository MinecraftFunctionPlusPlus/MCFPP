package top.mcfpp.type


import top.mcfpp.model.FieldContainer
import top.mcfpp.`var`.lang.Var
import top.mcfpp.`var`.lang.resource.*
import top.mcfpp.model.Class

class MCFPPResourceType {

object ResourceID: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){
    override val typeName: String
        get() = "ResourceID"
}
    object Block: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Block"

        override fun build(identifier: String, container: FieldContainer): Var<*> = BlockConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = BlockConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = BlockConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Block(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Block(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Block(clazz, identifier)
    }
    object BlockEntity: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BlockEntity"

        override fun build(identifier: String, container: FieldContainer): Var<*> = BlockEntityConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = BlockEntityConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = BlockEntityConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = BlockEntity(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = BlockEntity(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = BlockEntity(clazz, identifier)
    }
    object Liquid: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Liquid"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LiquidConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LiquidConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LiquidConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Liquid(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Liquid(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Liquid(clazz, identifier)
    }
    object Item: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Item"

        override fun build(identifier: String, container: FieldContainer): Var<*> = ItemConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = ItemConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = ItemConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Item(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Item(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Item(clazz, identifier)
    }
    object EntityType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "EntityType"

        override fun build(identifier: String, container: FieldContainer): Var<*> = EntityTypeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = EntityTypeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = EntityTypeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = EntityType(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = EntityType(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = EntityType(clazz, identifier)
    }
    object Memory: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Memory"

        override fun build(identifier: String, container: FieldContainer): Var<*> = MemoryConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = MemoryConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = MemoryConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Memory(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Memory(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Memory(clazz, identifier)
    }
    object PaintingVariant: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "PaintingVariant"

        override fun build(identifier: String, container: FieldContainer): Var<*> = PaintingVariantConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = PaintingVariantConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = PaintingVariantConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = PaintingVariant(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = PaintingVariant(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = PaintingVariant(clazz, identifier)
    }
    object VillagerProfession: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "VillagerProfession"

        override fun build(identifier: String, container: FieldContainer): Var<*> = VillagerProfessionConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = VillagerProfessionConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = VillagerProfessionConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = VillagerProfession(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = VillagerProfession(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = VillagerProfession(clazz, identifier)
    }
    object VillagerType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "VillagerType"

        override fun build(identifier: String, container: FieldContainer): Var<*> = VillagerTypeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = VillagerTypeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = VillagerTypeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = VillagerType(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = VillagerType(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = VillagerType(clazz, identifier)
    }
    object Effect: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Effect"

        override fun build(identifier: String, container: FieldContainer): Var<*> = EffectConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = EffectConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = EffectConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Effect(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Effect(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Effect(clazz, identifier)
    }
    object PotionEffect: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "PotionEffect"

        override fun build(identifier: String, container: FieldContainer): Var<*> = PotionEffectConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = PotionEffectConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = PotionEffectConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = PotionEffect(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = PotionEffect(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = PotionEffect(clazz, identifier)
    }
    object Enchant: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Enchant"

        override fun build(identifier: String, container: FieldContainer): Var<*> = EnchantConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = EnchantConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = EnchantConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Enchant(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Enchant(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Enchant(clazz, identifier)
    }
    object Particle: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Particle"

        override fun build(identifier: String, container: FieldContainer): Var<*> = ParticleConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = ParticleConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = ParticleConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Particle(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Particle(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Particle(clazz, identifier)
    }
    object Dimension: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Dimension"

        override fun build(identifier: String, container: FieldContainer): Var<*> = DimensionConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = DimensionConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = DimensionConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Dimension(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Dimension(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Dimension(clazz, identifier)
    }
    object Biome: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Biome"

        override fun build(identifier: String, container: FieldContainer): Var<*> = BiomeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = BiomeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = BiomeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Biome(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Biome(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Biome(clazz, identifier)
    }
    object Statistic: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Statistic"

        override fun build(identifier: String, container: FieldContainer): Var<*> = StatisticConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = StatisticConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = StatisticConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Statistic(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Statistic(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Statistic(clazz, identifier)
    }
    object DamageType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "DamageType"

        override fun build(identifier: String, container: FieldContainer): Var<*> = DamageTypeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = DamageTypeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = DamageTypeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = DamageType(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = DamageType(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = DamageType(clazz, identifier)
    }
    object RecipeType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "RecipeType"

        override fun build(identifier: String, container: FieldContainer): Var<*> = RecipeTypeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = RecipeTypeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = RecipeTypeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = RecipeType(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = RecipeType(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = RecipeType(clazz, identifier)
    }
    object RecipeSerializer: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "RecipeSerializer"

        override fun build(identifier: String, container: FieldContainer): Var<*> = RecipeSerializerConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = RecipeSerializerConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = RecipeSerializerConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = RecipeSerializer(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = RecipeSerializer(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = RecipeSerializer(clazz, identifier)
    }
    object SoundEvent: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "SoundEvent"

        override fun build(identifier: String, container: FieldContainer): Var<*> = SoundEventConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = SoundEventConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = SoundEventConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = SoundEvent(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = SoundEvent(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = SoundEvent(clazz, identifier)
    }
    object Advancement: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Advancement"

        override fun build(identifier: String, container: FieldContainer): Var<*> = AdvancementConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = AdvancementConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = AdvancementConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Advancement(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Advancement(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Advancement(clazz, identifier)
    }
    object LootTable: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTable"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LootTableConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LootTableConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LootTableConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = LootTable(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = LootTable(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = LootTable(clazz, identifier)
    }
    object LootTablePredicate: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTablePredicate"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LootTablePredicateConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LootTablePredicateConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LootTablePredicateConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = LootTablePredicate(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = LootTablePredicate(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = LootTablePredicate(clazz, identifier)
    }
    object FunctionID: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "FunctionID"

        override fun build(identifier: String, container: FieldContainer): Var<*> = FunctionIDConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = FunctionIDConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = FunctionIDConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = FunctionID(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = FunctionID(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = FunctionID(clazz, identifier)
    }
    object Structure: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Structure"

        override fun build(identifier: String, container: FieldContainer): Var<*> = StructureConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = StructureConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = StructureConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Structure(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Structure(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Structure(clazz, identifier)
    }
    object Recipe: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Recipe"

        override fun build(identifier: String, container: FieldContainer): Var<*> = RecipeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = RecipeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = RecipeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Recipe(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Recipe(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Recipe(clazz, identifier)
    }
    object BlockTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BlockTag"

        override fun build(identifier: String, container: FieldContainer): Var<*> = BlockTagConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = BlockTagConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = BlockTagConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = BlockTag(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = BlockTag(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = BlockTag(clazz, identifier)
    }
    object LiquidTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LiquidTag"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LiquidTagConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LiquidTagConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LiquidTagConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = LiquidTag(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = LiquidTag(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = LiquidTag(clazz, identifier)
    }
    object ItemTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "ItemTag"

        override fun build(identifier: String, container: FieldContainer): Var<*> = ItemTagConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = ItemTagConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = ItemTagConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = ItemTag(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = ItemTag(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = ItemTag(clazz, identifier)
    }
    object EntityTypeTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "EntityTypeTag"

        override fun build(identifier: String, container: FieldContainer): Var<*> = EntityTypeTagConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = EntityTypeTagConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = EntityTypeTagConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = EntityTypeTag(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = EntityTypeTag(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = EntityTypeTag(clazz, identifier)
    }
    object FunctionTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "FunctionTag"

        override fun build(identifier: String, container: FieldContainer): Var<*> = FunctionTagConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = FunctionTagConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = FunctionTagConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = FunctionTag(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = FunctionTag(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = FunctionTag(clazz, identifier)
    }
    object BlockStateFile: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BlockStateFile"

        override fun build(identifier: String, container: FieldContainer): Var<*> = BlockStateFileConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = BlockStateFileConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = BlockStateFileConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = BlockStateFile(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = BlockStateFile(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = BlockStateFile(clazz, identifier)
    }
    object Model: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Model"

        override fun build(identifier: String, container: FieldContainer): Var<*> = ModelConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = ModelConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = ModelConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Model(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Model(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Model(clazz, identifier)
    }
    object Texture: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Texture"

        override fun build(identifier: String, container: FieldContainer): Var<*> = TextureConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = TextureConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = TextureConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Texture(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Texture(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Texture(clazz, identifier)
    }
    object Sound: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Sound"

        override fun build(identifier: String, container: FieldContainer): Var<*> = SoundConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = SoundConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = SoundConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Sound(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Sound(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Sound(clazz, identifier)
    }
    object BossBar: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BossBar"

        override fun build(identifier: String, container: FieldContainer): Var<*> = BossBarConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = BossBarConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = BossBarConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = BossBar(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = BossBar(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = BossBar(clazz, identifier)
    }
    object Storage: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Storage"

        override fun build(identifier: String, container: FieldContainer): Var<*> = StorageConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = StorageConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = StorageConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = Storage(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = Storage(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = Storage(clazz, identifier)
    }
    object LootTableFunction: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTableFunction"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LootTableFunctionConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LootTableFunctionConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LootTableFunctionConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = LootTableFunction(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = LootTableFunction(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = LootTableFunction(clazz, identifier)
    }
    object LootTableCondition: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTableCondition"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LootTableConditionConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LootTableConditionConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LootTableConditionConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = LootTableCondition(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = LootTableCondition(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = LootTableCondition(clazz, identifier)
    }
    object LootTableType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTableType"

        override fun build(identifier: String, container: FieldContainer): Var<*> = LootTableTypeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = LootTableTypeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = LootTableTypeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = LootTableType(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = LootTableType(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = LootTableType(clazz, identifier)
    }
    object CommandArgumentType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "CommandArgumentType"

        override fun build(identifier: String, container: FieldContainer): Var<*> = CommandArgumentTypeConcrete(container, "", identifier)
        override fun build(identifier: String): Var<*> = CommandArgumentTypeConcrete("", identifier)
        override fun build(identifier: String, clazz: Class): Var<*> = CommandArgumentTypeConcrete(clazz, "", identifier)
        override fun buildUnConcrete(identifier: String, container: FieldContainer): Var<*> = CommandArgumentType(container, identifier)
        override fun buildUnConcrete(identifier: String): Var<*> = CommandArgumentType(identifier)
        override fun buildUnConcrete(identifier: String, clazz: Class): Var<*> = CommandArgumentType(clazz, identifier)
    }
}
