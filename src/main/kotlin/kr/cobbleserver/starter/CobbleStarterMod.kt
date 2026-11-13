package kr.cobbleserver.starter

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import java.nio.file.Files
import java.util.UUID

/**
 * 서버 전용 모드.
 *
 *  1) 첫 접속 시 1~9세대 스타팅 27마리 중 완전 랜덤 6V 스타팅 지급
 *  2) /힐            : 내 파티 전체 회복 (OP 불필요)
 *  3) /pb <1~6>      : 파티의 포켓몬을 아이템으로 꺼냄. 아이템을 우클릭하면 다시 파티(꽉 차면 PC)로 들어감
 *  4) /starterroll <대상> [wipe] : OP 전용. 대상에게 랜덤 6V 스타팅을 다시 지급
 *
 * /pc 는 Cobblemon 이 이미 가지고 있는 명령어이므로 이 모드가 만들지 않습니다.
 * (같은 이름으로 또 등록하면 Cobblemon 의 OP 요구 조건이 그대로 남기 때문)
 * 권한 노드 cobblemon.command.pc 를 LuckPerms 로 일반 유저에게 허용해 주세요. README 참고.
 */
class CobbleStarterMod : ModInitializer {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val givenFile by lazy {
        FabricLoader.getInstance().configDir.resolve("cobblestarter-given.json")
    }
    private val given: MutableSet<String> by lazy { loadGiven() }

    override fun onInitialize() {
        // 첫 접속 스타팅
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            val id = player.uuid.toString()
            if (given.contains(id)) return@register
            // Cobblemon 의 플레이어 데이터가 모두 올라온 뒤에 실행되도록 한 틱 미룸
            server.execute {
                if (given.add(id)) {
                    saveGiven()
                    giveRandomStarter(server, player)
                }
            }
        }

        // 명령어 등록
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            // /힐  (누구나)
            dispatcher.register(
                Commands.literal("힐").executes { ctx ->
                    val player = ctx.source.playerOrException
                    runAsConsole(ctx.source.server, "healpokemon ${player.scoreboardName}")
                    player.sendSystemMessage(Component.literal("§a파티의 포켓몬을 모두 회복했습니다."))
                    1
                }
            )

            // /pb <칸>  (누구나)
            dispatcher.register(
                Commands.literal("pb").then(
                    Commands.argument("칸", IntegerArgumentType.integer(1, 6)).executes { ctx ->
                        val player = ctx.source.playerOrException
                        itemize(player, IntegerArgumentType.getInteger(ctx, "칸"))
                    }
                )
            )

