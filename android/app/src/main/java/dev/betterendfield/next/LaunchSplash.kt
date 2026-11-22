package dev.betterendfield.next

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.StyleRes
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.doOnPreDraw

private const val LogoHoldMillis = 50L
private const val SplashFadeMillis = 140L

internal fun Activity.rememberPlatformSplashTheme(@StyleRes startingTheme: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        splashScreen.setSplashScreenTheme(startingTheme)
    }
}

internal fun finishLaunchSplash(
    provider: SplashScreenViewProvider,
    appearance: AppAppearance,
    onFinished: () -> Unit,
) {
    val cover = provider.view
    cover.setBackgroundColor(cover.context.getColor(appearance.background))
    val ink = cover.context.getColor(appearance.controlAccent)
    val finish = {
        provider.remove()
        onFinished()
    }
    if (!ValueAnimator.areAnimatorsEnabled()) {
        finish()
        return
    }

    fun fade() {
        cover.animate().alpha(0f).setDuration(SplashFadeMillis).withEndAction(finish).start()
    }

    fun fadeAfter(delayMillis: Long, handOffNativeLogo: Boolean = false) {
        cover.postOnAnimationDelayed({
            val logo = if (handOffNativeLogo) {
                // Draw the exact final glyph into the cover so the logo and
                // backdrop fade together even when the system uses a SurfaceView.
                cover.context.getDrawable(R.drawable.be_splash_finished)?.let { addLogo(provider, it, ink) }
            } else null
            if (logo != null) {
                logo.doOnPreDraw {
                    provider.iconView.visibility = View.INVISIBLE
                    fade()
                }
            } else fade()
        }, delayMillis)
    }

    val iconStart = provider.iconAnimationStartMillis
    val iconDuration = provider.iconAnimationDurationMillis
    if (iconStart > 0L && iconDuration > 0L) {
        // Native metadata is expressed in epoch time. Content has drawn by the
        // time the exit listener runs, even when it takes longer than the icon.
        val wait = (iconStart + iconDuration + LogoHoldMillis - System.currentTimeMillis())
            .coerceIn(0L, iconDuration + LogoHoldMillis)
        fadeAfter(wait, handOffNativeLogo = true)
        return
    }

    // AndroidX supplies the surface on Android 10/11, where WindowManager
    // cannot animate the launch icon. This also handles a native surface
    // with no animated icon, such as when a device omits it for a warm start.
    val animation = cover.context.getDrawable(R.drawable.be_splash_animation) as? AnimatedVectorDrawable
    val logo = animation?.let { addLogo(provider, it, ink) }
    if (logo == null) {
        fadeAfter(0)
        return
    }
    animation.registerAnimationCallback(object : Animatable2.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable) {
            animation.unregisterAnimationCallback(this)
            fadeAfter(LogoHoldMillis)
        }
    })
    logo.doOnPreDraw {
        provider.iconView.visibility = View.INVISIBLE
        animation.start()
    }
}

private fun addLogo(provider: SplashScreenViewProvider, drawable: Drawable, ink: Int): ImageView? {
    val cover = provider.view as? FrameLayout ?: return null
    val size = cover.resources.getDimensionPixelSize(R.dimen.be_splash_icon_size)
    val logo = ImageView(cover.context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(drawable.mutate().apply { setTint(ink) })
    }
    cover.addView(logo, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
    return logo
}
