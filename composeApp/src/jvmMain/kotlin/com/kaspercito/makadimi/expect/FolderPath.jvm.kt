package com.kaspercito.makadimi.expect

actual fun getDownloadFolderPath(): String = System.getProperty("user.home") + "/Downloads"