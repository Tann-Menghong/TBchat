package com.tannmenghong.tbchat.inference

/** Calls must be serialized on one background dispatcher. Native code never accesses the network. */
class LocalChat {
    init { System.loadLibrary("tbchat") }
    external fun open(path: String)
    external fun begin(prompt: ByteArray, temperature: Float)
    external fun next(): ByteArray?
    external fun close()
}
