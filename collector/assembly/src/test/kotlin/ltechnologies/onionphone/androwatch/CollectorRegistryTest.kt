package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.collector.CollectorRegistry
import ltechnologies.onionphone.androwatch.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorRegistryTest {
    @Test
    fun registryCoversAllCategories() {
        assertEquals(SignalCategory.entries.size, CollectorRegistry.all.size)
        SignalCategory.entries.forEach { category ->
            assertTrue("Missing collector for $category", CollectorRegistry.byCategory.containsKey(category))
        }
    }

    @Test
    fun collectorsHaveUniqueCategories() {
        val categories = CollectorRegistry.all.map { it.category }
        assertEquals(categories.size, categories.toSet().size)
    }
}
