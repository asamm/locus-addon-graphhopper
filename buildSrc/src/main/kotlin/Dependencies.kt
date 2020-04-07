@file:Suppress("unused", "SpellCheckingInspection")

/****************************************************************************
 *
 * Created by menion on 16.02.2020.
 * Copyright (c) 2020. All rights reserved.
 *
 * This file is part of the Asamm team software.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 ***************************************************************************/

//*****************************************************
// PROJECT
//*****************************************************

object Modules {
    const val app = ":app"
    const val library = ":library"
}

object App {
    const val code = 19
    const val name = "0.10"
}

//*****************************************************
// VERSIONS
//*****************************************************

/**
 * Core versions needed by build system or global project setup.
 */
object Versions {

    // BUILD

    // https://developer.android.com/studio/releases/gradle-plugin.html
    const val gradle = "3.5.2"
    // https://developer.android.com/studio/releases/build-tools.html
    const val buildTools = "29.0.2"

    // ANDROID

    const val compileSdk = 29
    const val minSdk = 21
    const val targetSdk = 29

    // KOTLIN

    const val kotlin = "1.3.61"
}

/**
 * Internal libraries.
 */
private object VersionsApi {
    // Locus API
    const val locusApi = "0.9.8"
}

/**
 * Versions for AndroidX dependencies
 */
private object VersionsAndroidX {
    const val appCompat = "1.1.0"
}

//*****************************************************
// LIBRARIES
//*****************************************************

object Build {
    const val androidGradle = "com.android.tools.build:gradle:${Versions.gradle}"
    const val kotlin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
}

object Libraries {
    const val kotlin = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
}

object LibrariesApi {
    const val locusApiAndroid = "com.asamm:locus-api-android:${VersionsApi.locusApi}"
}

object LibrariesAndroidX {
    const val appCompat = "androidx.appcompat:appcompat:${VersionsAndroidX.appCompat}"
}
