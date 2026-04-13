package com.marfeel.compass.experiences

import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.experiences.model.RecirculationModule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class WholeModuleAugmenterTest {
    private var pageUrl: String? = "https://page-1"
    private val augmenter = WholeModuleAugmenter { pageUrl }

    private val wholeModule = WholeModuleAugmenter.wholeModuleLink()

    private fun module(name: String, vararg positions: String): RecirculationModule =
        RecirculationModule(
            name = name,
            links = positions.map { RecirculationLink(url = "https://$name/$it", position = it) }
        )

    @Test
    fun `elegible first time appends whole-module link`() {
        val result = augmenter.onElegible(listOf(module("mod-a", "0", "1")))
        assertEquals(3, result[0].links.size)
        assertEquals(wholeModule, result[0].links.last())
    }

    @Test
    fun `elegible second time for same module does not re-append`() {
        augmenter.onElegible(listOf(module("mod-a", "0")))
        val second = augmenter.onElegible(listOf(module("mod-a", "0", "1", "2")))
        assertFalse(second[0].links.contains(wholeModule))
    }

    @Test
    fun `elegible appends only to modules not yet seen`() {
        augmenter.onElegible(listOf(module("mod-a", "0")))
        val result = augmenter.onElegible(listOf(module("mod-a", "0"), module("mod-b", "0")))
        assertFalse(result[0].links.contains(wholeModule))
        assertTrue(result[1].links.contains(wholeModule))
    }

    @Test
    fun `impression after elegible appends whole-module once`() {
        augmenter.onElegible(listOf(module("mod-a", "0")))
        val first = augmenter.onImpression(module("mod-a", "0"))
        val second = augmenter.onImpression(module("mod-a", "0"))

        assertTrue(first.links.contains(wholeModule))
        assertFalse(second.links.contains(wholeModule))
    }

    @Test
    fun `impression without prior elegible does not append whole-module`() {
        val result = augmenter.onImpression(module("mod-orphan", "0"))
        assertFalse(result.links.contains(wholeModule))
    }

    @Test
    fun `page url change resets module state`() {
        augmenter.onElegible(listOf(module("mod-a", "0")))
        pageUrl = "https://page-2"
        val second = augmenter.onElegible(listOf(module("mod-a", "0")))
        assertTrue(second[0].links.contains(wholeModule))
    }

    @Test
    fun `whole-module link has position 255 and space url`() {
        assertEquals("255", wholeModule.position)
        assertEquals(" ", wholeModule.url)
    }

    @Test
    fun `client-provided links are preserved when augmenting`() {
        val original = module("mod-a", "0", "1", "42")
        val result = augmenter.onElegible(listOf(original))
        assertEquals(original.links, result[0].links.dropLast(1))
    }
}
