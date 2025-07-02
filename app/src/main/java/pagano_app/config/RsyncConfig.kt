/*
 * Copyright © 2021-2024 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.config

class RsyncConfig
    @JvmOverloads
    constructor(
        var args: String? = "-rv /source/ user@example.com:dest/",
        var knownHosts: String? = "example.com ssh-rsa ABCD1234...=",
        var checkForUpdates: Boolean? = null,
    )
