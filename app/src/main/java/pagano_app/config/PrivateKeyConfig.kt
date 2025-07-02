/*
 * Copyright © 2021-2023 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.config

class PrivateKeyConfig
    @JvmOverloads
    constructor(
        var keyType: String = "rsa",
//        var keyType: String = "Ed25519",
        var keySize: Int = 2048,
//        var keySize: Int = 256,
        var overwrite: Boolean = false,
    )
