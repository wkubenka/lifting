package com.astute.body.domain

import javax.inject.Inject
import javax.inject.Singleton

interface AppClock {
    fun now(): Long
}

@Singleton
class SystemClock @Inject constructor() : AppClock {
    override fun now(): Long = System.currentTimeMillis()
}
