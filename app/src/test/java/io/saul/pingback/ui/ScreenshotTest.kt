package io.saul.pingback.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.saul.pingback.monitor.MonitorRepository
import io.saul.pingback.monitor.MonitorState
import io.saul.pingback.monitor.NetworkSnapshot
import io.saul.pingback.monitor.Phase
import io.saul.pingback.monitor.PingMethod
import io.saul.pingback.monitor.PingResult
import io.saul.pingback.monitor.Providers
import io.saul.pingback.monitor.Settings
import io.saul.pingback.monitor.Target
import io.saul.pingback.monitor.TargetKind
import io.saul.pingback.monitor.TargetStatus
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screenshots used in the README from fake monitor states. Skipped unless run through
 * `./gradlew recordScreenshots`, which sets `pingback.screenshotDir`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val dir: String? = System.getProperty("pingback.screenshotDir")

    @Before
    fun onlyWhenRecording() = assumeTrue("not recording screenshots", dir != null)

    private fun reply(ms: Double) = PingResult(true, ms, PingMethod.ICMP)
    private val noReply = PingResult(false, null, PingMethod.ICMP)

    private val wifi = NetworkSnapshot("Wi-Fi", "192.168.1.1", listOf("192.168.1.1"))
    private val gateway = Target("gateway:192.168.1.1", "Gateway", "192.168.1.1", TargetKind.GATEWAY)
    private val dns = Target("dns:192.168.1.1", "DNS server", "192.168.1.1", TargetKind.DNS)

    private fun render(state: MonitorState, name: String) {
        MonitorRepository.publish(state)
        compose.setContent { PingBackTheme { MainScreen(onPlayStop = {}) } }
        compose.onRoot().captureRoboImage("$dir/$name.png")
    }

    @Test
    fun stopped() = render(MonitorState(), "stopped")

    @Test
    fun offline() {
        val rounds = 14
        render(
            MonitorState(
                phase = Phase.OFFLINE,
                network = wifi,
                providers = Providers.ALL.map { TargetStatus(it, noReply, 0, rounds, 3) },
                gateway = TargetStatus(gateway, reply(3.0), rounds, rounds, rounds),
                dns = listOf(TargetStatus(dns, reply(4.0), rounds, rounds, rounds)),
                providersMeeting = 0,
                rounds = rounds,
            ),
            "offline",
        )
    }

    @Test
    fun recovering() {
        val latency = listOf(14.0, 18.0, 0.0, 22.0)
        val streaks = listOf(3, 2, 0, 3)
        render(
            MonitorState(
                phase = Phase.OFFLINE,
                network = wifi,
                providers = Providers.ALL.mapIndexed { i, t ->
                    TargetStatus(t, if (streaks[i] > 0) reply(latency[i]) else noReply, streaks[i], 30, 6 + streaks[i])
                },
                gateway = TargetStatus(gateway, reply(3.0), 30, 30, 30),
                dns = listOf(TargetStatus(dns, reply(4.0), 30, 30, 30)),
                providersMeeting = 0,
                rounds = 30,
            ),
            "recovering",
        )
    }

    @Test
    fun online() {
        val latency = listOf(12.0, 17.0, 21.0, 25.0)
        render(
            MonitorState(
                phase = Phase.ONLINE,
                network = wifi,
                providers = Providers.ALL.mapIndexed { i, t -> TargetStatus(t, reply(latency[i]), 42, 60, 51) },
                gateway = TargetStatus(gateway, reply(2.0), 60, 60, 60),
                dns = listOf(TargetStatus(dns, reply(3.0), 60, 60, 60)),
                providersMeeting = 4,
                rounds = 60,
                lastRestoredAt = 1_793_000_000_000,
            ),
            "online",
        )
    }

    @Test
    fun settings() {
        compose.setContent { PingBackTheme { SettingsDialog(Settings(), onDismiss = {}, onSave = {}) } }
        val file = File("$dir/settings.png")
        compose.onNode(isDialog()).captureRoboImage(file)
        cropToContent(file)
    }

    /** Trims the dimmed backdrop around a dialog, keeping a small margin. */
    private fun cropToContent(file: File, margin: Int = 40) {
        val image = BitmapFactory.decodeFile(file.path)
        val backdrop = image.getPixel(0, 0)
        var top = image.height
        var bottom = -1
        for (y in 0 until image.height) {
            if ((0 until image.width).any { image.getPixel(it, y) != backdrop }) {
                if (top > y) top = y
                bottom = y
            }
        }
        if (bottom < 0) return
        top = (top - margin).coerceAtLeast(0)
        bottom = (bottom + margin).coerceAtMost(image.height - 1)
        val cropped = Bitmap.createBitmap(image, 0, top, image.width, bottom - top + 1)
        file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
