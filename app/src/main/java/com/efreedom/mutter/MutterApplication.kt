package com.efreedom.mutter

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** 앱 진입점 — Hilt 의존성 그래프의 루트. (iOS MutterApp/AppDelegate 대응) */
@HiltAndroidApp
class MutterApplication : Application()
