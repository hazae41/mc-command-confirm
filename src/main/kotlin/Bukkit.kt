package hazae41.minecraft.cmdconfirm

import hazae41.minecraft.kotlin.bukkit.*
import hazae41.minecraft.kotlin.catch
import hazae41.minecraft.kotlin.ex
import hazae41.minecraft.kotlin.lowerCase
import hazae41.minecraft.kotlin.not
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.collections.set

class CmdConfirmBukkit: BukkitPlugin(){

    object Config: ConfigFile("config"){
        val commands get() = config.keys.map(::Command)
        fun command(cmd: String) = commands.firstOrNull{cmd.startsWith(it.command)}
    }

    class Command(key: String): ConfigSection(Config, key){
        val command by string("command")
        val items by stringList("items")
        val message by string("message")
        val delay by long("delay")
    }

    data class Confirmation(val task: BukkitTask, val cmd: String, var status: String)
    val players = mutableMapOf<UUID, Confirmation>()

    override fun onEnable() = catch<Exception>(::warning){
        Config.init(this, "config/bukkit")
        mkreload()
        mkconfirm()
        listen(callback = playerCommand)
    }

    fun mkreload() = command("cmdconfirm"){
            args -> Config.reload()
        msg("&bConfig reloaded!")
    }

    fun mkconfirm() = command("confirm"){ args ->
        catch<Exception>(::msg){
            if(this !is Player)
                throw ex("&cYou're not a player")

            val confirm = players[uniqueId]
                ?: throw ex("&cYou don't have any confirmation")

            if(confirm.status != "pending")
                throw ex("&cAn internal error occured")

            confirm.status = "executing"
            chat(confirm.cmd)
        }
    }

    var playerCommand = fun(e: PlayerCommandPreprocessEvent){
        if(e.isCancelled) return

        val input = e.message.lowerCase
        val cmd = Config.command(input) ?: return

        val uuid = e.player.uniqueId
        if(uuid in players){
            val confirm = players[uuid]!!
            confirm.task.cancel()
            if(confirm.status == "executing"){
                players.remove(uuid)
                return
            }
        }

        val item = e.player.inventory.itemInMainHand.type.name.lowerCase
        val items = cmd.items.map(String::lowerCase)
        if(items.isNotEmpty()) if("all" !in items) if(item !in items) return

        e.isCancelled = true

        cmd.message.not("")?.also(e.player::msg)

        val task = schedule(delay = cmd.delay, unit = SECONDS){
            players.remove(uuid)
        }

        players[uuid] = Confirmation(task, e.message, "pending")
    }
}