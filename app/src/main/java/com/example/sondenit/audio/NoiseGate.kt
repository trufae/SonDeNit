package com.example.sondenit.audio

/**
 * Adaptive noise gate. Maintains an exponentially-smoothed estimate of the
 * ambient noise floor (in dBFS) using only "quiet" frames. A frame is treated
 * as "active" if its RMS exceeds the floor by [activationDb]. Hysteresis is
 * provided by tracking how many consecutive silent frames have been seen.
 */
class NoiseGate(
    private val attackDb: Float = 12f,        // open above floor + attackDb
    private val releaseDb: Float = 6f,        // close below floor + releaseDb
    private val floorAlpha: Float = 0.02f,    // EMA weight for new quiet frames
    private val initialFloorDb: Float = -55f, // assume reasonably quiet room
) {

    var floorDb: Float = initialFloorDb
        private set

    /**
     * Returns true if this frame should be considered "active" (above gate).
     * Updates the ambient floor estimate when frames are quiet.
     */
    fun process(frameRmsDb: Float, currentlyOpen: Boolean): Boolean {
        val openThreshold = floorDb + attackDb
        val closeThreshold = floorDb + releaseDb
        val isActive = if (currentlyOpen) {
            frameRmsDb >= closeThreshold
        } else {
            frameRmsDb >= openThreshold
        }

        // Only adapt the floor when the gate is closed and the frame is quiet.
        if (!currentlyOpen && !isActive) {
            floorDb = floorDb * (1f - floorAlpha) + frameRmsDb * floorAlpha
        }
        return isActive
    }
}
