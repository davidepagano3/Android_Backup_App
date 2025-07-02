/*
 * Copyright © 2021-2024 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup

import android.content.Context
import android.util.Log
import org.pagano.backup.config.PrivateKeyConfig
import java.io.File

class PrivateKeyRunner {
    val TAG = "PAGANO_APP"

    fun run(
        context: Context,
        input: PrivateKeyConfig,
    ): Boolean {
        val libDir = context.applicationInfo.nativeLibraryDir
        val privateKey = Utils.privateKeyFile(context)

        if (privateKey.exists()) {
            if (input.overwrite) {
                privateKey.delete()
            } else {
                return false
            }
        }
        Log.d(TAG, "About to run dropbearkey")

        val dropbearkey =
            Runtime.getRuntime().exec(
                arrayOf(
                    "$libDir/libdropbearkey.so",
                    "-t",
                    input.keyType.lowercase(),
                    "-s",
                    input.keySize.toString(),
                    "-f",
                    privateKey.absolutePath,
                ),
            )

        val retcode = dropbearkey.waitFor()
        Log.d(TAG, "Completed, exit code $retcode")


        Log.d(TAG, privateKey.toString())
        Log.d(TAG, File(context.filesDir, "${Utils.KEY_FILENAME}.pub").toString())
//        File("/sdcard/Download/BARBARA").delete()
//        File("/sdcard/Download/BARBARA.pub").delete()
//        File(context.filesDir, "${Utils.KEY_FILENAME}.pub").copyTo(File("/sdcard/Download/BARBARA.pub"))
//        File(context.filesDir, "${Utils.KEY_FILENAME}").copyTo(File("/sdcard/Download/BARBARA"))
//        privateKey.delete()
//        File(context.filesDir, "${Utils.KEY_FILENAME}.pub").delete()

        return if (retcode == 0) {
            true
        } else {
            false
        }
    }
}
