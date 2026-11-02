package com.metrolist.music.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

object UpdateInstaller {
    @Suppress("DEPRECATION")
    fun validateArchive(context: Context, file: File, expectedVersion: String) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IllegalArgumentException("The downloaded file is not a readable Android APK")
        require(installed.packageName == context.packageName) { "The installed app could not be verified" }
        validatePackage(installed, candidate, expectedVersion)
    }

    @Suppress("DEPRECATION")
    internal fun validatePackage(installed: PackageInfo, candidate: PackageInfo, expectedVersion: String) {
        require(candidate.packageName == installed.packageName) { "The APK belongs to a different app" }
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.longVersionCode else installed.versionCode.toLong()
        val candidateCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) candidate.longVersionCode else candidate.versionCode.toLong()
        require(candidateCode > installedCode) { "The APK is not newer than the installed app" }
        val candidateVersion = candidate.versionName?.substringBefore("+").orEmpty()
        val installedVersion = installed.versionName?.substringBefore("+").orEmpty()
        require(candidateVersion == expectedVersion && Updater.isUpdateAvailable(installedVersion, candidateVersion)) {
            "The APK version does not match the selected update"
        }
        val installedSigners = signers(installed)
        val candidateSigners = signers(candidate)
        require(installedSigners.isNotEmpty() && installedSigners == candidateSigners) {
            "The APK signature does not match this Sourcify installation"
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    fun permissionIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))

    @Suppress("DEPRECATION")
    internal fun installIntent(context: Context, uri: Uri): Intent {
        require(uri.scheme == "content" && uri.authority == "${context.packageName}.FileProvider") {
            "Only a verified private APK can be sent to the installer"
        }
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            clipData = ClipData.newRawUri("Sourcify update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }
}
