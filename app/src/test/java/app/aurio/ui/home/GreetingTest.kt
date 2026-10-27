package app.aurio.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test fun usesFirstNameOnly() = assertEquals("Good evening, Aman", greeting("Aman Sharma", 20))
    @Test fun noNameJustGreets() = assertEquals("Good morning", greeting("  ", 9))
    @Test fun partsOfTheDay() {
        assertEquals("Good night, Riya", greeting("Riya", 2))
        assertEquals("Good afternoon, Riya", greeting("Riya", 13))
    }
}
