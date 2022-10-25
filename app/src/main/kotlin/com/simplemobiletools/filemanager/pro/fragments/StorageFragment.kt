package com.simplemobiletools.filemanager.pro.fragments

import android.annotation.SuppressLint
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.filemanager.pro.R
import com.simplemobiletools.filemanager.pro.activities.MimeTypesActivity
import com.simplemobiletools.filemanager.pro.activities.SimpleActivity
import com.simplemobiletools.filemanager.pro.extensions.formatSizeThousand
import com.simplemobiletools.filemanager.pro.helpers.*
import kotlinx.android.synthetic.main.storage_fragment.view.*
import java.util.*

class StorageFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {

    companion object {
        private const val SIZE_DIVIDER = 100000
    }

    override fun setupFragment(activity: SimpleActivity) {
        total_space.text = String.format(context.getString(R.string.total_storage), "…")
        getSizes()

        free_space_holder.setOnClickListener {
            try {
                val storageSettingsIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                activity.startActivity(storageSettingsIntent)
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }

        images_holder.setOnClickListener { launchMimetypeActivity(IMAGES) }
        videos_holder.setOnClickListener { launchMimetypeActivity(VIDEOS) }
        audio_holder.setOnClickListener { launchMimetypeActivity(AUDIO) }
        documents_holder.setOnClickListener { launchMimetypeActivity(DOCUMENTS) }
        archives_holder.setOnClickListener { launchMimetypeActivity(ARCHIVES) }
        others_holder.setOnClickListener { launchMimetypeActivity(OTHERS) }
    }

    override fun refreshFragment() {}

    override fun onResume(textColor: Int) {
        getSizes()
        context.updateTextColors(storage_fragment)

        val properPrimaryColor = context.getProperPrimaryColor()
        main_storage_usage_progressbar.setIndicatorColor(properPrimaryColor)
        main_storage_usage_progressbar.trackColor = properPrimaryColor.adjustAlpha(0.3f)
    }

    private fun launchMimetypeActivity(mimetype: String) {
        Intent(context, MimeTypesActivity::class.java).apply {
            putExtra(SHOW_MIMETYPE, mimetype)
            context.startActivity(this)
        }
    }

    private fun getSizes() {
        if (!isOreoPlus()) {
            return
        }

        ensureBackgroundThread {
            try {
                getMainStorageStats(context)
            } catch (_: Exception) {
            }

            val filesSize = getSizesByMimeType()
            val imagesSize = filesSize[IMAGES]!!
            val videosSize = filesSize[VIDEOS]!!
            val audioSize = filesSize[AUDIO]!!
            val documentsSize = filesSize[DOCUMENTS]!!
            val archivesSize = filesSize[ARCHIVES]!!
            val othersSize = filesSize[OTHERS]!!

            post {
                images_size.text = imagesSize.formatSize()
                videos_size.text = videosSize.formatSize()
                audio_size.text = audioSize.formatSize()
                documents_size.text = documentsSize.formatSize()
                archives_size.text = archivesSize.formatSize()
                others_size.text = othersSize.formatSize()
            }
        }
    }

    private fun getSizesByMimeType(): HashMap<String, Long> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )

        var imagesSize = 0L
        var videosSize = 0L
        var audioSize = 0L
        var documentsSize = 0L
        var archivesSize = 0L
        var othersSize = 0L
        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val mimeType = cursor.getStringValue(MediaStore.Files.FileColumns.MIME_TYPE)?.lowercase(Locale.getDefault())
                    val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE)
                    if (mimeType == null) {
                        if (size > 0 && size != 4096L) {
                            val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
                            if (!context.getIsPathDirectory(path)) {
                                othersSize += size
                            }
                        }
                        return@queryCursor
                    }

                    when (mimeType.substringBefore("/")) {
                        "image" -> imagesSize += size
                        "video" -> videosSize += size
                        "audio" -> audioSize += size
                        "text" -> documentsSize += size
                        else -> {
                            when {
                                extraDocumentMimeTypes.contains(mimeType) -> documentsSize += size
                                extraAudioMimeTypes.contains(mimeType) -> audioSize += size
                                archiveMimeTypes.contains(mimeType) -> archivesSize += size
                                else -> othersSize += size
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        val mimeTypeSizes = HashMap<String, Long>().apply {
            put(IMAGES, imagesSize)
            put(VIDEOS, videosSize)
            put(AUDIO, audioSize)
            put(DOCUMENTS, documentsSize)
            put(ARCHIVES, archivesSize)
            put(OTHERS, othersSize)
        }

        return mimeTypeSizes
    }

    @SuppressLint("NewApi")
    private fun getMainStorageStats(context: Context) {
        val externalDirs = context.getExternalFilesDirs(null)
        val storageManager = context.getSystemService(AppCompatActivity.STORAGE_SERVICE) as StorageManager

        externalDirs.forEach { file ->
            val storageVolume = storageManager.getStorageVolume(file) ?: return
            if (storageVolume.isPrimary) {
                // internal storage
                val storageStatsManager = context.getSystemService(AppCompatActivity.STORAGE_STATS_SERVICE) as StorageStatsManager
                val uuid = StorageManager.UUID_DEFAULT
                val totalSpace = storageStatsManager.getTotalBytes(uuid)
                val freeSpace = storageStatsManager.getFreeBytes(uuid)

                post {
                    arrayOf(
                        main_storage_usage_progressbar
                    ).forEach {
                        it.max = (totalSpace / SIZE_DIVIDER).toInt()
                    }

                    main_storage_usage_progressbar.progress = ((totalSpace - freeSpace) / SIZE_DIVIDER).toInt()

                    main_storage_usage_progressbar.beVisible()
                    free_space_value.text = freeSpace.formatSizeThousand()
                    total_space.text = String.format(context.getString(R.string.total_storage), totalSpace.formatSizeThousand())
                    free_space_label.beVisible()
                }
            }
        }
    }
}
