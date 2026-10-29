package org.lukemech.lmtoolbox.features.servers.mc_server

import android.content.Context
import kotlinx.coroutines.flow.combine
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.KeepAwakeService
import org.lukemech.lmtoolbox.linux.parts.ConnectionPhase

/**
 * Keeps the Minecraft server going while it runs (see KeepAwakeService): its notification tells how it's doing,
 * who's online and whether a tunnel is up. Stops once the server has.
 */
class ServerService : KeepAwakeService() {
    override val notificationId = 0x4d43
    override val channel = CHANNEL
    override val icon = R.drawable.mc_block
    override val lockTag = "LMToolbox:MinecraftServer"
    override val isActive get() = ServerRunner.state.value?.isActive == true

    override fun shownNow() = shown(ServerRunner.state.value, tunnel = false)

    override fun shown() = combine(ServerRunner.state, ServerRunner.tunnel, ServerRunner.bore) { run, tunnel, bore ->
        // A tunnel connected: Cloudflare's or bore's
        run?.takeIf { it.isActive }?.let {
            shown(it, tunnel?.state?.phase == ConnectionPhase.Connected || bore?.state?.phase == ConnectionPhase.Connected)
        }
    }

    override fun stopWork() = ServerRunner.stop()

    private fun shown(run: ServerRunner.Run?, tunnel: Boolean): Shown {
        val status = getString(
            when (run?.phase) {
                ServerRunner.Phase.Running -> R.string.f_Mc_phase_running
                ServerRunner.Phase.Stopping -> R.string.f_Mc_phase_stopping
                else -> R.string.f_Mc_phase_starting
            }
        )
        return Shown(
            title = run?.name ?: getString(R.string.fp_McServer),
            text = listOfNotNull(
                status,
                run?.online?.let { resources.getQuantityString(R.plurals.f_Mc_players_online, it, it) },
                getString(R.string.f_Mc_tunnel_short).takeIf { tunnel }
            ).joinToString(" · "),
            route = MinecraftServerFeature.route,
            // Stopping already
            stop = getString(R.string.f_Mc_stop).takeIf { run?.phase != ServerRunner.Phase.Stopping }
        )
    }

    companion object {
        private const val CHANNEL = "minecraft_server"

        /** Started with the server. */
        fun start(context: Context) =
            start(context, ServerService::class.java, CHANNEL, context.getString(R.string.f_Mc_channel))
    }
}
