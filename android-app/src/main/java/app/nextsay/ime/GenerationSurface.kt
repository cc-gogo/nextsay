package app.nextsay.ime

enum class GenerationSurface {
    QUICK,
    ADVANCED,
    IME,
}

enum class GenerationDestination {
    QUICK_WINDOW,
    ADVANCED_PANEL,
    IME_ONLY,
}

class GenerationPresentationPolicy {
    fun destination(surface: GenerationSurface): GenerationDestination = when (surface) {
        GenerationSurface.QUICK -> GenerationDestination.QUICK_WINDOW
        GenerationSurface.ADVANCED -> GenerationDestination.ADVANCED_PANEL
        GenerationSurface.IME -> GenerationDestination.IME_ONLY
    }

    fun shouldRenderOverlay(surface: GenerationSurface): Boolean =
        destination(surface) == GenerationDestination.ADVANCED_PANEL
}
