package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.mni.expand.CompilerStateJournal
import kotlin.test.Test
import kotlin.test.assertTrue

class LambdaDeliveryIntegrationTest {
    @Test
    fun libraryRoundTripThenNormalExportStillWritesImportPackMetadata() {
        val before = CompilerStateJournal.checkpoint()
        try {
            Project.config = ProjectConfig()
            assertTrue(Project.config.copyImport)
            LambdaLibraryTest().freshSessionLibraryClosuresMatchSourceExecution()
            assertTrue(Project.config.copyImport, "Library test export must preserve the normal compiler configuration")
            VersionPreprocessorTest().writesMetadataForLinkedPack()
        } finally {
            before.restore()
        }
    }
}
