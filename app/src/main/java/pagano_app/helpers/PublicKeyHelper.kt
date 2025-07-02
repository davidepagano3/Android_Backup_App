/*
 * Copyright © 2021 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup.helpers

import org.pagano.backup.PublicKeyRunner
import org.pagano.backup.output.PublicKeyOutput

class PublicKeyHelper() {
    val runnerClass = PublicKeyRunner::class.java
    val outputClass = PublicKeyOutput::class.java
}
