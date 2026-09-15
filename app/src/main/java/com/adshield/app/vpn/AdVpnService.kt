package com.adshield.app.vpn

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.adshield.app.AdShieldApp
import com.adshield.app.MainActivity
import com.adshield.app.R
import com.adshield.app.core.AppGraph
import com.adshield.app.core.BlockedToastNotifier
import com.adshield.app.core.EngineState
import com.adshield.app.filter.FilterEngine
import com.adshield.app.overlay.OverlayPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Local, on-device DNS firewall.
 *
 * The tunnel routes only its own DNS address and the addresses of well known public resolvers.
 * Every query that lands there is checked against the blocklists; allowed queries are relayed to
 * the configured upstream and answered back through the tunnel. Plain traffic keeps using the
 * device network, which keeps battery use low while still making hardcoded resolvers useless.
 */
class AdVpnService : VpnService() {

    private var scope: CoroutineScope = newScope()
    private var workers: ExecutorService = newWorkers()

    @Volatile private var scopeAlive = true
    @Volatile private var workersAlive = true
    @Volatile private var running = false
    @Volatile private var pausedUntil = 0L
    @Volatile private var observing = false
    @Volatile private var foregroundStarted = false
    @Volatile private var toastEnabled = true
    @Volatile private var overlayEnabled = false

    private val toastNotifier by lazy { BlockedToastNotifier(this) }
    private val overlayPanel by lazy { OverlayPanel(this) }

    /** Windows and the Compose owners behind them are main thread objects. */
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var tunnel: ParcelFileDescriptor? = null
    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null
    private var loopThread: Thread? = null
    private var upstream: DnsUpstream? = null

    private val writeLock = Any()
    private val packetBuffer = ByteArray(MAX_PACKET)

