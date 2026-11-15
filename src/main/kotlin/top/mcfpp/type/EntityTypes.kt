package top.mcfpp.type

/** Entity reference types share the target representation, but have distinct language identities. */
object EntityTypes {
    val DisplayEntity = MCFPPEntityType(entityName = "DisplayEntity", entityKinds = setOf(
        "minecraft:item_display", "minecraft:block_display", "minecraft:text_display"), parent = MCFPPEntityType.NormalSelector)

    private val ids = """
        allay area_effect_cloud armadillo armor_stand arrow axolotl bat bee blaze block_display boat
        bogged breeze breeze_wind_charge camel cat cave_spider chest_boat chest_minecart chicken cod cow
        creaking creeper dolphin donkey dragon_fireball drowned egg elder_guardian end_crystal ender_dragon
        ender_pearl enderman endermite evoker evoker_fangs experience_bottle experience_orb eye_of_ender
        falling_block fireball firework_rocket fishing_bobber fox frog furnace_minecart ghast giant
        glow_item_frame glow_squid goat guardian happy_ghast hoglin hopper_minecart horse husk illusioner
        interaction iron_golem item item_display item_frame leash_knot lightning_bolt llama llama_spit
        magma_cube marker minecart mooshroom mule ocelot ominous_item_spawner painting panda parrot
        phantom pig piglin piglin_brute pillager player polar_bear potion pufferfish rabbit ravager
        salmon sheep shulker shulker_bullet silverfish skeleton skeleton_horse slime small_fireball
        sniffer snow_golem snowball spawner_minecart spectral_arrow spider squid stray strider
        tadpole text_display tnt tnt_minecart trader_llama trident tropical_fish turtle vex villager
        vindicator wandering_trader warden wind_charge witch wither wither_skeleton wither_skull wolf
        zoglin zombie zombie_horse zombie_villager splash_potion lingering_potion
        acacia_boat acacia_chest_boat bamboo_raft bamboo_chest_raft birch_boat birch_chest_boat
        cherry_boat cherry_chest_boat dark_oak_boat dark_oak_chest_boat jungle_boat jungle_chest_boat
        mangrove_boat mangrove_chest_boat oak_boat oak_chest_boat pale_oak_boat pale_oak_chest_boat
        spruce_boat spruce_chest_boat
    """.trimIndent().split(Regex("\\s+"))

    val all: List<MCFPPEntityType> = listOf(DisplayEntity) + ids.map { id ->
        val name = id.split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        MCFPPEntityType(entityName = name, entityKinds = setOf("minecraft:$id"),
            parent = if (id.endsWith("_display")) DisplayEntity else MCFPPEntityType.NormalSelector)
    }
    private val names = all.associateBy { it.typeName }
    fun named(name: String): MCFPPEntityType? = names[name.removePrefix("mcfpp.minecraft.entity:")]
    fun byId(id: TypeId): MCFPPEntityType? = (id as? TypeId.Builtin)?.key?.removePrefix("entity.")?.let(names::get)
}
