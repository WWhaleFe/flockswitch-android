// The Google Play build (`-PplayStore`). Play keeps the app up to date itself and does
// not allow an app to update itself, so this build has no update code at all: the
// calls below do nothing, and the manifest asks for no install permission.

package io.github.wwhalefe.flockswitch

import android.app.Activity
import android.content.Context

object SelfUpdate {
    @Suppress("UNUSED_PARAMETER")
    fun autoCheck(context: Context, minGapMs: Long = 0) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun check(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun updateNow(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun onForeground(activity: Activity) = Unit

    @Suppress("UNUSED_PARAMETER")
    suspend fun periodic(context: Context) = Unit
}
