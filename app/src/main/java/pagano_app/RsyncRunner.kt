/*
 * Copyright © 2021-2025 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.util.Log
import org.pagano.backup.config.RsyncConfig
import org.pagano.backup.output.CommandOutput
import java.io.File

class RsyncRunner(private val timeoutOverride: Int? = null) {

    val TAG = "PAGANO_APP_RSYNC"

    private fun checkForMissingPermission(
        context: Context,
        args: RsyncArgExtractor,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (context.checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }
        } else {
            if (Environment.isExternalStorageManager()) {
                return true
            }
        }

        val externalPaths = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager =
                context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

            externalPaths +=
                manager.storageVolumes.mapNotNull {
                    it.directory?.absolutePath
                }
        } else {
            externalPaths += Environment.getExternalStorageDirectory().absolutePath
        }

        // If the legacy /sdcard symlink points to an external storage
        // location add it to the list of prefixes to check too
        @SuppressLint("SdCardPath")
        if (externalPaths.contains(File("/sdcard").canonicalPath)) {
            externalPaths += "/sdcard"
        }

        args.paths.forEach { path ->
            if (externalPaths.any { path.startsWith(it) }) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.d(TAG, "ERROR_MISSING_STORAGE_PERMISSION")
                    return false
                } else {
                    Log.d(TAG, " R.string.missing_legacy_storage_permission ERROR_MISSING_STORAGE_PERMISSION")
                    return false
                }
            }
        }

        return true
    }

    fun run(
        context: Context,
        input: RsyncConfig,
    ): Boolean {

        val libDir = context.applicationInfo.nativeLibraryDir
        val parsedArgs = ArgumentParser.parse(input.args)

        val argExtractor = RsyncArgExtractor(parsedArgs)

        if (argExtractor.remoteSrcOrDest &&
            !Utils.privateKeyFile(context).exists()
        ) {
            Log.d(TAG, "Utils.ERROR_NO_PRIVATE_KEY")
            return false
        }

        Log.d(TAG, "About to run rsync")

        val args = ArrayList<String>()
        args.add("$libDir/librsync.so")

        args.addAll(parsedArgs)

        val builder = ProcessBuilder(args)
        val timeoutMS = timeoutOverride ?: 35000

        ProcessEnv(context, builder, input.knownHosts).use {
            val handler = ProcessHandler(context, builder, timeoutMS)
            val result = handler.run()
            Log.d(TAG, handler.stderr.toString())
            Log.d(TAG, handler.stdout.toString())
            if (result == 0) {
                return true
            }
            Log.d(TAG, handler.stderr.toString())
            Log.d(TAG, handler.stdout.toString())
            return false
        }
    }
}
