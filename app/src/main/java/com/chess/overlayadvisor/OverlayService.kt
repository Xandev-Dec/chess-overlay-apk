package com.chess.overlayadvisor

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.move.Move
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var controlPanel: LinearLayout

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var playingAsWhite = true

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        setupOverlayCanvas()
        setupFloatingControlBar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("DATA")
        }

        if (data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
            setupVirtualDisplay()
        }
        return START_STICKY
    }

    private fun setupOverlayCanvas() {
        overlayView = OverlayView(this)
        val margin = 36f
        val size = screenWidth - (margin * 2)
        val topY = (screenHeight - size) / 2f
        overlayView.boardRect.set(margin, topY, margin + size, topY + size)

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            flag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(overlayView, params)
    }

    private fun setupFloatingControlBar() {
        controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1E1E1E.toInt())
            setPadding(12, 8, 12, 8)
        }

        val btnScan = Button(this).apply {
            text = "▶ Scan"
            textSize = 11f
            setOnClickListener {
                isScanning = !isScanning
                text = if (isScanning) "⏸ Stop" else "▶ Scan"
                if (isScanning) startScanLoop()
            }
        }

        val btnSide = Button(this).apply {
            text = "Putih"
            textSize = 11f
            setOnClickListener {
                playingAsWhite = !playingAsWhite
                text = if (playingAsWhite) "Putih" else "Hitam"
            }
        }

        val btnGrid = Button(this).apply {
            text = "Grid"
            textSize = 11f
            setOnClickListener {
                overlayView.showCalibrationGrid = !overlayView.showCalibrationGrid
                overlayView.invalidate()
            }
        }

        val btnUp = Button(this).apply {
            text = "▲"
            textSize = 11f
            setOnClickListener {
                overlayView.boardRect.offset(0f, -15f)
                overlayView.invalidate()
            }
        }

        val btnDown = Button(this).apply {
            text = "▼"
            textSize = 11f
            setOnClickListener {
                overlayView.boardRect.offset(0f, 15f)
                overlayView.invalidate()
            }
        }

        controlPanel.addView(btnScan)
        controlPanel.addView(btnSide)
        controlPanel.addView(btnGrid)
        controlPanel.addView(btnUp)
        controlPanel.addView(btnDown)

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            flag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        windowManager.addView(controlPanel, params)
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ChessCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startScanLoop() {
        if (!isScanning) return
        captureAndAnalyze()
        handler.postDelayed({ startScanLoop() }, 1200)
    }

    private fun captureAndAnalyze() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val fen = detectBoardToFen(bitmap)
            bitmap.recycle()

            if (fen != null) {
                val suggestions = computeTopMoves(fen)
                overlayView.arrows = suggestions
                overlayView.postInvalidate()
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    /**
     * Deteksi okupansi bidak per petak berdasarkan varians kontras warna tengah petak
     * terhadap warna sudut petak papan.
     */
    private fun detectBoardToFen(bmp: Bitmap): String? {
        val rect = overlayView.boardRect
        val cell = rect.width() / 8f
        if (rect.left < 0 || rect.right > bmp.width || rect.top < 0 || rect.bottom > bmp.height) return null

        val grid = Array(8) { CharArray(8) { '.' } }

        for (r in 0..7) {
            for (c in 0..7) {
                val cx = (rect.left + (c + 0.5f) * cell).toInt()
                val cy = (rect.top + (r + 0.5f) * cell).toInt()
                val cornerX = (rect.left + (c + 0.12f) * cell).toInt()
                val cornerY = (rect.top + (r + 0.12f) * cell).toInt()

                val bgPixel = bmp.getPixel(cornerX, cornerY)
                val centerPixel = bmp.getPixel(cx, cy)

                val diff = colorDistance(bgPixel, centerPixel)
                if (diff > 45) {
                    val lum = luminance(centerPixel)
                    grid[r][c] = if (lum > 145) 'P' else 'p'
                }
            }
        }

        // Pastikan kedua Raja selalu valid agar chesslib tidak melempar error
        if (!grid.any { it.contains('K') }) grid[7][4] = 'K'
        if (!grid.any { it.contains('k') }) grid[0][4] = 'k'

        val sb = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (c in 0..7) {
                val ch = grid[r][c]
                if (ch == '.') empty++ else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(ch)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(if (playingAsWhite) " w - - 0 1" else " b - - 0 1")
        return sb.toString()
    }

    private fun computeTopMoves(fen: String): List<MoveArrow> {
        return try {
            val board = Board()
            board.loadFromFen(fen)
            val legalMoves = board.legalMoves()
            if (legalMoves.isEmpty()) return emptyList()

            val scored = legalMoves.map { move ->
                board.doMove(move)
                val eval = evaluateMaterial(board) * (if (playingAsWhite) 1 else -1)
                board.undoMove()
                Pair(move, eval)
            }.sortedByDescending { it.second }.take(3)

            val bestVal = scored.first().second
            scored.mapIndexed { idx, pair ->
                val m: Move = pair.first
                val fromCol = m.from.file.ordinal
                val fromRow = 7 - m.from.rank.ordinal
                val toCol = m.to.file.ordinal
                val toRow = 7 - m.to.rank.ordinal
                val scorePct = if (idx == 0) 100 else (85 - idx * 9).coerceIn(50, 96)
                MoveArrow(fromCol, fromRow, toCol, toRow, scorePct, idx == 0)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun evaluateMaterial(board: Board): Int {
        var score = 0
        for (sq in com.github.bhlangonijr.chesslib.Square.values()) {
            val p = board.getPiece(sq)
            if (p == Piece.NONE) continue
            val v = when (p.pieceType) {
                com.github.bhlangonijr.chesslib.PieceType.PAWN -> 100
                com.github.bhlangonijr.chesslib.PieceType.KNIGHT -> 320
                com.github.bhlangonijr.chesslib.PieceType.BISHOP -> 330
                com.github.bhlangonijr.chesslib.PieceType.ROOK -> 500
                com.github.bhlangonijr.chesslib.PieceType.QUEEN -> 900
                else -> 0
            }
            score += if (p.pieceSide == Side.WHITE) v else -v
        }
        return score
    }

    private fun colorDistance(c1: Int, c2: Int): Int {
        val dr = abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF))
        val dg = abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF))
        val db = abs((c1 and 0xFF) - (c2 and 0xFF))
        return (dr + dg + db) / 3
    }

    private fun luminance(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun startForegroundNotification() {
        val id = "chess_overlay_srv"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(id, "Chess Overlay Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = Notification.Builder(this, id)
            .setContentTitle("Chess Overlay Advisor")
            .setContentText("Layanan pembaca layar aktif")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(101, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        virtualDisplay?.release()
        mediaProjection?.stop()
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        if (::controlPanel.isInitialized) windowManager.removeView(controlPanel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
