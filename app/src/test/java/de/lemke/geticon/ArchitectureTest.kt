package de.lemke.geticon

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val scope = Konsist.scopeFromProduction()

    @Test
    fun `data layer does not depend on ui`() {
        scope.files
            .withPackage("de.lemke.geticon.data..")
            .assertFalse { it.hasImport { import -> import.name.startsWith("de.lemke.geticon.ui.") } }
    }

    @Test
    fun `data layer does not depend on domain`() {
        scope.files
            .withPackage("de.lemke.geticon.data..")
            .assertFalse { it.hasImport { import -> import.name.startsWith("de.lemke.geticon.domain.") } }
    }

    @Test
    fun `domain layer does not depend on ui`() {
        scope.files
            .withPackage("de.lemke.geticon.domain..")
            .assertFalse { it.hasImport { import -> import.name.startsWith("de.lemke.geticon.ui.") } }
    }
}
