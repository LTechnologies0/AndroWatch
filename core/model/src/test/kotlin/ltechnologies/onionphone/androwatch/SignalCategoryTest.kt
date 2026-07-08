package ltechnologies.onionphone.androwatch

import ltechnologies.onionphone.androwatch.model.Sensitivity
import ltechnologies.onionphone.androwatch.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalCategoryTest {
    @Test
    fun hasExpectedTierCounts() {
        assertEquals(17, SignalCategory.entries.count { it.sensitivity == Sensitivity.Passive })
        assertEquals(10, SignalCategory.entries.count { it.sensitivity == Sensitivity.Permissioned })
        assertEquals(3, SignalCategory.entries.count { it.sensitivity == Sensitivity.Advanced })
    }

    @Test
    fun permissionedCategoriesHavePermissionKind() {
        assertTrue(
            SignalCategory.entries
                .filter { it.sensitivity == Sensitivity.Permissioned }
                .all { it.permissionKind != null },
        )
    }
}
