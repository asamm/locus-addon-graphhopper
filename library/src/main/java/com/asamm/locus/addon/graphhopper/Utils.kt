/**
 * Created by menion on 29. 7. 2014.
 * Class is part of Locus project
 */
package com.asamm.locus.addon.graphhopper

import android.content.Context
import android.preference.PreferenceManager
import locus.api.android.ActionBasics
import locus.api.android.objects.VersionCode
import locus.api.android.utils.LocusUtils
import locus.api.android.utils.exceptions.RequiredVersionMissingException
import locus.api.utils.Logger
import java.io.File
import java.util.*

/**
 * Helpers methods for routing system.
 */
object Utils {

    // tag for logger
    private const val TAG = "Utils"

    // path to currently selected item
    private const val KEY_S_DATA_ITEM_PATH = "KEY_S_DATA_ITEM_PATH"

    /**
     * Check if in system is installed valid Locus version.
     *
     * @param ctx current context
     * @return `true` if Locus is available
     */
    internal fun existsValidLocus(ctx: Context): Boolean {
        return LocusUtils.isLocusAvailable(ctx, VersionCode.UPDATE_11)
    }

    // SOURCE FOR DATA

    /**
     * Get valid directory with GraphHopped routing items.
     *
     * @param ctx current context
     * @return File as root GH directory or null if not defined or any problem happen
     */
    internal fun getRootDirectory(ctx: Context): File? {
        // check Locus version
        if (!existsValidLocus(ctx)) {
            Logger.logW(TAG, "getAvailableData(" + ctx + "), " +
                    "Valid Locus version not available")
            return null
        }

        try {
            // get active Locus version
            val lv = LocusUtils.getActiveVersion(ctx)

            // get root directory
            ActionBasics.getLocusInfo(ctx, lv!!)
                    ?.takeIf { it.rootDir.isNotBlank() }
                    ?.let {
                        return File(it.rootDirMapsVector)
                    }

            // handle invalid state
            Logger.logW(TAG, "getAvailableData(" + ctx + "), " +
                    "Locus ROOT directory do not exists")
            return null
        } catch (e: RequiredVersionMissingException) {
            Logger.logE(TAG, "getRootDirectory($ctx)", e)
            return null
        }
    }

    /**
     * Get all available data from root defined directory. This directory is currently hard-coded
     * to Locus/mapsVector directory.
     * @return list of all available routing items
     * @throws locus.api.android.utils.exceptions.RequiredVersionMissingException
     */
    @Throws(RequiredVersionMissingException::class)
    internal fun getAvailableData(ctx: Context): List<File> {
        // container for data
        val res = ArrayList<File>()

        // get data directory
        val fileRoot = getRootDirectory(ctx)
                ?: return res
        getAvailableData(fileRoot, res)

        // return directories
        return res
    }

    /**
     * Get full list of all available routing items.
     *
     * @param item item (directory/file) we wants to test
     * @param container container for files
     */
    private fun getAvailableData(item: File?, container: MutableList<File>) {
        // load directory content
        if (item?.isDirectory == true) {
            // check GraphHopper directory
            if (item.name.endsWith("-gh")) {
                container.add(item)
                return
            }

            // get content of directory
            item.listFiles()?.forEach {
                getAvailableData(it, container)
            }
        }
    }

    // ACTIVE ROUTING ITEM (DIR/FILE)

    /**
     * Get current stored routing item (dir/file).
     * @param ctx current context
     * @return defined path to routing item
     */
    internal fun getCurrentRoutingItem(ctx: Context): File? {
        // get and check dir
        val dir = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(KEY_S_DATA_ITEM_PATH, "") ?: ""
        if (dir.isEmpty()) {
            return null
        }

        // return valid directory
        return File(dir).takeIf { it.exists() }
    }

    /**
     * Set selected routing item that should be used by this add-on.
     * @param ctx current context
     * @param file selected item
     */
    internal fun setCurrentRoutingItem(ctx: Context, file: File) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(KEY_S_DATA_ITEM_PATH, file.absolutePath)
                .apply()
    }
}
