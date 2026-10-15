package com.kaspercito.data.io

import com.kaspercito.data.db.documentDirectory
import okio.FileSystem

actual fun fileSystem(): FileSystem = FileSystem.SYSTEM
actual fun fileDir(): String = documentDirectory() + "/SimpMusic"