package org.pagano.backup

class NativeC {
    init {
        System.loadLibrary("example-jni-c")
    }

    external fun getString(): String
}
