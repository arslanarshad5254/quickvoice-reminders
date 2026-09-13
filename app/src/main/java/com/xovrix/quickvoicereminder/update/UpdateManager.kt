package com.xovrix.quickvoicereminder.update

import android.app.Activity
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.installStatus
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed

class UpdateManager(private val activity: Activity) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val updateType = AppUpdateType.FLEXIBLE

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus == InstallStatus.DOWNLOADED) {
            showUpdateFinishedToast()
        }
    }

    fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            val isUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val isFlexibleAllowed = info.isFlexibleUpdateAllowed

            if (isUpdateAvailable && isFlexibleAllowed) {
                appUpdateManager.registerListener(installStateUpdatedListener)
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    activity,
                    AppUpdateOptions.newBuilder(updateType).build(),
                    UPDATE_REQUEST_CODE,
                )
            }
        }
    }

    fun onResume() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus == InstallStatus.DOWNLOADED) {
                showUpdateFinishedToast()
            }
        }
    }

    fun onDestroy() {
        try {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        } catch (_: Exception) {
        }
    }

    private fun showUpdateFinishedToast() {
        Toast.makeText(
            activity,
            "Update downloaded. Restarting app to install...",
            Toast.LENGTH_LONG
        ).show()
        
        // Complete update after a short delay
        activity.window.decorView.postDelayed(
            {
                appUpdateManager.completeUpdate()
            },
            2000,
        )
    }

    companion object {
        const val UPDATE_REQUEST_CODE = 5566
    }
}
