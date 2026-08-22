package com.campusai

import com.campusai.core.localai.Sha256Verifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256VerifierTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `accepts only matching size and sha256`() {
        val file = temporary.newFile("model.part").apply { writeText("CampusAI") }
        val sha = Sha256Verifier.digest(file)
        assertTrue(Sha256Verifier.verify(file, 8, sha))
        assertFalse(Sha256Verifier.verify(file, 7, sha))
        assertFalse(Sha256Verifier.verify(file, 8, "0".repeat(64)))
    }
}
