package com.zirakusa.stone.nativeapp.ui

import android.os.Build
import android.util.Log
import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Collections
import java.util.IdentityHashMap

internal val LocalImmersiveSystemBarsController =
    staticCompositionLocalOf<ImmersiveSystemBarsController?> { null }

/**
 * The single owner of system-bar visibility for Stone's Activity and Compose dialog windows.
 *
 * Compose bottom sheets and alerts are backed by their own Window. Registering those windows
 * here prevents a sheet from silently replacing the Activity's immersive policy. Insets are
 * observed but never consumed, so the IME, permission dialogs and native system gestures keep
 * their normal behavior.
 */
internal class ImmersiveSystemBarsController(private val activity: ComponentActivity) {
    private companion object {
        const val TAG = "StoneImmersive"
    }

    private class ManagedWindow(val window: Window, val root: Boolean) {
        val decorView get() = window.decorView
        var attached = false
        var hasFocus = false
        var applying = false
        var pendingHide = false
        var userRevealedTransientBars = false
        var lastSystemBarsVisible: Boolean? = null
        var systemBarsVisible = true
        var configuredForImmersive = false
        var baseBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        var baseCutoutMode: Int? = null
        var insetsListener: OnApplyWindowInsetsListener? = null
        var focusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
        var animationListener: WindowInsetsAnimationCompat.Callback? = null
        var systemBarsAnimationRunning = false
    }

    private val rootWindow = activity.window
    private val windows = Collections.synchronizedMap(IdentityHashMap<Window, ManagedWindow>())
    private var root: ManagedWindow? = null
    private var attached = false
    private var resumed = false
    private var hasActivityFocus = false
    private var requested = false

    /** True while Android reports visible bars for the Activity window. */
    var systemBarsVisible by mutableStateOf(true)
        private set

    /** True while the on-screen keyboard is visible in the Activity window. */
    var imeVisible by mutableStateOf(false)
        private set

    fun attach() {
        if (attached) return
        attached = true
        root = registerWindowInternal(rootWindow, root = true)
        // Configure edge-to-edge once. Immersive mode changes visibility and cutout policy,
        // but never delegates those decisions to individual screens.
        WindowCompat.setDecorFitsSystemWindows(rootWindow, false)
        ViewCompat.requestApplyInsets(rootWindow.decorView)
        log("attach activity window=${windowId(rootWindow)}")
    }

    /** Registers a Compose ModalBottomSheet/AlertDialog window with the same policy. */
    fun registerWindow(window: Window) {
        if (!attached || window === rootWindow) return
        val managed = registerWindowInternal(window, root = false)
        log("register dialog window=${windowId(window)} requested=$requested")
        if (requested) reconcile(managed, force = true)
    }

