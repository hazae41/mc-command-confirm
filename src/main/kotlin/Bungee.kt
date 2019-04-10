package hazae41.minecraft.cmdconfirm

import hazae41.minecraft.kotlin.bungee.*
import hazae41.minecraft.kotlin.catch
import hazae41.minecraft.kotlin.ex
import hazae41.minecraft.kotlin.lowerCase
import hazae41.minecraft.kotlin.not
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.ChatEvent
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

class CmdConfirmBungee: BungeePlugin(){

    object Config: ConfigFile("config"){
        val commands get() = config.keys.map(::Command)
        fun command(cmd: String) = commands.firstOrNull{cmd.startsWith(it.command)}
    }

    class Command(key: String): ConfigSection(Config, key){
        val command by string("command")
        val message by string("message")
        val delay by long("delay")
    }

    data class Confirmation(val task: BungeeTask, val cmd: String, var status: String)
    val players = mutableMapOf<UUID, Confirmation>()

    override fun onEnable() = catch<Exception>(::warning){
        Config.init(this, "config/bungee")
        mkreload()
        mkconfirm()
        listen(callback = playerCommand)
    }

    fun mkreload() = command("gcmdconfirm", "cmdconfirm.reload"){
            args -> Config.reload()
        msg("&bConfig reloaded!")
    }

    fun mkconfirm() = command("gconfirm"){ args ->
        catch<Exception>(::msg){
            if(this !is ProxiedPlayer)
                throw ex("&cYou're not a player")

            val confirm = players[uniqueId]
                ?: throw ex("&cYou don't have any confirmation")

            if(confirm.status != "pending")
                throw ex("&cAn internal error occured")

            confirm.status = "executing"
            chat(confirm.cmd)
        }
    }

    var playerCommand = fun(e: ChatEvent){
        if(e.isCancelled) return

        val player = e.sender as? ProxiedPlayer ?: return
        val input = e.message.lowerCase
        val cmd = Config.command(input) ?: return

        val uuid = player.uniqueId
        if(uuid in players){
            val confirm = players[uuid]!!
            confirm.task.cancel()
            if(confirm.status == "executing"){
                players.remove(uuid)
                return
            }
        }

        e.isCancelled = true

        cmd.message.not("")?.also(player::msg)

        val task = schedule(delay = cmd.delay, unit = SECONDS){
            players.remove(uuid)
        }

        players[uuid] = Confirmation(task, e.message, "pending")
    }
}