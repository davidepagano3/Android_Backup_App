/*
 * Copyright © 2021 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.helpers


import org.pagano.backup.RsyncRunner
import org.pagano.backup.config.RsyncConfig
import org.pagano.backup.output.CommandOutput

class RsyncHelper(config: RsyncConfig) {
    val runnerClass = RsyncRunner::class.java
    val inputClass = RsyncConfig::class.java
    val outputClass = CommandOutput::class.java
}
