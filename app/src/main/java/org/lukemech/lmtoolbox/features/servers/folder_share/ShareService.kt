package org.lukemech.lmtoolbox.features.servers.folder_share

import android.content.Context
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.KeepAwakeService
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.linux.parts.Bore
import org.lukemech.lmtoolbox.linux.parts.Tunnel
import org.lukemech.lmtoolbox.linux.parts.ConnectionPhase

/**
 * Keeps the folder's server going while it's shared (see KeepAwakeService): its notification tells its address
 * and who's connected. Stops once the share has.
 */
class ShareService : KeepAwakeService() {
    /** Not the Minecraft server's (0x4d43). */
    override val notificationId = 0x4653
    override val channel = CHANNEL
    override val icon = R.drawable.ic_folder_share
    override val lockTag = "LMToolbox:FolderShare"
    override val isActive get() = FolderShare.isRunning

    override fun shownNow() = shown(FolderShare.state.value, null)

    override fun shown() = combine(FolderShare.state, FolderShare.tunnels.tunnel, FolderShare.tunnels.bore, ticks) { run, tunnel, bore, _ ->
        run?.takeIf { it.isActive }?.let { shown(it, address(it, tunnel?.state, bore?.state)) }
    }

    override fun stopWork() = FolderShare.stop()

    /** Who's connected changes as time goes too (counted within a while of their last request). */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(30.seconds)
        }
    }

    /** The address it's best opened at: a tunnel's (from anywhere), else the network's. */
    private fun address(run: FolderShare.Run, tunnel: Tunnel.State?, bore: Bore.State?): String? {
        tunnel?.url?.takeIf { tunnel.phase == ConnectionPhase.Connected }?.let { return it }
        bore?.address?.takeIf { bore.phase == ConnectionPhase.Connected }?.let { return "http://$it" }
        return Reach.lanAddresses(this).firstOrNull()?.let { "http://$it:${run.port}" }
    }

    private fun shown(run: FolderShare.Run?, address: String?): Shown {
        val connected = run?.connected() ?: 0
        return Shown(
            title = run?.name?.let { getString(R.string.f_Fs_notification_title, it) } ?: getString(R.string.fp_FolderShare),
            text = listOfNotNull(
                address ?: getString(R.string.f_Fs_phase_running),
                resources.getQuantityString(R.plurals.f_Fs_connected, connected, connected).takeIf { connected > 0 }
            ).joinToString(" · "),
            route = FolderShareFeature.route,
            stop = getString(R.string.f_Fs_stop)
        )
    }

    companion object {
        private const val CHANNEL = "folder_share"

        /** Started with the share. */
        fun start(context: Context) =
            KeepAwakeService.start(context, ShareService::class.java, CHANNEL, context.getString(R.string.fp_FolderShare))
    }
}
