package app.nextsay.insertion

class InsertionGate {
    private var inProgress = false

    fun tryStart(): Boolean {
        if (inProgress) return false
        inProgress = true
        return true
    }

    fun finish() {
        inProgress = false
    }
}
