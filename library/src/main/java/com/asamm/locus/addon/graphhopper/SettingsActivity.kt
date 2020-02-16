/**
 * Created by menion on 10. 7. 2014.
 * Class is part of Locus project
 */
package com.asamm.locus.addon.graphhopper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import locus.api.utils.Logger
import java.io.File

/**
 * Visible activity with the routing service settings.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // refresh content of page
        refreshPageContent()

        // check also permissions on Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val granted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
            if (granted != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE) {
            refreshPageContent()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Refresh content of layout.
     */
    private fun refreshPageContent() {
        // get directories
        var dirs: List<File>? = null
        try {
            dirs = Utils.getAvailableData(this)
        } catch (e: Exception) {
            Logger.logE(TAG, "refreshPageContent()", e)
        }

        // set empty view
        if (!Utils.existsValidLocus(this)) {
            displayWarningMessage("No valid Locus installation!")
        } else if (dirs?.isNotEmpty() == true) {
            displayDataFiles(dirs)
        } else {
            val sb = StringBuilder()
            sb.append("No content!").append("\n\n")
            sb.append("Put GraphHopper data to ").append(Utils.getRootDirectory(this)).append(" directory")
            displayWarningMessage(sb)
        }
    }

    /**
     * Display warning on a display.
     *
     * @param text text to display
     */
    private fun displayWarningMessage(text: CharSequence) {
        val tv = TextView(this)
        tv.text = text
        tv.setPadding(20, 20, 20, 20)
        setContentView(tv)
    }

    /**
     * Display content with possible routing files.
     *
     * @param dirs found routing directories
     */
    private fun displayDataFiles(dirs: List<File>) {
        setContentView(R.layout.settings_data_activity)
        // find spinner
        val sp = findViewById<View>(R.id.spinner_data) as Spinner

        // prepare data
        var selectedIndex = 0
        val currentItem = Utils.getCurrentRoutingItem(this)
        val data = arrayOfNulls<String>(dirs.size)
        for (i in dirs.indices) {
            data[i] = dirs[i].name
                .replace("-gh", "")
                .replace("_", " ")
                .trim { it <= ' ' }
            if (dirs[i] == currentItem) {
                selectedIndex = i
            }
        }

        // set spinner chooser
        val spinnerArrayAdapter = ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_item, data)
        spinnerArrayAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = spinnerArrayAdapter
        sp.setSelection(selectedIndex)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val newDir = dirs[position]
                Utils.setCurrentRoutingItem(this@SettingsActivity, newDir)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    companion object {

        // tag for logger
        private const val TAG = "SettingsActivity"

        // request code for obtaining permission
        private const val REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE = 1001
    }
}
