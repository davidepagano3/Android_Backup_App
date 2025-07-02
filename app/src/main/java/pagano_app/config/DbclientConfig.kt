/*
 * Copyright © 2021-2024 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.config

class DbclientConfig
    @JvmOverloads
    constructor(
        var args: String? = "user@example.com true",
        var knownHosts: String? = "example.com ssh-rsa ABCD1234...=",
        var checkForUpdates: Boolean? = null,
    )
