package app.aurio.ui.levelup

import app.aurio.domain.AuraLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelLineTest {
    @Test fun saysTheRangeAndWhatIsLeftToTheNextLevel() {
        assertEquals("Glow is 400 to 599 aura. 188 more to Radiant.", levelLine(AuraLevel.GLOW, 412))
    }

    @Test fun haloIsTheTop() {
        assertEquals("Halo is the top level, 800 to 1000 aura.", levelLine(AuraLevel.HALO, 812))
    }
}
