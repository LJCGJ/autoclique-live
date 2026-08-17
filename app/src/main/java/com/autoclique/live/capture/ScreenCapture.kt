package com.autoclique.live.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * Espelha a tela num VirtualDisplay e mantém sempre o último quadro aberto,
 * para conseguir ler a cor de um pixel a qualquer momento sem copiar a tela
 * inteira a cada frame.
 *
 * Nenhuma imagem é salva em disco nem enviada para lugar nenhum.
 */
object ScreenCapture {

    private const val TAG = "ScreenCapture"

    private var projection: MediaProjection? = null
    private var callback: MediaProjection.Callback? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    /** Protege `current`, `imageReader` e `closed` contra a thread do ImageReader. */
    private val lock = Any()
    private var current: Image? = null
    private var imageReader: ImageReader? = null
    private var closed = true

    /** Readers substituídos por um resize e ainda não fechados. */
    private val pendingReaders = mutableListOf<ImageReader>()

    /** Cresce a cada quadro novo — usado para esperar um frame realmente atualizado. */
    @Volatile
    var frameId: Long = 0L
        private set

    @Volatile private var width = 0
    @Volatile private var height = 0

    /** Avisado quando o sistema/usuário encerra o compartilhamento por fora. */
    @Volatile
    var onSessionEnded: (() -> Unit)? = null

    val ready: Boolean get() = virtualDisplay != null

    @Synchronized
    fun start(ctx: Context, mp: MediaProjection): Boolean {
        // Encerra por completo qualquer sessão anterior: duas MediaProjection vivas
        // fazem o onStop() da antiga derrubar a nova.
        val keepListener = onSessionEnded
        stop()
        onSessionEnded = keepListener
        projection = mp
        return runCatching {
            thread = HandlerThread("screen-capture").also { it.start() }
            handler = Handler(thread!!.looper)
            // Obrigatório no Android 14+: registrar o callback antes de criar o display.
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (projection === mp) {
                        Log.i(TAG, "MediaProjection encerrada pelo sistema/usuário")
                        val notify = onSessionEnded
                        stop()
                        notify?.invoke()
                    }
                }
            }
            callback = cb
            mp.registerCallback(cb, handler!!)
            buildDisplay(ctx)
            // createVirtualDisplay pode devolver null sem lançar — nesse caso a sessão
            // está morta e precisa ser limpa, senão vazam thread, reader e projeção.
            checkNotNull(virtualDisplay) { "createVirtualDisplay devolveu null" }
            true
        }.getOrElse {
            Log.e(TAG, "Falha ao iniciar captura", it)
            stop()
            false
        }
    }

    /**
     * Ajusta o display de captura ao novo tamanho da tela (após girar).
     * Usa resize + troca de Surface porque criar um segundo VirtualDisplay
     * na mesma MediaProjection falha a partir do Android 14.
     */
    @Synchronized
    fun refreshSize(ctx: Context) {
        val vd = virtualDisplay ?: return
        val (w, h) = realSize(ctx)
        if (w == width && h == height) return

        runCatching {
            val newReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            attachListener(newReader)

            // Troca a Surface ANTES de publicar o reader novo, para que uma falha
            // aqui não deixe a captura viva mas cega.
            vd.surface = null
            vd.resize(w, h, ctx.resources.displayMetrics.densityDpi)
            vd.surface = newReader.surface

            var old: ImageReader? = null
            synchronized(lock) {
                runCatching { current?.close() }
                current = null
                old = imageReader
                imageReader = newReader
                width = w
                height = h
                old?.let { pendingReaders.add(it) }
            }
            // Só fecha o reader antigo depois que a Surface nova já está em uso.
            handler?.postDelayed({ closePending(old) }, 500L)
        }.onFailure {
            Log.e(TAG, "Falha ao redimensionar captura; encerrando captura", it)
            val notify = onSessionEnded
            stop()
            notify?.invoke()
        }
    }

    private fun closePending(reader: ImageReader?) {
        if (reader == null) return
        synchronized(lock) { pendingReaders.remove(reader) }
        runCatching { reader.close() }
    }

    private fun buildDisplay(ctx: Context) {
        val (w, h) = realSize(ctx)
        val density = ctx.resources.displayMetrics.densityDpi
        width = w
        height = h

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        synchronized(lock) {
            imageReader = reader
            closed = false
        }
        attachListener(reader)

        virtualDisplay = projection?.createVirtualDisplay(
            "autoclique-capture",
            w, h, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    private fun attachListener(reader: ImageReader) {
        reader.setOnImageAvailableListener({ r ->
            val img = runCatching { r.acquireLatestImage() }.getOrNull()
                ?: return@setOnImageAvailableListener
            synchronized(lock) {
                // O reader pode ter sido trocado/fechado enquanto adquiríamos o frame.
                if (closed || imageReader !== r) {
                    runCatching { img.close() }
                    return@synchronized
                }
                runCatching { current?.close() }
                current = img
                frameId++
            }
        }, handler)
    }

    @Synchronized
    fun stop() {
        var readerToClose: ImageReader? = null
        var leftovers: List<ImageReader> = emptyList()
        synchronized(lock) {
            closed = true
            runCatching { current?.close() }
            current = null
            readerToClose = imageReader
            imageReader = null
            leftovers = pendingReaders.toList()
            pendingReaders.clear()
        }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { readerToClose?.close() }
        // quitSafely descarta o postDelayed que fecharia estes — feche na mão.
        leftovers.forEach { runCatching { it.close() } }
        callback?.let { cb -> runCatching { projection?.unregisterCallback(cb) } }
        callback = null
        runCatching { projection?.stop() }
        projection = null
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
        width = 0
        height = 0
        onSessionEnded = null
    }

    /**
     * Cor média de uma janelinha 3x3 em torno de (x, y) no último quadro.
     * A média evita que antialiasing e gradientes do botão façam a cor oscilar.
     * Retorna null se ainda não houver quadro.
     */
    fun pixelAt(x: Int, y: Int): Int? = synchronized(lock) {
        if (closed) return null
        val img = current ?: return null
        return runCatching {
            val plane = img.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val w = img.width
            val h = img.height
            if (x < 0 || y < 0 || x >= w || y >= h) return null

            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (dy in -1..1) {
                val py = (y + dy).coerceIn(0, h - 1)
                for (dx in -1..1) {
                    val px = (x + dx).coerceIn(0, w - 1)
                    val offset = py * rowStride + px * pixelStride
                    if (offset + 2 >= buffer.capacity()) continue
                    r += buffer.get(offset).toInt() and 0xFF
                    g += buffer.get(offset + 1).toInt() and 0xFF
                    b += buffer.get(offset + 2).toInt() and 0xFF
                    n++
                }
            }
            if (n == 0) return null
            Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }.getOrNull()
    }

    /** Tamanho real da tela em pixels. Nunca lança — cai no DisplayMetrics se preciso. */
    fun realSize(ctx: Context): Pair<Int, Int> = runCatching {
        val wm = ctx.getSystemService(WindowManager::class.java)
            ?: return@runCatching fallbackSize(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }.getOrElse { fallbackSize(ctx) }

    private fun fallbackSize(ctx: Context): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }
}
