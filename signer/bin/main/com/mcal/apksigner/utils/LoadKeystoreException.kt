package com.mcal.apksigner.utils

import java.io.IOException


class LoadKeystoreException : IOException {
    constructor()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}
