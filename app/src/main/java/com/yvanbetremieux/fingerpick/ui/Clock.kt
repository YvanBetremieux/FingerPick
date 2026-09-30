package com.yvanbetremieux.fingerpick.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis

/** SystemClock.uptimeMillis(), updated every frame. Read it only inside draw lambdas. */
@Composable
fun rememberUptimeClock(): State<Long> = produceState(SystemClock.uptimeMillis()) {
    while (true) withFrameMillis { value = SystemClock.uptimeMillis() }
}
