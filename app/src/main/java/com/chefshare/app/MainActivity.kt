package com.chefshare.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chefshare.app.data.repository.AuthRepository
import com.chefshare.app.ui.auth.LoginActivity
import com.chefshare.app.ui.feed.FeedActivity

/**
 * Launcher entry point and auth router.
 *
 * Shows the system splash, checks whether a Firebase session already exists, then routes to the
 * Feed (signed in) or Login (signed out) and finishes — so this router never sits in the back stack.
 * The auth read is wrapped so a placeholder Firebase config can't crash the app at launch.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate so it paints on the first frame.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val destination =
            if (isUserSignedInSafely()) FeedActivity::class.java else LoginActivity::class.java

        startActivity(Intent(this, destination))
        finish()
    }

    /** True if a Firebase user session already exists; false on any error/misconfiguration. */
    private fun isUserSignedInSafely(): Boolean = try {
        AuthRepository().isLoggedIn()
    } catch (e: Exception) {
        false
    }
}
