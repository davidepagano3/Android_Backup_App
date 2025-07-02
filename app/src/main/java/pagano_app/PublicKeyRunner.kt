/*
 * Copyright © 2021-2024 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup

import android.content.Context
import android.util.Log
import java.util.Scanner

class PublicKeyRunner {

    val TAG = "PAGANO_APP"

    fun run(
        context: Context,
        input: Unit,
    ): Boolean {
        val libDir = context.applicationInfo.nativeLibraryDir
        val privateKey = Utils.privateKeyFile(context)

        if (!privateKey.exists()) {
            Log.d(TAG, "Utils.ERROR_NO_PRIVATE_KEY")
            return false
        }

        Log.d(TAG, "About to run dropbearkey")

        val dropbearkey =
            Runtime.getRuntime().exec(
                arrayOf(
                    "$libDir/libdropbearkey.so",
                    "-f",
                    privateKey.absolutePath,
                    "-y",
                ),
            )

        val retcode = dropbearkey.waitFor()
        Log.d(TAG, "Completed, exit code $retcode")

        if (retcode != 0) {
            Log.d(TAG, "ERROR")
            return false
        }

        val scanner = Scanner(dropbearkey.inputStream)
        val pubkey = scanner.findWithinHorizon("(?<=\n)ssh-[a-z0-9]+ [a-zA-Z0-9+/]+={0,2} ", 0)

        if (pubkey != null) {
            return true
        }

        throw RuntimeException("Unable to find public key in dropbearkey output")
    }
}
