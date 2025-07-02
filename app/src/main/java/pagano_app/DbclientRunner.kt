/*
 * Copyright © 2021-2024 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup

import android.content.Context
import android.util.Log
import org.pagano.backup.config.DbclientConfig

class DbclientRunner(private val timeoutOverride: Int? = null) {

    val TAG = "PAGANO_APP"

    fun run(
        context: Context,
        input: DbclientConfig,
    ): Boolean {

        val libDir = context.applicationInfo.nativeLibraryDir

        if (!Utils.privateKeyFile(context).exists()) {
            Log.d(TAG, "ERROR_NO_PRIVATE_KEY")
            return false
        }

        Log.d(TAG, "About to run dbclient")

        val args = ArrayList<String>()
        args.add("$libDir/libdbclient.so")
        args.addAll(ArgumentParser.parse(input.args))

        val builder = ProcessBuilder(args)
        val timeoutMS = timeoutOverride ?: 3500

        ProcessEnv(context, builder, input.knownHosts).use {
            val handler = ProcessHandler(context, builder, timeoutMS)
            val result = handler.run()

            if (result == 0) {
                Log.d(TAG, "ERROR_NO_PRIVATE_KEY")
                return true
            }
            Log.d(TAG, handler.stderr.toString())
            return false
        }
    }
}