            // /starterroll <대상> [wipe]  (OP 전용)
            val roll = Commands.literal("starterroll")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.argument("대상", EntityArgument.players())
                        .executes { ctx -> reroll(ctx, wipe = false) }
                        .then(
                            Commands.argument("wipe", BoolArgumentType.bool())
                                .executes { ctx -> reroll(ctx, BoolArgumentType.getBool(ctx, "wipe")) }
                        )
                )
            dispatcher.register(roll)
            dispatcher.register(
                Commands.literal("스타팅")
                    .requires { it.hasPermission(2) }
                    .redirect(dispatcher.root.getChild("starterroll"))
            )
        }

        // 아이템화된 포켓몬 우클릭 -> 되돌리기
        UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
            val stack = player.getItemInHand(hand)
            if (level.isClientSide || player !is ServerPlayer) {
                return@UseItemCallback InteractionResultHolder.pass(stack)
            }
            val root = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
                ?: return@UseItemCallback InteractionResultHolder.pass(stack)
            if (!root.contains(TAG_KEY)) return@UseItemCallback InteractionResultHolder.pass(stack)

            val pokemon = Pokemon().loadFromNBT(player.registryAccess(), root.getCompound(TAG_KEY))
            val party = Cobblemon.storage.getParty(player)
            if (!party.add(pokemon)) {
                Cobblemon.storage.getPC(player).add(pokemon)
                player.sendSystemMessage(Component.literal("§e파티가 가득 차서 PC로 보냈습니다."))
            } else {
                player.sendSystemMessage(Component.literal("§a포켓몬이 파티로 돌아왔습니다."))
            }
            stack.shrink(1)
            InteractionResultHolder.success(stack)
        })
    }

    // ---------------------------------------------------------------- 스타팅

    // 1~9세대 스타팅 포켓몬 27마리 (세대별 풀/불/물 순서)
    private val starterPool = listOf(
        "bulbasaur", "charmander", "squirtle",
        "chikorita", "cyndaquil", "totodile",
        "treecko", "torchic", "mudkip",
        "turtwig", "chimchar", "piplup",
        "snivy", "tepig", "oshawott",
        "chespin", "fennekin", "froakie",
        "rowlet", "litten", "popplio",
        "grookey", "scorbunny", "sobble",
        "sprigatito", "fuecoco", "quaxly"
    )

    private fun pickSpeciesName(): String = starterPool.random()

    private fun giveRandomStarter(server: MinecraftServer, player: ServerPlayer) {
        val species = pickSpeciesName()
        val props = "level=5 hp_iv=31 attack_iv=31 defence_iv=31 " +
            "special_attack_iv=31 special_defence_iv=31 speed_iv=31"
        runAsConsole(server, "givepokemonother ${player.scoreboardName} $species $props")
        player.sendSystemMessage(Component.literal("§b스타팅 포켓몬이 지급되었습니다! 파티를 확인하세요."))
    }

    private fun reroll(ctx: CommandContext<CommandSourceStack>, wipe: Boolean): Int {
        val targets = EntityArgument.getPlayers(ctx, "대상")
        val server = ctx.source.server
        for (target in targets) {
            if (wipe) {
                runAsConsole(server, "pokemonrestartother ${target.scoreboardName} false")
            }
            giveRandomStarter(server, target)
            given.add(target.uuid.toString())
        }
        saveGiven()
        ctx.source.sendSuccess(
            { Component.literal("${targets.size}명에게 랜덤 스타팅을 지급했습니다." + if (wipe) " (파티/PC 초기화 포함)" else "") },
            true
        )
        return targets.size
    }

    // ------------------------------------------------------------------- /pb

    private fun itemize(player: ServerPlayer, slot: Int): Int {
        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.get(slot - 1)
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("§c$slot 번 칸에 포켓몬이 없습니다."))
            return 0
        }
        if (!party.remove(pokemon)) {
            player.sendSystemMessage(Component.literal("§c포켓몬을 꺼내지 못했습니다."))
            return 0
        }

        val root = CompoundTag()
        root.put(TAG_KEY, pokemon.saveToNBT(player.registryAccess(), CompoundTag()))

        val base: Item = BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"))
            .orElse(Items.HEART_OF_THE_SEA)
        val stack = ItemStack(base)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root))
        stack.set(DataComponents.MAX_STACK_SIZE, 1)
        stack.set(
            DataComponents.CUSTOM_NAME,
            Component.literal("§f").append(pokemon.species.translatedName).append(Component.literal(" Lv.${pokemon.level}"))
        )
        stack.set(
            DataComponents.LORE,
            ItemLore(listOf(Component.literal("§7우클릭하면 파티로 돌아갑니다.")))
        )

        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
        player.sendSystemMessage(Component.literal("§a포켓몬을 아이템으로 꺼냈습니다."))
        return 1
    }

    // ------------------------------------------------------------------ 유틸

    private fun runAsConsole(server: MinecraftServer, command: String) {
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
    }

    private fun loadGiven(): MutableSet<String> {
        return try {
            if (Files.exists(givenFile)) {
                val type = object : TypeToken<MutableSet<String>>() {}.type
                gson.fromJson<MutableSet<String>>(Files.readString(givenFile), type) ?: mutableSetOf()
            } else mutableSetOf()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun saveGiven() {
        try {
            Files.writeString(givenFile, gson.toJson(given))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TAG_KEY = "cobblestarter_pokemon"
    }
}
