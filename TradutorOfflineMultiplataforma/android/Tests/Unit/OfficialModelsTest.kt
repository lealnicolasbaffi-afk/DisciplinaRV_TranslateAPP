package br.edu.tradutoroffline.services.speech

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Optional integration fixtures downloaded from the publisher, never packaged in the app. */
class OfficialModelsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun verifiesAndExtractsOfficialPortugueseArchive() = verify("pt")
    @Test fun verifiesAndExtractsOfficialEnglishArchive() = verify("en")

    private fun verify(language: String) {
        val spec = ModelArchive.spec(language)
        val archive = File("work/models/${spec.directory}.zip")
        assumeTrue("Baixe o ZIP oficial em work/models para verificar esta fixture real.", archive.isFile)
        val verified = temporary.newFile()
        archive.inputStream().use { ModelDownload.copyVerified(it, verified, spec.sha256, archive.length()) }
        val destination = temporary.newFolder()
        val extracted = verified.inputStream().use { ModelArchive.extract(it, destination, spec) }
        assertTrue(extracted.isDirectory)
        ModelArchive.validate(extracted)
    }
}
