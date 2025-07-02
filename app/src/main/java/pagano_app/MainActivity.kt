/*
 * Copyright © 2021-2024 Matt Robinson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.pagano.backup

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pagano.backup.config.PrivateKeyConfig
import org.pagano.backup.config.RsyncConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.Calendar
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity(), View.OnClickListener, NetworkMonitor.Callback {
    val context: Context get() = applicationContext
    val TAG = "PAGANO_APP"
    val bunchsize = 500
    val ssh_user = "giuseppe"
    val ssh_server = "XXXXXXXXXXXXX"
    private val testUrl = "https://$ssh_server/alive.html"
    val ssh_known_host = "ssh-ed25519 XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    val remote_backup_folder = "/storage"
    private val apkFileName = "app-release.apk"
    private val LastBackupFileName = "Last_Backup_File.txt"
    private val StartBackupFileName = "Start_Backup_File.txt"
    private var loopcount = 0
    private var todayBackupDone = false
    private var rsyncTaskRunning = true
    private var useNetworkMonitor = false
    private var updateApkRunning = false
    private lateinit var customProgressBar: ProgressBar
    private lateinit var customShowText: TextView
    private lateinit var customButton: Button
    private val STORAGE_PERMISSION_CODE = 1001

    private fun showSettingsDialog() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "0 - Missing storage access")
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
                }
            }
            Log.d(TAG, "1 - Missing storage access")
            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                // User denied with "Don't ask again"
                // Show explanation and prompt to open settings
                showSettingsDialog()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "3 - Missing storage access")
            }
        }
    }

//    private val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission()
//        ) { isGranted: Boolean ->
//            if (isGranted) {
//                // Permission Granted
//            } else {
//                // Permission not granted
//            }
//        }

//    private fun checkPermission(permission: String) =
//        ContextCompat.checkSelfPermission(
//            fragment.requireContext(),
//            permission
//        ) == PackageManager.PERMISSION_GRANTED
//
//    fun requestReadExternalStoragePermission() {
//        when {
//            checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
//                // Permission Granted
//            }
//            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
//                // Show rationale permission dialog and open settings
//            }
//            else -> {
//                requestPermissionLauncher.launch(
//                    Manifest.permission.READ_EXTERNAL_STORAGE
//                )
//            }
//        }
//    }
//
//    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
//        if (isGranted) {
//            buildMediafileList()
//        } else {
//            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
//        }
//    }

    fun generateNewKey() {
        val privKeyFile1 = File("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
        val pubKeyFile1 = File("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX.pub")

        privKeyFile1.delete()
        pubKeyFile1.delete()
        Log.d(TAG, "00 CHIAVI CANCELLATE !!!")

        val config = PrivateKeyConfig()
        Log.d(TAG, "dd EEEE " + context.filesDir)

        val privKeyFile = File(context.filesDir, "id_dropbear")
        privKeyFile.delete()

        val pubKeyFile = File(context.filesDir, "id_dropbear.pub")
        pubKeyFile.delete()

        val result = PrivateKeyRunner().run(context, config)
        if (!result) {
            Log.d(TAG, "ERROR in generating Private Key")
        }

        privKeyFile.copyTo(File(privKeyFile1.toString()))
        pubKeyFile.copyTo(File(pubKeyFile1.toString()))
    }

    fun generateKey(key_private: InputStream, key_public: InputStream) {

        val privKeyFile1 = File(context.filesDir, "id_dropbear")
        val pubKeyFile1 = File(context.filesDir, "id_dropbear.pub")


        privKeyFile1.delete()
        pubKeyFile1.delete()

        key_private.use { input ->
            privKeyFile1.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        key_public.use { input ->
            pubKeyFile1.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        privKeyFile1.setReadOnly()

    }

    private fun buildMediafileList(): Boolean {
        val photos = getPhotosFromGallery()
        if (photos.isNotEmpty()) {
            // Sync photos with cloud or database (Example: just log the photo paths)
            saveUrisToFile(photos)
        } else {
            Toast.makeText(this, "No photos found", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun syncMediaFileList(loopidx: Int): Boolean {
        val rsync_dest = ssh_user + "@" + ssh_server + ":" + remote_backup_folder + "/" + ssh_user
        val config =
            RsyncConfig(
                "-brR --size-only --files-from=$filesDir/photo_uris_" + loopidx + ".txt / $rsync_dest",
                "$ssh_server $ssh_known_host",
                false,
            )
        val output = RsyncRunner(1200000).run(context, config)

        if (output) {
            Log.d(TAG, "SUCCESS in loop $loopidx ")
            return output
        } else {
            Log.d(TAG, "ERROR in loop $loopidx")
            return false
        }
    }

    private fun syncSingleFile(src_uri: String, sync_opt: String): Boolean {
        val rsync_dest = ssh_user + "@" + ssh_server + ":" + remote_backup_folder + "/" + ssh_user
        val config =
            RsyncConfig(
                "$sync_opt $src_uri $rsync_dest",
                "$ssh_server $ssh_known_host",
                false,
            )
        val output = RsyncRunner(35000).run(context, config)
        if (output) {
            Log.d(TAG, "Success: " + output)
            return output
        } else {
            Log.d(TAG, "ERROR: ")
            return false
        }
    }

    private fun getPhotosFromGallery(): List<String> {
        val mediafilePaths = mutableListOf<String>()
        val projections =
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA, //TODO: Use URI instead of this.. see official docs for this field
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE
            )

        val sortBy = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val selectionArgs = null
        val selection = null

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val cursor: Cursor? = context.contentResolver.query(
            collection,
            projections,
            selection,
            selectionArgs,
            sortBy
        )

        cursor?.let {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
            while (it.moveToNext()) {
                val documentsPath = it.getString(columnIndex)
                if (File(documentsPath).isFile()) {
                    mediafilePaths.add(documentsPath)
                }
            }
            it.close()
        }
        return mediafilePaths
    }

    private fun getPhotosFromGalleryUnused(): List<String> {
        val mediafilePaths = mutableListOf<String>()

        val projection = arrayOf(MediaStore.Images.Media.DATA)

        val contentResolver: ContentResolver = contentResolver
        val cursorI: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        cursorI?.let {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (it.moveToNext()) {
                val photoPath = it.getString(columnIndex)
                mediafilePaths.add(photoPath)
            }
            it.close()
        }

        val cursorV: Cursor? = contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        cursorV?.let {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (it.moveToNext()) {
                val videoPath = it.getString(columnIndex)
                mediafilePaths.add(videoPath)
            }
            it.close()
        }

        val cursorA: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        cursorA?.let {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val audioPath = it.getString(columnIndex)
                mediafilePaths.add(audioPath)
            }
            it.close()
        }

        val cursorD: Cursor? = contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        cursorD?.let {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Downloads.DATA)
            while (it.moveToNext()) {
                val documentsPath = it.getString(columnIndex)
                mediafilePaths.add(documentsPath)
            }
            it.close()
        }
        return mediafilePaths
    }

    private fun saveUrisToFile(photoUris: List<String>) {
        // Get the path to the app's private storage directory
        var photocount = 0

        photoUris.forEach { _ ->
            photocount++
        }
        Log.d(TAG, "PHOTOCOUNT = " + photocount)
        loopcount = (photocount - 40)/bunchsize
        try {
            for (loopidx in 0..(loopcount + 2)) {
                photocount = 0
                var countlimitup = 40 + ((loopidx - 1) * bunchsize)
                var countlimitdown = 20 + ((loopidx - 2) * bunchsize)
                if (loopidx < 2 ) {
                    countlimitup = ((loopidx + 1) * 20)
                    countlimitdown = (loopidx * 20)
                }
                val file = File(context.filesDir, "photo_uris_" + loopidx + ".txt")

                file.delete()
                FileOutputStream(file).use { outputStream ->
                    // Write the URIs to the file
                    photoUris.forEach { uri ->
                        if ((photocount >= countlimitdown) && (photocount < countlimitup)) {
                            outputStream.write((uri + "\n").toByteArray())
                        }
                        photocount++
                    }
                }
                Log.d(TAG, "PHOTOCOUNT = " + photocount + "[" + loopcount + "] " + "( " + countlimitdown + " - " + countlimitup + ")")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save URIs to file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndUpdateApk(): Boolean {
        var retVal = false
        if (updateApkRunning) {
            return true
        }
        updateApkRunning = true
        try {
            val apkFile = downloadApk()
            if (apkFile != null) {
                Log.d(TAG, "InstallApk")
                retVal = installApk(apkFile)
            } else {
                Log.d(TAG, "Download failed or no new APK on the server")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "Error in installing app")
            retVal = false
        }
        Log.d(TAG, "installApkTask ended with retcode=" + retVal)
        updateApkRunning = false
        return retVal
    }


    @Throws(IOException::class)
    fun filesAreEqual(a: File, b: File): Boolean {
        if (a.length() != b.length()) {
            return false
        }

        val BLOCK_SIZE = 128
        val aStream: InputStream = FileInputStream(a)
        val bStream: InputStream = FileInputStream(b)
        val aBuffer = ByteArray(BLOCK_SIZE)
        val bBuffer = ByteArray(BLOCK_SIZE)
        do {
            val aByteCount = aStream.read(aBuffer, 0, BLOCK_SIZE)
            bStream.read(bBuffer, 0, BLOCK_SIZE)
            if (!Arrays.equals(aBuffer, bBuffer)) {
                return false
            }
        } while (aByteCount < 0)
        return true
    }

    private fun downloadApk(): File? {
        val apk_rsync_path = "$ssh_user@$ssh_server:$remote_backup_folder/$ssh_user/$apkFileName"
        val apk_local_path = "$filesDir/tmp_" + apkFileName
        File(apk_local_path).delete()
        val config =
            RsyncConfig(
                "$apk_rsync_path $apk_local_path",
                "$ssh_server $ssh_known_host",
                false,
            )
        val output = RsyncRunner(35000).run(context, config)
        if (output) {
            val file1 = File(apk_local_path)
            Log.d(TAG, "Success in downloading APK file: " + output)
            val file2 = File("/sdcard/Download", "new_" + apkFileName)
            if (file2.isFile()) {
                if (filesAreEqual(file1, file2)) {
                    Log.d(TAG, "Local and remote APK are equal!")
                    return null
                }
            }
            file2.delete()
            File(apk_local_path).copyTo(file2)
            Log.d(TAG, "SUCCEDED IN copy to: /sdcard/Download/new_" + apkFileName)
            return file2
        } else {
            Log.d(TAG, "ERROR: Failed in receiving file: " + apk_rsync_path)
            return null
        }
    }

    private fun installApk(file: File): Boolean {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider", // must match what's in Manifest
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Log.d(TAG, "APK update completed")
        return true
    }

    fun startManualRsync(): Boolean {
        val dataCorrente = Calendar.getInstance()
        val year = dataCorrente.get(Calendar.YEAR)
        val month = dataCorrente.get(Calendar.MONTH) + 1 // Mese in Kotlin è 0-based, quindi aggiungi 1
        val day = dataCorrente.get(Calendar.DAY_OF_MONTH)
        val current_date_string = "$day/$month/$year"
        val startBackupFile = File(context.filesDir, StartBackupFileName)
        startBackupFile.delete()
        startBackupFile.writeText(current_date_string)
        return syncSingleFile(startBackupFile.absolutePath, "-b")
    }

    fun rsyncSucced() {
        val dataCorrente = Calendar.getInstance()
        val year = dataCorrente.get(Calendar.YEAR)
        val month = dataCorrente.get(Calendar.MONTH) + 1 // Mese in Kotlin è 0-based, quindi aggiungi 1
        val day = dataCorrente.get(Calendar.DAY_OF_MONTH)
        val current_date_string = "$day/$month/$year"
        customShowText.setText("Last backup:" +  current_date_string)
        val lastBackupFile = File(context.filesDir, LastBackupFileName)
        lastBackupFile.delete()
        lastBackupFile.writeText(current_date_string)
        syncSingleFile(lastBackupFile.absolutePath, "-b")
        Log.d(TAG, "SYNC: " + lastBackupFile.absolutePath)
        rsyncTaskRunning = false
        todayBackupDone = true
    }

    override fun isIdleState(): Boolean {
        if (useNetworkMonitor) {
            return false
        }
        if (rsyncTaskRunning) {
            return false
        } else {
            return true
        }
    }

    override fun autoRsyncTask() {
        if (rsyncTaskRunning) {
            Log.d(TAG, "Backup in progress")
            return
        }

        if (todayBackupDone) {
            Log.d(TAG, "Today's backup already done")
            return
        }

        rsyncTaskRunning = true
//        if (checkAndUpdateApk()) {
//            Log.d(TAG, "APK has been updated just now. It's better to not run any backup on this round")
//            rsyncTaskRunning = false
//            return
//        }

//        var syncResult = false
        buildMediafileList()
        Log.d(TAG, "rsync func called [loopcount = $loopcount ]")
//        loopcount = 10
        for (loopidx in 0..(loopcount + 2)) {
            if (! syncMediaFileList(loopidx)) {
                Log.d(TAG, "Error in syncMediaFileList($loopidx)")
                rsyncTaskRunning = false
                return
            }
        }

        if (!syncSingleFile(context.filesDir.absolutePath, "-brR")) {
            Log.d(TAG, "Error in syncSingleFile($filesDir)")
            rsyncTaskRunning = false
            return
        }

        Log.d(TAG, "Backup done!!")
        rsyncSucced()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nicolo_key_priv = resources.openRawResource(R.raw.nicolo_priv)
        val nicolo_key_pub = resources.openRawResource(R.raw.nicolo_pub)
        val davide_key_priv = resources.openRawResource(R.raw.davide_priv)
        val davide_key_pub = resources.openRawResource(R.raw.davide_pub)
        val barbara_key_priv = resources.openRawResource(R.raw.barbara_priv)
        val barbara_key_pub = resources.openRawResource(R.raw.barbara_pub)
        val giuseppe_key_priv = resources.openRawResource(R.raw.giuseppe_priv)
        val giuseppe_key_pub = resources.openRawResource(R.raw.giuseppe_pub)

        val key_pub = when(ssh_user) {
            "nicolo" -> nicolo_key_pub
            "davide" -> davide_key_pub
            "barbara" -> barbara_key_pub
            "giuseppe" -> giuseppe_key_pub
            else -> giuseppe_key_pub
        }

        val key_priv = when(ssh_user) {
            "nicolo" -> nicolo_key_priv
            "davide" -> davide_key_priv
            "barbara" -> barbara_key_priv
            "giuseppe" -> giuseppe_key_priv
            else -> giuseppe_key_priv
        }

        checkStoragePermission()

        val networkMonitor = NetworkMonitor(this, this) {
            // This callback executes when connected to "DIGOS" SSID
            // Launch your notification or task here
        }
        networkMonitor.startMonitoring()

//        val f = File("$libDir/librsync.so")
//
//        if (f.exists()) {
//            Log.d(TAG, "IL FILE SISTE")
//        }
//
//        val d = File("$libDir/../../../")
//        if (d.exists() ) {
//            Log.d(TAG, "LA DIR ESISTEEEEEEEEEE")
//        }
//
//        d.walk(FileWalkDirection.BOTTOM_UP).forEach {
//            Log.d(TAG, it.toString())
//        }
//        Log.d(TAG, "LA DIR QUI ")


        fixedRateTimer(name="foo", true, 0.toLong(), period = 30 * 60 * 1000) {
            val dataCorrente = Calendar.getInstance()
            val year = dataCorrente.get(Calendar.YEAR)
            val month = dataCorrente.get(Calendar.MONTH) + 1 // Mese in Kotlin è 0-based, quindi aggiungi 1
            val day = dataCorrente.get(Calendar.DAY_OF_MONTH)
            val current_date_string = "Today: $year $month $day"
            val current_date_file = File(context.filesDir, "current_day")
            if (current_date_file.exists()) {
                val date_read = current_date_file.readText()
                if (current_date_string == date_read) {
                    Log.d(TAG, "$current_date_string Nothing to do until tomorrow")
                } else {
                    todayBackupDone = false
                    current_date_file.delete()
                    current_date_file.writeText(current_date_string)
                    Log.d(TAG, "$current_date_string This is a new day!")
                }
            } else {
                current_date_file.writeText(current_date_string)
                Log.d(TAG, "Created new date string file.")
                todayBackupDone = false
            }
        }

        // Request install packages permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.REQUEST_INSTALL_PACKAGES), 1234)
        }

//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
//            Log.d(TAG, "Permission already granted")
//        } else {
//            // Request permission
//            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
//        }
//        generateNewKey()
        Log.d(TAG, "Setting up rsync keys")
        generateKey(key_priv, key_pub)

//        binding = MainLayoutBinding.inflate(layoutInflater)
//        setContentView(binding.root)
        customProgressBar = findViewById<ProgressBar>(R.id.progressBar) // binding.progressBar
        customShowText = findViewById<TextView>(R.id.showText) // binding.showText
        customButton = findViewById<Button>(R.id.syncButton) // binding.syncButton
        customProgressBar.setVisibility(ProgressBar.VISIBLE)
        customProgressBar.setProgress(0)
        val lastBackupFile = File(context.filesDir, LastBackupFileName)
        if (lastBackupFile.isFile()) {
            val lastBackupDate = lastBackupFile.readText()
            Log.d(TAG, "Last backup: " + lastBackupDate)
            customShowText.setText("Last backup: " + lastBackupDate)
        } else {
            customShowText.setText("NO BACKUP")
        }

        customButton.setOnClickListener {
            customButton.setEnabled(false)
            customProgressBar.setProgress(0)
            customShowText.setText("0 %")
            val skipBackupSync = checkAndUpdateApk()
//            val skipBackupSync = false
            if (! skipBackupSync) {
                if (rsyncTaskRunning) {
                    customShowText.setText("BACKUP in PROGRESS")
                } else {
                    rsyncTaskRunning = true
                    if (startManualRsync()) {
                        Log.d(TAG, "BAckuo is possible")
                    } else {
                        Log.d(TAG, "Backup server is unreachable. Exiting.")
                        customShowText.setText("NOT RUNNING")
                        rsyncTaskRunning = false
                        return@setOnClickListener
                    }
                    customShowText.setText("0 %")

                    Log.d(TAG, "Build media file list")
                    buildMediafileList()

                    lifecycleScope.launch {
                        // Background task
                        withContext(Dispatchers.Main) {
                            // Long-running operation
                            var syncResult: Boolean

                            customShowText.setText("0 %")
                            customShowText.setText("1 %")
                            for (loopidx in 0..(loopcount + 2)) {
                                val syncProgress = (loopidx * 100) / (loopcount + 2)
                                customProgressBar.setProgress(syncProgress)
                                customShowText.setText("$syncProgress %")
                                withContext(Dispatchers.IO) {
                                    // Sync alla the Media Files from the phone
                                    syncResult = syncMediaFileList(loopidx)
                                }
                                if (!syncResult) {
                                    customProgressBar.setProgress(0)
                                    customShowText.setText("0 %")
                                    customButton.setEnabled(true)
                                    Log.d(TAG, "ERROR in loop $loopidx")
                                    return@withContext
                                }
                            }

                            // Sync the files used for the media sync
                            if (!syncSingleFile(context.filesDir.absolutePath, "-brR")) {
                                customProgressBar.setProgress(0)
                                customShowText.setText("0 %")
                                customButton.setEnabled(true)
                                Log.d(TAG, "ERROR in syncSingleFile")
                                return@withContext
                            }
                            customProgressBar.setProgress(100)
                            customShowText.setText("100 %")
                            customButton.setEnabled(true)
                            Log.d(TAG, "SUCCESSFUL RSYNC")
                            rsyncSucced()
                        }
                    }
                }
            }
        }
        Log.d(TAG, "App startup completed")
        rsyncTaskRunning = false
    }

    override fun onClick(v: View?) {
    }
}

