package com.campusai

import android.content.Context
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyBackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `legacy backup keeps conversations and credentials on device`() {
        val exclusions = exclusionsIn(R.xml.backup_rules)

        assertTrue(Exclusion(domain = "database", path = ".") in exclusions)
        assertTrue(Exclusion(domain = "sharedpref", path = SECURE_PREFERENCES_FILE) in exclusions)
    }

    @Test fun `cloud backup and device transfer both exclude conversations and credentials`() {
        val exclusions = exclusionsIn(R.xml.data_extraction_rules)

        assertEquals(2, exclusions.count { it == Exclusion(domain = "database", path = ".") })
        assertEquals(2, exclusions.count { it == Exclusion(domain = "sharedpref", path = SECURE_PREFERENCES_FILE) })
    }

    private fun exclusionsIn(@XmlRes resourceId: Int): List<Exclusion> {
        val parser = context.resources.getXml(resourceId)
        val exclusions = buildList {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                    add(
                        Exclusion(
                            domain = parser.getAttributeValue(null, "domain"),
                            path = parser.getAttributeValue(null, "path"),
                        ),
                    )
                }
                parser.next()
            }
        }
        parser.close()
        return exclusions
    }

    private data class Exclusion(val domain: String, val path: String)

    private companion object {
        const val SECURE_PREFERENCES_FILE = "campus_ai_secure_prefs.xml"
    }
}
