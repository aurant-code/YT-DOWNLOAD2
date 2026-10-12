package com.example.ytdownload

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform