package com.corphish.nightlight

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.AsyncTask
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View

import com.corphish.nightlight.data.Constants
import com.corphish.nightlight.engine.Core
import com.corphish.nightlight.engine.KCALManager
import com.corphish.nightlight.helpers.PreferenceHelper
import com.corphish.nightlight.helpers.RootUtils
import com.corphish.nightlight.helpers.CrashlyticsHelper
import kotlinx.android.synthetic.main.activity_splash.*

class StartActivity : AppCompatActivity() {

    /**
     * Declare the shortcut intent strings and id
     */
    private val SHORTCUT_INTENT_STRING_NL_TOGGLE = "android.intent.action.NL_TOGGLE"
    private val SHORTCUT_INTENT_STRING_INTENSITY_TOGGLE = "android.intent.action.INTENSITY_TOGGLE"
    private val SHORTCUT_ID_TOGGLE = "toggle"
    private val SHORTCUT_ID_INTENSITY = "intensity"


    private val TASKER_PLUGIN_INTENT = "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
    private val TASKER_INTENT_RQC = 100

    private var checkBypass = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashlyticsHelper.start(this)
        setContentView(R.layout.activity_splash)

        if (handleIntent())
            finish()
        else if (handleTaskerIntent()) {
        } else {
            if (!PreferenceHelper.getBoolean(this, Constants.COMPATIBILITY_TEST))
                CompatibilityChecker().execute()
            else
                switchToMain()
        }

        splashContainer.setOnClickListener {
            checkBypass--
            if (checkBypass == 0) switchToMain()
        }
    }

    /**
     * Handle the incoming intent of shortcut
     * Returns true if shortcut was handled, false otherwise
     */
    private fun handleIntent(): Boolean {
        val shortcutID: String

        if (intent.action != null) {
            if (intent.action == SHORTCUT_INTENT_STRING_NL_TOGGLE) {
                shortcutID = SHORTCUT_ID_TOGGLE
                doToggle()
            } else if (intent.action == SHORTCUT_INTENT_STRING_INTENSITY_TOGGLE) {
                shortcutID = SHORTCUT_ID_INTENSITY
                doIntensityToggle()
            } else {
                shortcutID = ""
            }
        } else
            return false

        if (shortcutID.isEmpty()) return false


        /*
         * On Android 7.0 or below, bail out from now
         * This is because app shortcuts are not supported by default in those android versions
         * It however is supported in 3rd party launchers like nova launcher.
         * As android API guidelines suggest to reportShortcutUsed(), but that can be done only on API 25
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return true
        else {
            val shortcutManager = this.getSystemService(ShortcutManager::class.java)
            shortcutManager!!.reportShortcutUsed(shortcutID)
        }

        return true
    }

    private fun handleTaskerIntent(): Boolean {
        if (TASKER_PLUGIN_INTENT != intent.action) return false
        // Check if master switch is enabled
        // If enabled redirect to ProfilesActivity
        // Otherwise redirect to MainActivity with appropriate intent message
        // MainActivity will then use this message to show a prompt to user to enable night light
        val masterSwitchEnabled = PreferenceHelper.getBoolean(this, Constants.PREF_MASTER_SWITCH)
        val intent: Intent

        intent = Intent(this, if (masterSwitchEnabled) ProfilesActivity::class.java else MainActivity::class.java)
        intent.putExtra(Constants.TASKER_ERROR_STATUS, !masterSwitchEnabled)
        startActivityForResult(intent, TASKER_INTENT_RQC)

        return true
    }

    /**
     * Actual night light toggling happens here
     */
    private fun doToggle() {
        val state = !PreferenceHelper.getBoolean(this, Constants.PREF_FORCE_SWITCH)
        val masterSwitch = PreferenceHelper.getBoolean(this, Constants.PREF_MASTER_SWITCH)

        /*
         * If state is on, while masterSwitch is off, turn on masterSwitch as well
         */
        if (state && !masterSwitch)
            PreferenceHelper.putBoolean(this, Constants.PREF_MASTER_SWITCH, true)

        Core.applyNightModeAsync(state, this)
    }

    private fun doIntensityToggle() {
        val state = PreferenceHelper.getBoolean(this, Constants.PREF_FORCE_SWITCH)
        val masterSwitch = PreferenceHelper.getBoolean(this, Constants.PREF_MASTER_SWITCH)

        // Toggle only if both are on
        if (state && masterSwitch)
            Core.toggleIntensities(this)
    }

    private fun showAlertDialog(caption: Int, msg: Int) {
        if (isFinishing || isDestroyed) return
        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AppTheme))
        builder.setTitle(caption)
        builder.setMessage(msg)
        builder.setPositiveButton(android.R.string.ok) { _, _ -> finish() }
        builder.show()
    }

    private inner class CompatibilityChecker : AsyncTask<String, String, String>() {
        internal var rootAccessAvailable = false
        internal var kcalSupported = false

        override fun doInBackground(vararg booms: String): String? {
            rootAccessAvailable = RootUtils.rootAccess
            kcalSupported = KCALManager.isKCALAvailable
            return null
        }

        override fun onPostExecute(boom: String?) {
            progressBar.visibility = View.GONE
            if (!rootAccessAvailable) {
                showAlertDialog(R.string.no_root_access, R.string.no_root_desc)
                alertPlaceholder.visibility = View.VISIBLE
            } else if (!kcalSupported) {
                showAlertDialog(R.string.no_kcal, R.string.no_kcal_desc)
                alertPlaceholder.visibility = View.VISIBLE
            } else {
                PreferenceHelper.putBoolean(applicationContext, Constants.COMPATIBILITY_TEST, true)
                switchToMain()
            }
        }
    }

    private fun switchToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != TASKER_INTENT_RQC) return
        setResult(RESULT_OK, data)
        finish()
    }
}
