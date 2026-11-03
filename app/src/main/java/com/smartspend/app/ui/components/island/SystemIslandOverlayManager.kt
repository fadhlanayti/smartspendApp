package com.smartspend.app.ui.components.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.smartspend.app.MainActivity
import com.smartspend.app.core.widget.WidgetSnapshot
import com.smartspend.app.core.widget.WidgetStateStore
import com.smartspend.app.ui.components.CurrencyFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.abs

object SystemIslandOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var overlayRoot: FrameLayout? = null
    private var islandCard: FrameLayout? = null
    private var cardBackground: GradientDrawable? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var activeWindowManager: WindowManager? = null
    private var isAttached = false
    private var isAppForeground = false
    private var isVisibleState = false

    private var currentAnimator: ValueAnimator? = null
    private var dismissAlertRunnable: Runnable? = null
    private var autoCollapseRunnable: Runnable? = null
    private var attachWatchdog: Runnable? = null
    private var activeLoadJob: Job? = null
    private const val AUTO_COLLAPSE_TIMEOUT_MS = 10000L
    private const val ATTACH_WATCHDOG_INTERVAL_MS = 2500L

    // Tuned for Infinix Note 30 Pro (Camera punch hole: X=468..612, Y=0..108px)
    // Small top gap (8dp) gives subtle breathing room from top screen bezel
    private const val TOP_GAP_DP = 8
    private const val CAMERA_CUTOUT_WIDTH_DP = 42
    private const val COMPACT_WIDTH_DP = 142
    private const val COMPACT_HEIGHT_DP = 38
    private const val COMPACT_CORNER_RADIUS_DP = 19

    // Compact tight expanded card: 160dp height with slim margins
    private const val EXPANDED_WIDTH_DP = 330
    private const val EXPANDED_HEIGHT_DP = 198
    private const val EXPANDED_CORNER_RADIUS_DP = 26

    private const val ALERT_WIDTH_DP = 264
    private const val ALERT_HEIGHT_DP = 42
    private const val ALERT_CORNER_RADIUS_DP = 21

    private enum class IslandMode {
        COMPACT,
        ALERT,
        EXPANDED
    }

    private var currentMode = IslandMode.COMPACT
    private var lastSnapshot = WidgetSnapshot()

    // Apple-style fluid spring interpolators
    private val appleSpringEnter = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val appleSpringExit = PathInterpolator(0.3f, 0f, 0.2f, 1f)

    /**
     * Checks if standard SYSTEM_ALERT_WINDOW (Display over other apps) permission is granted.
     * Unlike AccessibilityService, this does NOT trigger banking security warnings.
     */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun onAppForeground(context: Context) {
        isAppForeground = true
        setIslandVisible(context, false)
    }

    fun onAppBackground(context: Context) {
        isAppForeground = false
        mainHandler.postDelayed({
            if (!isAppForeground && canDrawOverlays(context)) {
                setIslandVisible(context, true)
            }
        }, 150L)
    }

    fun setIslandVisible(context: Context, visible: Boolean) {
        isVisibleState = visible
        mainHandler.post {
            if (visible && !isAppForeground) {
                ensureOverlayCreated(context)
                if (isAttached) {
                    overlayRoot?.visibility = View.VISIBLE
                    startAttachWatchdog(context.applicationContext)
                    showCompact(context)
                } else {
                    // Could not attach a window right now (service/token not ready).
                    // Remember the intent and retry instead of silently going dark.
                    scheduleAttachRetry(context.applicationContext)
                }
            } else {
                stopAttachWatchdog()
                activeLoadJob?.cancel()
                overlayRoot?.visibility = View.GONE
                dismissAlertRunnable?.let { mainHandler.removeCallbacks(it) }
                autoCollapseRunnable?.let { mainHandler.removeCallbacks(it) }
                currentAnimator?.cancel()
            }
        }
    }

    /** Read-only view of whether the island is supposed to be on screen right now. */
    fun isIslandVisible(): Boolean = isVisibleState

    /**
     * Keeps the overlay window alive while it is supposed to be visible.
     * A11y overlays get detached by the system / OEM task killers without any
     * callback, which previously left the island blank until the next app switch.
     */
    private fun startAttachWatchdog(context: Context) {
        val appContext = context.applicationContext
        if (attachWatchdog != null) return
        val runnable = object : Runnable {
            override fun run() {
                if (isAppForeground || !isVisibleState) {
                    stopAttachWatchdog()
                    return
                }
                val root = overlayRoot
                if (root == null || !root.isAttachedToWindow) {
                    isAttached = false
                    overlayRoot = null
                    activeWindowManager = null
                    if (canDrawOverlays(appContext)) {
                        ensureOverlayCreated(appContext)
                        if (isAttached) {
                            overlayRoot?.visibility = View.VISIBLE
                            showCompact(appContext)
                        }
                    }
                } else if (root.visibility != View.VISIBLE) {
                    root.visibility = View.VISIBLE
                }
                mainHandler.postDelayed(this, ATTACH_WATCHDOG_INTERVAL_MS)
            }
        }
        attachWatchdog = runnable
        mainHandler.postDelayed(runnable, ATTACH_WATCHDOG_INTERVAL_MS)
    }

    private fun stopAttachWatchdog() {
        attachWatchdog?.let { mainHandler.removeCallbacks(it) }
        attachWatchdog = null
    }

    /** Re-attempts the window attach a few times when the first try was too early. */
    private fun scheduleAttachRetry(context: Context, attempt: Int = 0) {
        if (attempt >= 5) return
        mainHandler.postDelayed({
            if (isAppForeground || !isVisibleState || isAttached) return@postDelayed
            if (!canDrawOverlays(context)) return@postDelayed
            ensureOverlayCreated(context)
            if (isAttached) {
                overlayRoot?.visibility = View.VISIBLE
                startAttachWatchdog(context)
                showCompact(context)
            } else {
                scheduleAttachRetry(context, attempt + 1)
            }
        }, ATTACH_WATCHDOG_INTERVAL_MS)
    }

    fun showCompact(context: Context) {
        val appContext = context.applicationContext
        if (isAppForeground || !isVisibleState || !canDrawOverlays(appContext)) return

        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            val snapshot = runCatching {
                WidgetStateStore.getSnapshotFlow(appContext).first()
            }.getOrDefault(WidgetSnapshot())
            lastSnapshot = snapshot

            mainHandler.post {
                if (isAppForeground || !isVisibleState) return@post
                transitionToMode(appContext, IslandMode.COMPACT)
            }
        }
    }

    fun expand(context: Context) {
        val appContext = context.applicationContext
        if (isAppForeground || !isVisibleState || !canDrawOverlays(appContext)) return

        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            val snapshot = runCatching {
                WidgetStateStore.getSnapshotFlow(appContext).first()
            }.getOrDefault(WidgetSnapshot())
            lastSnapshot = snapshot

            mainHandler.post {
                if (isAppForeground || !isVisibleState) return@post
                dismissAlertRunnable?.let { mainHandler.removeCallbacks(it) }
                transitionToMode(appContext, IslandMode.EXPANDED)
            }
        }
    }

    fun showAlert(
        context: Context,
        title: String,
        amount: Long,
        type: String,
        accountName: String? = null
    ) {
        val appContext = context.applicationContext
        if (isAppForeground || !canDrawOverlays(appContext)) return

        mainHandler.post {
            if (isAppForeground) return@post
            dismissAlertRunnable?.let { mainHandler.removeCallbacks(it) }
            transitionToMode(
                appContext,
                IslandMode.ALERT,
                alertTitle = title,
                alertAmount = amount,
                alertType = type
            )

            dismissAlertRunnable = Runnable {
                if (currentMode == IslandMode.ALERT) {
                    showCompact(appContext)
                }
            }
            mainHandler.postDelayed(dismissAlertRunnable!!, 4500L)
        }
    }

    fun dismiss(context: Context) {
        mainHandler.post {
            setIslandVisible(context, false)
        }
    }

    private fun ensureOverlayCreated(context: Context) {
        if (overlayRoot != null && isAttached) return

        if (!canDrawOverlays(context)) {
            return
        }
        val appContext = context.applicationContext
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        activeWindowManager = wm

        cardBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, COMPACT_CORNER_RADIUS_DP).toFloat()
            setColor(Color.parseColor("#000000")) // Pure black to blend camera punch hole
            setStroke(dpToPx(context, 1), Color.parseColor("#1C1E26"))
        }

        var touchDownY = 0f
        islandCard = FrameLayout(context).apply {
            background = cardBackground
            isClickable = true
            isFocusable = false

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaY = touchDownY - event.rawY
                        if (currentMode == IslandMode.EXPANDED && deltaY > dpToPx(context, 18)) {
                            // Swipe up collapses expanded island
                            transitionToMode(context, IslandMode.COMPACT)
                            true
                        } else if (currentMode == IslandMode.COMPACT && abs(deltaY) < dpToPx(context, 15)) {
                            v.performClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }

            setOnClickListener {
                if (currentMode == IslandMode.COMPACT) {
                    expand(context)
                }
            }
        }

        overlayRoot = object : FrameLayout(context) {
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (currentMode == IslandMode.EXPANDED) {
                    resetAutoCollapseTimer(context)
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            isClickable = false
            isFocusable = false
            visibility = View.VISIBLE
            addView(
                islandCard,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }

        windowLayoutParams = WindowManager.LayoutParams(
            dpToPx(context, COMPACT_WIDTH_DP),
            dpToPx(context, COMPACT_HEIGHT_DP),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(context, TOP_GAP_DP)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(overlayRoot, windowLayoutParams)
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetAutoCollapseTimer(context: Context) {
        autoCollapseRunnable?.let { mainHandler.removeCallbacks(it) }
        if (currentMode == IslandMode.EXPANDED) {
            autoCollapseRunnable = Runnable {
                if (currentMode == IslandMode.EXPANDED) {
                    transitionToMode(context, IslandMode.COMPACT)
                }
            }
            mainHandler.postDelayed(autoCollapseRunnable!!, AUTO_COLLAPSE_TIMEOUT_MS)
        }
    }

    private fun transitionToMode(
        context: Context,
        targetMode: IslandMode,
        alertTitle: String? = null,
        alertAmount: Long? = null,
        alertType: String? = null
    ) {
        ensureOverlayCreated(context)
        val wm = activeWindowManager ?: return
        val root = overlayRoot ?: return
        val card = islandCard ?: return
        val bg = cardBackground ?: return
        val lp = windowLayoutParams ?: return

        if (isAppForeground || !isVisibleState) {
            root.visibility = View.GONE
            return
        }
        root.visibility = View.VISIBLE

        val fromMode = currentMode
        currentMode = targetMode

        if (targetMode == IslandMode.EXPANDED) {
            resetAutoCollapseTimer(context)
        } else {
            autoCollapseRunnable?.let { mainHandler.removeCallbacks(it) }
        }

        val (targetWidthDp, targetHeightDp, targetRadiusDp) = when (targetMode) {
            IslandMode.COMPACT -> Triple(COMPACT_WIDTH_DP, COMPACT_HEIGHT_DP, COMPACT_CORNER_RADIUS_DP)
            IslandMode.ALERT -> Triple(ALERT_WIDTH_DP, ALERT_HEIGHT_DP, ALERT_CORNER_RADIUS_DP)
            IslandMode.EXPANDED -> Triple(EXPANDED_WIDTH_DP, EXPANDED_HEIGHT_DP, EXPANDED_CORNER_RADIUS_DP)
        }

        val startW = lp.width
        val startH = lp.height
        val targetW = dpToPx(context, targetWidthDp)
        val targetH = dpToPx(context, targetHeightDp)

        val startRadius = when (fromMode) {
            IslandMode.COMPACT -> dpToPx(context, COMPACT_CORNER_RADIUS_DP).toFloat()
            IslandMode.ALERT -> dpToPx(context, ALERT_CORNER_RADIUS_DP).toFloat()
            IslandMode.EXPANDED -> dpToPx(context, EXPANDED_CORNER_RADIUS_DP).toFloat()
        }
        val targetRadius = dpToPx(context, targetRadiusDp).toFloat()

        // Create new content view
        val newView = when (targetMode) {
            IslandMode.COMPACT -> buildCompactView(context, lastSnapshot)
            IslandMode.ALERT -> buildAlertView(
                context,
                alertTitle ?: "Transaksi Baru",
                alertAmount ?: 0L,
                alertType ?: "EXPENSE"
            )
            IslandMode.EXPANDED -> buildExpandedView(context, lastSnapshot)
        }

        val oldView = if (card.childCount > 0) card.getChildAt(0) else null

        // Prepare new view for entrance
        newView.alpha = 0f
        if (targetMode == IslandMode.EXPANDED) {
            newView.translationY = -dpToPx(context, 12).toFloat()
        }
        card.addView(newView)

        currentAnimator?.cancel()
        val isExpanding = targetMode == IslandMode.EXPANDED
        val animDuration = if (isExpanding) 320L else 260L
        val interpolator = if (isExpanding) appleSpringEnter else appleSpringExit

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            this.interpolator = interpolator
            addUpdateListener { va ->
                val f = va.animatedFraction
                val curW = (startW + (targetW - startW) * f).toInt()
                val curH = (startH + (targetH - startH) * f).toInt()
                val curR = startRadius + (targetRadius - startRadius) * f

                lp.width = curW
                lp.height = curH
                try {
                    wm.updateViewLayout(root, lp)
                } catch (_: Exception) {}

                bg.cornerRadius = curR

                oldView?.alpha = (1f - (f * 2.2f).coerceAtMost(1f))
                newView.alpha = ((f - 0.2f) * 1.25f).coerceIn(0f, 1f)

                if (isExpanding) {
                    newView.translationY = -dpToPx(context, 12) * (1f - f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    oldView?.let { card.removeView(it) }
                    newView.alpha = 1f
                    newView.translationY = 0f
                }
            })
        }
        currentAnimator = animator
        animator.start()
    }

    /**
     * Compact View: Tuned for Infinix Note 30 Pro
     * Fits in the notch gap between left notification icons and right status icons
     * [🟢 Sisa] --- [42dp Camera Cutout Spacer] --- [107 rb]
     */
    private fun buildCompactView(context: Context, snapshot: WidgetSnapshot): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // --- Left Wing: Status indicator ---
            val leftWing = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(dpToPx(context, 8), 0, 0, 0)

                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (snapshot.remainingDailyBudget > 0) Color.parseColor("#10D876") else Color.parseColor("#FF4757"))
                    }
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 6), dpToPx(context, 6)).apply {
                        marginEnd = dpToPx(context, 3)
                    }
                }
                addView(dot)

                val lbl = TextView(context).apply {
                    text = "Sisa"
                    setTextColor(Color.parseColor("#A0A5B5"))
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(lbl)
            }
            addView(leftWing)

            // --- Center: 42dp Camera Hole Spacer ---
            val cameraSpacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, CAMERA_CUTOUT_WIDTH_DP), dpToPx(context, 1))
            }
            addView(cameraSpacer)

            // --- Right Wing: Budget Amount ---
            val rightWing = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(0, 0, dpToPx(context, 8), 0)

                val amountTv = TextView(context).apply {
                    text = CurrencyFormatter.formatShort(snapshot.remainingDailyBudget)
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                addView(amountTv)
            }
            addView(rightWing)

            setOnClickListener {
                expand(context)
            }
        }
    }

    /**
     * Alert View: Notification banner wrapped around punch hole
     */
    private fun buildAlertView(
        context: Context,
        title: String,
        amount: Long,
        type: String
    ): View {
        val isExpense = type == "EXPENSE"
        val accentColor = if (isExpense) Color.parseColor("#FF4757") else Color.parseColor("#10D876")

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(dpToPx(context, 10), 0, 0, 0)

                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(accentColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 6), dpToPx(context, 6)).apply {
                        marginEnd = dpToPx(context, 5)
                    }
                }
                addView(dot)

                val titleTv = TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                addView(titleTv)
            }
            addView(leftCol)

            val cameraSpacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, CAMERA_CUTOUT_WIDTH_DP), dpToPx(context, 1))
            }
            addView(cameraSpacer)

            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(0, 0, dpToPx(context, 10), 0)

                val amountTv = TextView(context).apply {
                    val sign = if (isExpense) "-" else "+"
                    text = "$sign${CurrencyFormatter.formatShort(amount)}"
                    setTextColor(accentColor)
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                addView(amountTv)
            }
            addView(rightCol)

            setOnClickListener {
                openAppDeepLink(context, "smartspend://transactions")
            }
        }
    }

    /**
     * Compact Expanded View:
     * Slim, tight top/bottom margins (14dp top / 12dp bottom)
     */
    private fun buildExpandedView(context: Context, snapshot: WidgetSnapshot): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dpToPx(context, 16), dpToPx(context, 14), dpToPx(context, 16), dpToPx(context, 12))

            // --- Header Row: Title & Close '✕' ---
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#D8FF5E")) // Electric Lime
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 7), dpToPx(context, 7)).apply {
                    marginEnd = dpToPx(context, 6)
                }
            }
            header.addView(dot)

            val titleTv = TextView(context).apply {
                text = "SmartSpend Island"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            header.addView(titleTv)

            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#8F96A8"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(context, 8), dpToPx(context, 2), dpToPx(context, 4), dpToPx(context, 2))
                setOnClickListener {
                    transitionToMode(context, IslandMode.COMPACT)
                }
            }
            header.addView(closeBtn)
            addView(header)

            // --- Stats Row (Hari ini | Sisa Harian) - tight margins ---
            val statsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(context, 8)
                    bottomMargin = dpToPx(context, 10)
                }
            }

            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val lbl = TextView(context).apply {
                    text = "Hari ini"
                    setTextColor(Color.parseColor("#8F96A8"))
                    textSize = 10.5f
                }
                val valTv = TextView(context).apply {
                    text = CurrencyFormatter.format(snapshot.todayExpense)
                    setTextColor(Color.parseColor("#FF4757"))
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(lbl)
                addView(valTv)
            }
            statsRow.addView(leftCol)

            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val lbl = TextView(context).apply {
                    text = "Sisa Harian"
                    setTextColor(Color.parseColor("#8F96A8"))
                    textSize = 10.5f
                }
                val valTv = TextView(context).apply {
                    text = CurrencyFormatter.format(snapshot.remainingDailyBudget)
                    setTextColor(if (snapshot.remainingDailyBudget > 0) Color.parseColor("#10D876") else Color.parseColor("#FF4757"))
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(lbl)
                addView(valTv)
            }
            statsRow.addView(rightCol)
            addView(statsRow)

            // --- Wealth Row: Total Saldo | Utang | Piutang ---
            val wealthRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(context, 10) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 10).toFloat()
                    setColor(Color.parseColor("#16181F"))
                    setStroke(dpToPx(context, 1), Color.parseColor("#242835"))
                }
                setPadding(dpToPx(context, 8), dpToPx(context, 6), dpToPx(context, 8), dpToPx(context, 6))
            }

            wealthRow.addView(wealthCell(context, "Saldo", snapshot.totalBalance, "#FFFFFF"))
            wealthRow.addView(wealthCell(context, "Utang", -snapshot.totalDebtOwe, "#FF4757"))
            wealthRow.addView(wealthCell(context, "Piutang", snapshot.totalDebtOwed, "#10D876"))
            addView(wealthRow)

            // --- Buttons Row: [+ Catat] [Riwayat] ---
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val catatBtn = TextView(context).apply {
                text = "+ Catat"
                setTextColor(Color.parseColor("#0C0D12"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(context, 8), 0, dpToPx(context, 8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 11).toFloat()
                    setColor(Color.parseColor("#D8FF5E")) // Electric Lime
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dpToPx(context, 8)
                }
                setOnClickListener {
                    openAppDeepLink(context, "smartspend://add?type=expense")
                }
            }
            btnRow.addView(catatBtn)

            val riwayatBtn = TextView(context).apply {
                text = "Riwayat"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(context, 8), 0, dpToPx(context, 8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 11).toFloat()
                    setColor(Color.parseColor("#1C1F2C"))
                    setStroke(dpToPx(context, 1), Color.parseColor("#2E3347"))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    openAppDeepLink(context, "smartspend://transactions")
                }
            }
            btnRow.addView(riwayatBtn)
            addView(btnRow)
        }
    }

    /** One third of the island wealth strip: label + short amount. */
    private fun wealthCell(context: Context, label: String, amount: Long, colorHex: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val lbl = TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#8F96A8"))
                textSize = 9.5f
            }
            addView(lbl)

            val amt = TextView(context).apply {
                text = CurrencyFormatter.formatShort(amount)
                setTextColor(Color.parseColor(colorHex))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            addView(amt)
        }
    }

    private fun openAppDeepLink(context: Context, uriString: String) {
        try {
            // Automatically minimize island when an action is opened
            transitionToMode(context, IslandMode.COMPACT)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
