package app.nextsay.contacts

object GenerationEligibility {
    fun unlocked(interactive: Boolean, keyguardLocked: Boolean, deviceLocked: Boolean) = interactive && !keyguardLocked && !deviceLocked
}
