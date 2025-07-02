/*
 * Copyright © 2021 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.helpers


import org.pagano.backup.DbclientRunner
import org.pagano.backup.config.DbclientConfig
import org.pagano.backup.output.CommandOutput

class DbclientHelper(config: DbclientConfig) {
    val runnerClass = DbclientRunner::class.java
    val inputClass = DbclientConfig::class.java
    val outputClass = CommandOutput::class.java
}