    fun unregisterWindow(window: Window) {
        if (window === rootWindow) return
        val managed = windows.remove(window) ?: return
        // The dialog window is going away. Showing bars here can leak a visibility change
        // into the next window transition and is not needed for a destroyed window.
        managed.attached = false
        val decor = managed.decorView
        managed.insetsListener?.let { ViewCompat.setOnApplyWindowInsetsListener(decor, null) }
        managed.animationListener?.let { ViewCompat.setWindowInsetsAnimationCallback(decor, null) }
        managed.focusListener?.let { listener ->
            if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
        log("unregister dialog window=${windowId(window)}")
    }

    fun setImmersiveEnabled(enabled: Boolean) {
        if (!attached) return
        if (requested == enabled) {
            if (!enabled) restoreAll() else reconcileAll(force = false)
            return
        }
        log("request immersive=$enabled")
        requested = enabled
        resetTransientRevealState()
        if (enabled) reconcileAll(force = true) else restoreAll()
    }

    fun onResume() {
        log("lifecycle onResume requested=$requested")
        resumed = true
        root?.hasFocus = hasActivityFocus
        resetTransientRevealState()
        reconcileAll(force = true)
    }

    fun onPause() {
        log("lifecycle onPause")
        resumed = false
    }

    fun onStop() {
        log("lifecycle onStop requested=$requested")
        resumed = false
        hasActivityFocus = false
        root?.hasFocus = false
        resetTransientRevealState()
        restoreAll()
    }

    fun onWindowFocusChanged(focused: Boolean) {
        log("activity focus=$focused requested=$requested")
        hasActivityFocus = focused
        root?.hasFocus = focused
        if (focused) reconcileAll(force = false)
    }

    fun onConfigurationChanged() {
        log("configuration changed requested=$requested")
        resetTransientRevealState()
        windows.values.toList().forEach { managed ->
            managed.lastSystemBarsVisible = null
            managed.configuredForImmersive = false
            ViewCompat.requestApplyInsets(managed.decorView)
        }
        reconcileAll(force = true)
    }

    fun dispose() {
        if (!attached) return
        log("dispose")
        requested = false
        resumed = false
        hasActivityFocus = false
        restoreAll()
        windows.values.toList().filterNot { it.root }.forEach { managed ->
            val decor = managed.decorView
            managed.insetsListener?.let { ViewCompat.setOnApplyWindowInsetsListener(decor, null) }
            managed.animationListener?.let { ViewCompat.setWindowInsetsAnimationCallback(decor, null) }
            managed.focusListener?.let { listener ->
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            }
        }
        windows.clear()
        root = null
        attached = false
    }

    private fun registerWindowInternal(window: Window, root: Boolean): ManagedWindow {
        windows[window]?.let { return it }
        val managed = ManagedWindow(window, root)
        managed.baseBarsBehavior = WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior
        managed.baseCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode
        } else null
        managed.hasFocus = window.decorView.hasWindowFocus()
        managed.attached = true
        managed.insetsListener = OnApplyWindowInsetsListener { _, insets ->
            onInsets(managed, insets)
            insets
        }
        managed.focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            managed.hasFocus = focused
            log("window focus=${focused} window=${windowId(window)} requested=$requested")
            if (focused && requested) reconcile(managed, force = false)
        }
        managed.animationListener = object : WindowInsetsAnimationCompat.Callback(
            WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.systemBars() != 0) {
                    managed.systemBarsAnimationRunning = true
                    log("system-bar animation start window=${windowId(window)} pendingHide=${managed.pendingHide}")
                }
                super.onPrepare(animation)
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat = insets

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.systemBars() != 0) {
                    managed.systemBarsAnimationRunning = false
                    log("system-bar animation end window=${windowId(window)} visible=${managed.systemBarsVisible}")
                }
                super.onEnd(animation)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView, managed.insetsListener)
        ViewCompat.setWindowInsetsAnimationCallback(window.decorView, managed.animationListener)
        window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(managed.focusListener)
        windows[window] = managed
        if (root) systemBarsVisible = managed.systemBarsVisible
        return managed
    }

    private fun onInsets(managed: ManagedWindow, insets: WindowInsetsCompat) {
        val barsVisible = insets.isVisible(WindowInsetsCompat.Type.systemBars())
        val keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
        val wasVisible = managed.lastSystemBarsVisible
        if (managed.pendingHide && !barsVisible) managed.pendingHide = false
        if (!barsVisible && managed.userRevealedTransientBars) {
            managed.userRevealedTransientBars = false
            log("system bars hidden after transient reveal window=${windowId(managed.window)}")
        }
        managed.lastSystemBarsVisible = barsVisible
        managed.systemBarsVisible = barsVisible
        if (requested && !managed.applying && !managed.pendingHide &&
            wasVisible == false && barsVisible) {
            if (managed.systemBarsAnimationRunning) {
                // A real system-edge gesture animates the system-bar insets. Leave those bars
                // transiently visible and let Android dismiss them according to its policy.
                managed.userRevealedTransientBars = true
                log("transient system gesture revealed bars window=${windowId(managed.window)}")
            } else {
                // A content/window transition re-dispatched visible insets without a system
                // gesture. Reconcile once at the central owner instead of accepting overlap.
                managed.userRevealedTransientBars = false
                log("unexpected visible insets; reapplying immersive window=${windowId(managed.window)}")
                managed.systemBarsVisible = true
                reconcile(managed, force = false)
            }
        }
        if (managed.root) {
            systemBarsVisible = managed.systemBarsVisible
            imeVisible = keyboardVisible
        }
    }

    private fun reconcileAll(force: Boolean) {
        if (!attached || !requested) return
        windows.values.toList().forEach { reconcile(it, force) }
    }

    private fun reconcile(managed: ManagedWindow, force: Boolean) {
        if (!attached || !requested || !managed.attached) return
        // The Activity must be resumed and focused. A dialog is already a visible independent
        // window when its composition registers it, so it can be handled immediately.
        if (managed.root && (!resumed || !hasActivityFocus)) return
        if (managed.userRevealedTransientBars && !force) return
        applyImmersive(managed, force)
    }

    private fun applyImmersive(managed: ManagedWindow, force: Boolean) {
        if (!force && managed.pendingHide) return
        val controller = WindowCompat.getInsetsController(managed.window, managed.decorView)
        if (!managed.configuredForImmersive) {
            WindowCompat.setDecorFitsSystemWindows(managed.window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attributes = managed.window.attributes
                attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                managed.window.attributes = attributes
            }
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            managed.configuredForImmersive = true
        }
        if (force || managed.systemBarsVisible) {
            log("hide system bars window=${windowId(managed.window)} force=$force")
            managed.pendingHide = true
            managed.applying = true
            controller.hide(WindowInsetsCompat.Type.systemBars())
            managed.applying = false
            managed.systemBarsVisible = false
            managed.lastSystemBarsVisible = false
        }
    }

    private fun restoreAll() = windows.values.toList().forEach(::restore)

    private fun restore(managed: ManagedWindow) {
        if (!managed.attached) return
        val controller = WindowCompat.getInsetsController(managed.window, managed.decorView)
        if (managed.configuredForImmersive || requested || !managed.systemBarsVisible) {
            log("show system bars window=${windowId(managed.window)}")
            managed.pendingHide = false
            managed.applying = true
            controller.show(WindowInsetsCompat.Type.systemBars())
            managed.applying = false
            controller.systemBarsBehavior = managed.baseBarsBehavior
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && managed.baseCutoutMode != null) {
                val attributes = managed.window.attributes
                attributes.layoutInDisplayCutoutMode = managed.baseCutoutMode!!
                managed.window.attributes = attributes
            }
            managed.configuredForImmersive = false
            managed.systemBarsVisible = true
            managed.lastSystemBarsVisible = true
            if (managed.root) systemBarsVisible = true
        }
    }

    private fun resetTransientRevealState() {
        windows.values.toList().forEach {
            it.userRevealedTransientBars = false
            it.pendingHide = false
            it.systemBarsAnimationRunning = false
        }
    }

    private fun windowId(window: Window): String =
        if (window === rootWindow) "activity" else "dialog#${System.identityHashCode(window)}"

    private fun log(message: String) = Log.d(TAG, message)
}

/** Registers the independent Window used by a Compose sheet or alert. */
@Composable
internal fun ImmersiveWindowEffect() {
    val controller = LocalImmersiveSystemBarsController.current
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(controller, dialogWindow) {
        if (controller != null && dialogWindow != null) controller.registerWindow(dialogWindow)
        onDispose {
            if (controller != null && dialogWindow != null) controller.unregisterWindow(dialogWindow)
        }
    }
}
