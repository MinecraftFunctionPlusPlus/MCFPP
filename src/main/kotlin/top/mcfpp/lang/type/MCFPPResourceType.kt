package top.mcfpp.lang.type

import top.mcfpp.lang.resource.*

class MCFPPResourceType {

object ResourceID: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){
    override val typeName: String
        get() = "ResourceID"
}
    object Block: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Block"
    }
    object BlockEntity: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BlockEntity"
    }
    object Liquid: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Liquid"
    }
    object Item: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Item"
    }
    object EntityType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "EntityType"
    }
    object Memory: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Memory"
    }
    object PaintingVariant: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "PaintingVariant"
    }
    object VillagerProfession: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "VillagerProfession"
    }
    object VillagerType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "VillagerType"
    }
    object Effect: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Effect"
    }
    object PotionEffect: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "PotionEffect"
    }
    object Enchant: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Enchant"
    }
    object Particle: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Particle"
    }
    object Dimension: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Dimension"
    }
    object Biome: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Biome"
    }
    object Statistic: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Statistic"
    }
    object DamageType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "DamageType"
    }
    object RecipeType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "RecipeType"
    }
    object RecipeSerializer: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "RecipeSerializer"
    }
    object SoundEvent: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "SoundEvent"
    }
    object Advancement: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Advancement"
    }
    object LootTable: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTable"
    }
    object LootTablePredicate: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTablePredicate"
    }
    object Function: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Function"
    }
    object Structure: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Structure"
    }
    object Recipe: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Recipe"
    }
    object BlockTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BlockTag"
    }
    object LiquidTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LiquidTag"
    }
    object ItemTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "ItemTag"
    }
    object EntityTypeTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "EntityTypeTag"
    }
    object FunctionTag: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "FunctionTag"
    }
    object BlockStateFile: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BlockStateFile"
    }
    object Model: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Model"
    }
    object Texture: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Texture"
    }
    object Sound: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Sound"
    }
    object BossBar: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "BossBar"
    }
    object Storage: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "Storage"
    }
    object LootTableFunction: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTableFunction"
    }
    object LootTableCondition: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTableCondition"
    }
    object LootTableType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "LootTableType"
    }
    object CommandArgumentType: MCFPPType(parentType = listOf(ResourceID)){
        override val typeName: String
            get() = "CommandArgumentType"
    }
}