    override fun onCreate() {
        super.onCreate()
        pausedUntil = runCatching { AppGraph.settings.pausedUntil }.getOrDefault(0L)
        toastEnabled = runCatching { AppGraph.settings.blockedToast.value }.getOrDefault(true)
        overlayEnabled = runCatching { AppGraph.settings.floatingPanel.value }.getOrDefault(false)
        runCatching { AdShieldApp.createChannels(this) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                startInForeground()
                stopEverything()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                if (!running) {
                    startInForeground()
                    stopEverything()
                    stopSelf()
                    return START_NOT_STICKY
                }
                pause(intent.getIntExtra(EXTRA_MINUTES, 15))
                return START_STICKY
            }

            ACTION_RESUME -> {
                if (running) {
                    resume()
                    return START_STICKY
                }
                startInForeground()
                stopEverything()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REBUILD -> {
                if (running) {
                    rebuildInterface()
                    return START_STICKY
                }
                startInForeground()
                stopEverything()
                stopSelf()
                return START_NOT_STICKY
            }

            // Sent when the floating panel is switched on, or after Android's overlay consent
            // screen closes. It must not start the tunnel by accident.
            ACTION_REFRESH_OVERLAY -> {
                if (running) {
                    applyOverlay()
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startProtection()
                return START_STICKY
            }
        }
    }

    override fun onRevoke() {
        EngineState.vpnError.value = runCatching { getString(R.string.vpn_revoked) }.getOrNull()
        stopEverything()
        stopSelf()
        runCatching { super.onRevoke() }
    }

    override fun onDestroy() {
        stopEverything()
        runCatching { overlayPanel.hide() }
        super.onDestroy()
    }

    // ------------------------------------------------------------- lifecycle

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun newWorkers(): ExecutorService = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "adshield-dns").apply { isDaemon = true }
    }

    private fun startProtection() {
        if (running) {
            refreshNotification()
            return
        }
        if (!scopeAlive) {
            scope = newScope()
            scopeAlive = true
        }
        if (!workersAlive) {
            workers = newWorkers()
            workersAlive = true
        }
        observing = false
        startInForeground()

        val storedPause = runCatching { AppGraph.settings.pausedUntil }.getOrDefault(0L)
        pausedUntil = if (storedPause > System.currentTimeMillis()) storedPause else 0L

        if (!establish()) {
            EngineState.vpnError.value = runCatching { getString(R.string.vpn_error_establish) }.getOrNull()
            EngineState.isRunning.value = false
            stopEverything()
            stopSelf()
            return
        }

        running = true
        EngineState.isRunning.value = true
        EngineState.pausedUntil.value = pausedUntil
        startLoop()
        observe()
        refreshNotification()
        applyOverlay()
    }

    private fun stopEverything() {
        running = false
        EngineState.isRunning.value = false
        observing = false
        closeStreams()
        closeTunnel()
        runCatching { workers.shutdownNow() }
        workersAlive = false
        runCatching { scope.cancel() }
        scopeAlive = false
        runCatching { AppGraph.stats.flushBlocking() }
        applyOverlay()
        if (foregroundStarted) {
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            foregroundStarted = false
        }
    }

    private fun pause(minutes: Int) {
        val until = System.currentTimeMillis() + minutes.coerceIn(1, 24 * 60) * 60_000L
        pausedUntil = until
        runCatching { AppGraph.settings.pausedUntil = until }
        EngineState.pausedUntil.value = until
        refreshNotification()
    }

    private fun resume() {
        pausedUntil = 0L
        runCatching { AppGraph.settings.pausedUntil = 0L }
        EngineState.pausedUntil.value = 0L
        refreshNotification()
    }

    private fun observe() {
        if (observing) return
        observing = true

        scope.launch {
            EngineState.todayBlocked.collectLatest {
                delay(NOTIFICATION_THROTTLE_MS)
                refreshNotification()
            }
        }

        // The Settings screen can switch the blocked-ad message on and off while running.
        scope.launch {
            runCatching {
                AppGraph.settings.blockedToast.collect { enabled -> toastEnabled = enabled }
            }
        }

        scope.launch {
            runCatching {
                AppGraph.settings.floatingPanel.collect { enabled ->
                    overlayEnabled = enabled
                    applyOverlay()
                }
            }
        }

        // Per-app exclusions changed: rebuild the interface so the new list takes effect.
        scope.launch {
            var first = true
            runCatching {
                AppGraph.settings.excludedPackages.collect {
                    if (first) {
                        first = false
                        return@collect
                    }
                    delay(REBUILD_DEBOUNCE_MS)
                    rebuildInterface()
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(HOUSEKEEPING_INTERVAL_MS)
                runCatching { AppGraph.stats.flush() }
                if (pausedUntil > 0L && System.currentTimeMillis() >= pausedUntil) resume()
            }
        }

        // Make sure lists and rules are loaded even when the service starts before the UI.
        scope.launch {
            runCatching {
                AppGraph.lists.init()
                AppGraph.refreshFilters()
            }
        }
    }

    // ------------------------------------------------------------- tunnel

    private fun establish(): Boolean {
        val settings = runCatching { AppGraph.settings }.getOrNull()
        // VpnService.Builder is an inner class, so it is constructed against this service
        // instance instead of being qualified with the VpnService name.
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUN_ADDRESS4, 32)
            .addDnsServer(ResolverIps.FAKE_DNS4_TEXT)
            .addRoute(ResolverIps.FAKE_DNS4_TEXT, 32)
            .setMtu(1500)
            .setBlocking(false)

        runCatching {
            builder.addAddress(ResolverIps.FAKE_DNS6_TEXT, 128)
            builder.addDnsServer(ResolverIps.FAKE_DNS6_TEXT)
            builder.addRoute(ResolverIps.FAKE_DNS6_TEXT, 128)
        }

        if (settings == null || settings.hijackResolvers) {
            ResolverIps.IPV4_TEXTS_PUBLIC.forEach { runCatching { builder.addRoute(it, 32) } }
            ResolverIps.IPV6_TEXTS_PUBLIC.forEach { runCatching { builder.addRoute(it, 128) } }
        }

        // Our own traffic must stay outside the tunnel: upstream lookups, list downloads and
        // encrypted DNS would otherwise loop back into the filter.
        runCatching { builder.addDisallowedApplication(packageName) }
        settings?.excludedPackages?.value?.forEach { pkg ->
            runCatching { builder.addDisallowedApplication(pkg) }
        }

        val vpnInterface = try {
            builder.establish()
        } catch (throwable: Throwable) {
            null
        } ?: return false

        return try {
            tunnel = vpnInterface
            input = FileInputStream(vpnInterface.fileDescriptor)
            output = FileOutputStream(vpnInterface.fileDescriptor)
            upstream = DnsUpstream { socket -> runCatching { protect(socket) }.isSuccess }
            true
        } catch (throwable: Throwable) {
            closeTunnel()
            false
        }
    }

    @Synchronized
    private fun rebuildInterface() {
        if (!running) return
        closeStreams()
        closeTunnel()
        if (!establish()) {
            EngineState.vpnError.value = runCatching { getString(R.string.vpn_error_establish) }.getOrNull()
            stopEverything()
            stopSelf()
            return
        }
        startLoop()
        refreshNotification()
    }

    private fun startLoop() {
        val stream = input ?: return
        val thread = Thread({
            val buffer = packetBuffer
            while (running) {
                val length = try {
                    stream.read(buffer)
                } catch (throwable: Throwable) {
                    break
                }
                if (length <= 0) continue
                try {
                    handlePacket(buffer, length)
                } catch (throwable: Throwable) {
                    // A malformed packet must never take the tunnel down.
                }
            }
        }, "adshield-tun")
        thread.isDaemon = true
        loopThread = thread
        thread.start()
    }

    private fun closeStreams() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        val thread = loopThread
        input = null
        output = null
        loopThread = null
        thread?.let { runCatching { it.join(600) } }
    }

    private fun closeTunnel() {
        val descriptor = tunnel
        tunnel = null
        runCatching { descriptor?.close() }
    }

    // ------------------------------------------------------------- packet handling

    private fun handlePacket(buffer: ByteArray, length: Int) {
        when ((buffer[0].toInt() shr 4) and 0x0F) {
            4 -> handleIpv4(buffer, length)
            6 -> handleIpv6(buffer, length)
        }
    }

    private fun handleIpv4(buffer: ByteArray, length: Int) {
        val udp = Net.parseUdpV4(buffer, length)
        if (udp != null) {
            if (udp.dstPort == DNS_PORT && ResolverIps.isKnownResolverV4(udp.dst)) {
                val query = buffer.copyOfRange(udp.payloadOffset, udp.payloadOffset + udp.payloadLength)
                handleDnsQuery(query, udp.dst, udp.src, udp.srcPort, ipv4 = true)
            }
            return
        }

        val tcp = Net.parseTcpV4(buffer, length) ?: return
        if (tcp.dstPort != PORT_HTTPS && tcp.dstPort != PORT_DOT) return
        if (!ResolverIps.IPV4.any { it.contentEquals(tcp.dst) }) return
        // DNS over TLS and DNS over HTTPS straight to a public resolver IP: refuse the connection
        // so the app falls back to the resolver the system hands out, which we do filter.
        writePacket(Net.buildTcpResetV4(tcp))
    }

    private fun handleIpv6(buffer: ByteArray, length: Int) {
        val udp = Net.parseUdpV6(buffer, length) ?: return
        if (udp.dstPort != DNS_PORT) return
        if (!ResolverIps.isKnownResolverV6(udp.dst)) return
        val query = buffer.copyOfRange(udp.payloadOffset, udp.payloadOffset + udp.payloadLength)
        handleDnsQuery(query, udp.dst, udp.src, udp.srcPort, ipv4 = false)
    }

    private fun handleDnsQuery(
        query: ByteArray,
        replyFrom: ByteArray,
        replyTo: ByteArray,
        replyPort: Int,
        ipv4: Boolean
    ) {
        if (query.size < 12) return
        val question = DnsMessage.parseQuery(query)

        AppGraph.stats.recordQuery(question?.domain ?: "")

        val paused = pausedUntil > System.currentTimeMillis()
        if (question != null && !paused && FilterEngine.decide(question.domain) == FilterEngine.Action.BLOCK) {
            AppGraph.stats.recordBlocked(question.domain)
            // Throttled inside the notifier, so a burst of blocked lookups cannot flood the screen.
            toastNotifier.onBlocked(question.domain, toastEnabled)
            writeResponse(DnsMessage.nxdomain(query, question), replyFrom, replyTo, replyPort, ipv4)
            return
        }

        if (question != null) {
            // Remembered in memory only, so the floating panel can offer to block a host that
            // slipped past the lists while the user is still looking at the page.
            AppGraph.stats.recordAllowed(question.domain)
        }

        val resolver = upstream
        if (resolver == null) {
            writeResponse(DnsMessage.servfail(query, question), replyFrom, replyTo, replyPort, ipv4)
            return
        }

        workers.execute {
            val response = runCatching { resolver.resolve(query) }.getOrNull()
            if (response != null) {
                writeResponse(response, replyFrom, replyTo, replyPort, ipv4)
            } else {
                writeResponse(DnsMessage.servfail(query, question), replyFrom, replyTo, replyPort, ipv4)
            }
        }
    }

    private fun writeResponse(
        payload: ByteArray,
        from: ByteArray,
        to: ByteArray,
        toPort: Int,
        ipv4: Boolean
    ) {
        // A UDP datagram cannot carry more than 65507 bytes and real DNS answers stay far below
        // this, so anything bigger is dropped rather than emitted as a malformed packet.
        if (payload.size > MAX_DNS_PAYLOAD) return
        val packet = try {
            if (ipv4) {
                Net.buildUdpV4(from, to, DNS_PORT, toPort, payload)
            } else {
                Net.buildUdpV6(from, to, DNS_PORT, toPort, payload)
            }
        } catch (throwable: Throwable) {
            return
        }
        writePacket(packet)
    }

    private fun writePacket(packet: ByteArray) {
        val stream = output ?: return
        synchronized(writeLock) {
            runCatching { stream.write(packet) }
        }
    }

    // ------------------------------------------------------------- notification

    // ------------------------------------------------------------- floating panel

    /**
     * Shows or hides the floating bubble. Both the tunnel and the setting decide: a bubble with
     * neither a tunnel nor the user's consent would be an unexplained window on screen.
     */
    private fun applyOverlay() {
        val wanted = overlayEnabled && running
        mainHandler.post {
            runCatching {
                if (wanted && overlayPanel.canDraw()) overlayPanel.show() else overlayPanel.hide()
            }
        }
    }

    private fun startInForeground() {
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        val started = runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        }.getOrDefault(false)
        if (!started) {
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
        foregroundStarted = true
    }

    private fun refreshNotification() {
        if (!foregroundStarted) return
        // Refreshing the counter is a courtesy: when the user has not granted the notification
        // permission the foreground service still keeps filtering, it just stays quiet.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val paused = pausedUntil > now
        val text = when {
            paused -> getString(
                R.string.notif_paused_minutes,
                ((pausedUntil - now) / 60_000L).toInt().coerceAtLeast(1)
            )

            running -> getString(R.string.notif_blocked_today, EngineState.todayBlocked.value)
            else -> getString(R.string.notif_starting)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, AdShieldApp.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (paused) {
            builder.addAction(0, getString(R.string.notif_action_resume), serviceIntent(ACTION_RESUME))
        } else {
            builder.addAction(0, getString(R.string.notif_action_pause), serviceIntent(ACTION_PAUSE, 15))
        }
        builder.addAction(0, getString(R.string.notif_action_stop), serviceIntent(ACTION_STOP))
        return builder.build()
    }

    private fun serviceIntent(action: String, minutes: Int = 0): PendingIntent {
        val intent = Intent(this, AdVpnService::class.java).setAction(action)
        if (minutes > 0) intent.putExtra(EXTRA_MINUTES, minutes)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val ACTION_START = "com.adshield.app.action.START"
        const val ACTION_STOP = "com.adshield.app.action.STOP"
        const val ACTION_PAUSE = "com.adshield.app.action.PAUSE"
        const val ACTION_RESUME = "com.adshield.app.action.RESUME"
        const val ACTION_REBUILD = "com.adshield.app.action.REBUILD"
        const val ACTION_REFRESH_OVERLAY = "com.adshield.app.action.REFRESH_OVERLAY"
        const val EXTRA_MINUTES = "minutes"

        const val NOTIFICATION_ID = 0x5EED

        private const val TUN_ADDRESS4 = "10.111.222.1"
        private const val DNS_PORT = 53
        private const val PORT_HTTPS = 443
        private const val PORT_DOT = 853
        private const val MAX_PACKET = 65_535
        private const val MAX_DNS_PAYLOAD = 8_000
        private const val NOTIFICATION_THROTTLE_MS = 700L
        private const val REBUILD_DEBOUNCE_MS = 900L
        private const val HOUSEKEEPING_INTERVAL_MS = 15_000L

        fun start(context: Context) = send(context, ACTION_START)

        fun stop(context: Context) = send(context, ACTION_STOP)

        fun pause(context: Context, minutes: Int) = send(context, ACTION_PAUSE, minutes)

        fun resume(context: Context) = send(context, ACTION_RESUME)

        fun rebuild(context: Context) = send(context, ACTION_REBUILD)

        /**
         * Asked for when the floating panel is switched on. Deliberately a plain startService:
         * refreshOverlay must not start the tunnel, and a background start would also be
         * rejected on Android 12 and later.
         */
        fun refreshOverlay(context: Context) {
            val intent = Intent(context, AdVpnService::class.java).setAction(ACTION_REFRESH_OVERLAY)
            runCatching { context.applicationContext.startService(intent) }
        }

        private fun send(context: Context, action: String, minutes: Int = 0) {
            val intent = Intent(context, AdVpnService::class.java).setAction(action)
            if (minutes > 0) intent.putExtra(EXTRA_MINUTES, minutes)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
