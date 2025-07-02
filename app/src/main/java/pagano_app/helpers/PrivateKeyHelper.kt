/*
 * Copyright © 2021 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.helpers

import org.pagano.backup.PrivateKeyRunner
import org.pagano.backup.config.PrivateKeyConfig

class PrivateKeyHelper(config: PrivateKeyConfig) {
    val runnerClass = PrivateKeyRunner::class.java
    val inputClass = PrivateKeyConfig::class.java
}
