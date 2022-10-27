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
    private val SIZE_DIVIDER = 100000

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

        val redColor = context.resources.getColor(R.color.md_red_700)
        images_progressbar.setIndicatorColor(redColor)
        images_progressbar.trackColor = redColor.adjustAlpha(0.3f)

        val greenColor = context.resources.getColor(R.color.md_green_700)
        videos_progressbar.setIndicatorColor(greenColor)
        videos_progressbar.trackColor = greenColor.adjustAlpha(0.3f)

        val lightBlueColor = context.resources.getColor(R.color.md_light_blue_700)
        audio_progressbar.setIndicatorColor(lightBlueColor)
        audio_progressbar.trackColor = lightBlueColor.adjustAlpha(0.3f)

        val yellowColor = context.resources.getColor(R.color.md_yellow_700)
        documents_progressbar.setIndicatorColor(yellowColor)
        documents_progressbar.trackColor = yellowColor.adjustAlpha(0.3f)

        val tealColor = context.resources.getColor(R.color.md_teal_700)
        archives_progressbar.setIndicatorColor(tealColor)
        archives_progressbar.trackColor = tealColor.adjustAlpha(0.3f)

        val pinkColor = context.resources.getColor(R.color.md_pink_700)
        others_progressbar.setIndicatorColor(pinkColor)
        others_progressbar.trackColor = pinkColor.adjustAlpha(0.3f)
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
            getMainStorageStats(context)

            val filesSize = getSizesByMimeType()
            val imagesSize = filesSize[IMAGES]!!
            val videosSize = filesSize[VIDEOS]!!
            val audioSize = filesSize[AUDIO]!!
            val documentsSize = filesSize[DOCUMENTS]!!
            val archivesSize = filesSize[ARCHIVES]!!
            val othersSize = filesSize[OTHERS]!!

            post {
                images_size.text = imagesSize.formatSize()
                images_progressbar.progress = (imagesSize / SIZE_DIVIDER).toInt()

                videos_size.text = videosSize.formatSize()
                videos_progressbar.progress = (videosSize / SIZE_DIVIDER).toInt()

                audio_size.text = audioSize.formatSize()
                audio_progressbar.progress = (audioSize / SIZE_DIVIDER).toInt()

                documents_size.text = documentsSize.formatSize()
                documents_progressbar.progress = (documentsSize / SIZE_DIVIDER).toInt()

                archives_size.text = archivesSize.formatSize()
                archives_progressbar.progress = (archivesSize / SIZE_DIVIDER).toInt()

                others_size.text = othersSize.formatSize()
                others_progressbar.progress = (othersSize / SIZE_DIVIDER).toInt()
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
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
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
                        main_storage_usage_progressbar, images_progressbar, videos_progressbar, audio_progressbar, documents_progressbar,
                        archives_progressbar, others_progressbar
                    ).forEach {
                        it.max = (totalSpace / SIZE_DIVIDER).toInt()
                    }

                    main_storage_usage_progressbar.progress = ((totalSpace - freeSpace) / SIZE_DIVIDER).toInt()

                    main_storage_usage_progressbar.beVisible()
                    free_space_value.text = freeSpace.formatSizeThousand()
                    total_space.text = String.format(context.getString(R.string.total_storage), totalSpace.formatSizeThousand())
                    free_space_label.beVisible()
                }
            } else {
                // sd card
                val totalSpace = file.totalSpace
                val freeSpace = file.freeSpace
            }
        }
    }
}
