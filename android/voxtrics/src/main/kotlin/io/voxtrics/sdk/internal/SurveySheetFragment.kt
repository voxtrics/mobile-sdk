package io.voxtrics.sdk.internal

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Outline
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.voxtrics.sdk.R

/**
 * The sheet a survey is shown in. It holds no state of its own: the WebView, already loaded, and its height are the
 * active SurveySession's. Not draggable - the survey is a scrolling page, and a sheet that also moves with the finger
 * fights it; Back, a tap outside and the survey's own close control close it.
 *
 * Recreated by the system after a configuration change, it takes the still open survey over the new activity;
 * recreated after the process was restored, it finds no survey and goes away.
 */
internal class SurveySheetFragment : BottomSheetDialogFragment() {
    private var orphaned = false
    private var contentHeight = 0

    // While the keyboard is up: the room between it and the status bar. The sheet never reaches under the keyboard -
    // it shrinks, sits on it, and the page scrolls the field being typed in into view.
    private var keyboardCap = Int.MAX_VALUE
    private var keyboardHeight = 0

    // Where the sheet goes down under the navigation bar and the keyboard (edge to edge: always from Android 15), the
    // page reaches under the bar too while the keyboard is down - its own background fills it - and its content ends
    // above it; with the keyboard up the sheet is lifted onto it. Both read from the insets that reach the sheet: where
    // the dialog keeps clear of the bar and the keyboard itself, they arrive without them.
    private var navigationBarHeight = 0
    private var keyboardUnderSheet = 0

    override fun getTheme(): Int = R.style.Voxtrics_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orphaned = SurveySession.active?.isShown != true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setCanceledOnTouchOutside(true)
        dialog.behavior.apply {
            isDraggable = false
            skipCollapsed = true
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val frame = FrameLayout(inflater.context).apply {
            // The page's own background, under rounded top corners.
            clipToOutline = true
            outlineProvider = TopRoundedOutline(resources.getDimension(R.dimen.voxtrics_sheet_corner_radius))
        }
        val session = SurveySession.active
        if (!orphaned && session != null) {
            session.adopt(requireActivity())
            (session.webView.parent as? ViewGroup)?.removeView(session.webView)
            frame.addView(session.webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            session.onSheetCreated(this)
        }
        return frame
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SurveySession.active?.takeIf { !orphaned }?.let { setContentHeight(it.sheetHeight()) }
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            keyboardUnderSheet = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            applyHeight()
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        if (orphaned) {
            dismissAllowingStateLoss()
            return
        }
        val window = dialog?.window ?: return
        // Resize, not pan: panning slides the whole sheet up under the status bar and leaves its lower part - Submit -
        // under the keyboard. Resizing hands the keyboard's height to the listener below.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        SurveySession.active?.let {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !it.isDarkPage
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { root, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            keyboardHeight = ime
            keyboardCap = if (ime > 0) root.height - ime - statusBar else Int.MAX_VALUE
            applyHeight()
            // The sheet's own insets handling still runs.
            ViewCompat.onApplyWindowInsets(root, insets)
        }
    }

    /** The survey's height, already capped at the session's maximum - the page scrolls past it. */
    fun setContentHeight(height: Int) {
        contentHeight = height
        applyHeight()
    }

    private fun applyHeight() {
        val frame = view ?: return
        val keyboardUp = keyboardHeight > 0
        val height = if (keyboardUp) minOf(contentHeight, keyboardCap) else contentHeight + navigationBarHeight
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val bottomPadding = if (keyboardUp) keyboardUnderSheet else 0
        if (frame.layoutParams?.height == height && sheet?.paddingBottom == bottomPadding) return
        sheet?.updatePadding(bottom = bottomPadding)
        frame.updateLayoutParams<ViewGroup.LayoutParams> { this.height = height }
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // A configuration change takes the dialog down with the old activity; the survey stays open for the new one.
        if (!orphaned && activity?.isChangingConfigurations != true) SurveySession.active?.onSheetDismissed()
    }

    override fun onDestroyView() {
        // The WebView is the session's: it is destroyed when the session ends, never with this view.
        (view as? ViewGroup)?.removeAllViews()
        SurveySession.active?.onSheetDestroyed(this)
        super.onDestroyView()
    }

    private class TopRoundedOutline(private val radius: Float) : ViewOutlineProvider() {
        // Taller than the view by the radius, so only the top corners round.
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height + radius.toInt(), radius)
        }
    }

    companion object {
        const val TAG = "io.voxtrics.sdk.SurveySheet"
    }
}
