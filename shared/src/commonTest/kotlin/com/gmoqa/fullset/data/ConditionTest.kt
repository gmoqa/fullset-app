package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Parsing tolerante de la condición: keys canónicas + valores legacy del Excel original. */
class ConditionTest {

    @Test
    fun keysCanonicas() {
        assertEquals(Condition.LOOSE, Condition.fromRaw("loose"))
        assertEquals(Condition.LOOSE_MANUAL, Condition.fromRaw("loose_manual"))
        assertEquals(Condition.BOXED, Condition.fromRaw("boxed"))
        assertEquals(Condition.COMPLETE, Condition.fromRaw("complete"))
    }

    @Test
    fun ignoraMayusculasYEspacios() {
        assertEquals(Condition.COMPLETE, Condition.fromRaw("  COMPLETE "))
        assertEquals(Condition.LOOSE, Condition.fromRaw("Loose"))
    }

    @Test
    fun legacyEspanol() {
        assertEquals(Condition.LOOSE, Condition.fromRaw("Suelto"))
        assertEquals(Condition.LOOSE_MANUAL, Condition.fromRaw("Suelto con manual"))
        assertEquals(Condition.BOXED, Condition.fromRaw("Con caja, sin manual"))
        assertEquals(Condition.COMPLETE, Condition.fromRaw("Con caja y manual"))
        assertEquals(Condition.COMPLETE, Condition.fromRaw("Completo"))
    }

    @Test
    fun legacyCib() = assertEquals(Condition.COMPLETE, Condition.fromRaw("CIB"))

    @Test
    fun sinDato() {
        assertNull(Condition.fromRaw(null))
        assertNull(Condition.fromRaw(""))
        assertNull(Condition.fromRaw("   "))
    }

    @Test
    fun valorDesconocido() = assertNull(Condition.fromRaw("mint"))
}
