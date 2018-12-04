package com.virgilsecurity.android.ethree.utils

class TestUtils {

    companion object {
        const val THROTTLE_TIMEOUT = 2 * 1000L // 2 seconds
        const val REQUEST_TIMEOUT = 10L // 10 seconds

        fun pause() {
            Thread.sleep(THROTTLE_TIMEOUT)
        }
    }
}