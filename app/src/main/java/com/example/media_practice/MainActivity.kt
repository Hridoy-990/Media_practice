package com.example.media_practice

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.media_practice.databinding.ActivityMainBinding
import com.example.media_practice.service.EncodingService
import java.io.File

/**
 * @author Md Jahirul Islam Hridoy
 * Created on 13,March,2022
 */
class MainActivity : AppCompatActivity() {

    //view binding
    private lateinit var binding: ActivityMainBinding

    private var selectedImgUris = ArrayList<Uri>()
    private var selectedAudioUri: Uri? = null
    private var selectedVideoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            selectedImgUris = savedInstanceState.getParcelableArrayList("selectedImgUris")!!
            selectedAudioUri = savedInstanceState.getParcelable("selectedAudioUri")
            selectedVideoUri = savedInstanceState.getParcelable("selectedVideoUri")
        }

        initView()

    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState?.putParcelableArrayList("selectedImgUris", selectedImgUris)
        outState?.putParcelable("selectedAudioUri", selectedAudioUri)
        outState?.putParcelable("selectedVideoUri", selectedVideoUri)
    }

    private fun initView() {
        binding.ivPreview.setOnClickListener {
            playPreview()
        }

        binding.butAddImages.setOnClickListener {

            if (needsStoragePermission(this@MainActivity)) {
                requestStoragePermission(this@MainActivity, CODE_IMAGE_SEARCH)
            }
            else {
                performImagesSearch(this@MainActivity, CODE_IMAGE_SEARCH)
            }
        }

        binding.butAddAudio.setOnClickListener {
            if (needsStoragePermission(this@MainActivity)) {
                requestStoragePermission(this@MainActivity, CODE_AUDIO_SEARCH)
            }
            else {
                performAudioSearch(this@MainActivity, CODE_AUDIO_SEARCH)
            }
        }

        binding.butClearImages.setOnClickListener {
            selectedImgUris.clear()
            binding.butCreateTimeLapse.visibility = View.INVISIBLE
            binding.butClearImages.visibility = View.INVISIBLE
            binding.tvSelectedCount.text = ""
        }

        binding.butClearImages.visibility = if (selectedImgUris.isNotEmpty()) View.VISIBLE else View.INVISIBLE

        binding.butClearAudio.setOnClickListener {
            binding.butClearAudio.visibility = View.INVISIBLE
            binding.tvAudioFile.text = ""
        }

        binding.butClearAudio.visibility = if (selectedAudioUri != null) View.VISIBLE else View.INVISIBLE

        binding.butAddVideo.setOnClickListener {
            if (needsStoragePermission(this@MainActivity)) {
                requestStoragePermission(this@MainActivity, CODE_VIDEO_SEARCH)
            }
            else {
                performVideoSearch(this@MainActivity, CODE_VIDEO_SEARCH)
            }
        }

        binding.butClearVideo.setOnClickListener {
            binding.butCreateTimeLapse.visibility = View.INVISIBLE
            binding.butClearVideo.visibility = View.INVISIBLE
            binding.tvVideoFile.text = ""
        }

        binding.butClearVideo.visibility = if (selectedVideoUri != null) View.VISIBLE else View.INVISIBLE

        binding.butCreateTimeLapse.setOnClickListener {
            //requestEncodeImages()
            requestConvertVideo()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)


        if (requestCode == CODE_IMAGE_SEARCH && resultCode == Activity.RESULT_OK) {
            addImages(data!!)
        } else if (requestCode == CODE_AUDIO_SEARCH && resultCode == Activity.RESULT_OK) {
            addAudio(data!!)
        } else if (requestCode == CODE_VIDEO_SEARCH && resultCode == Activity.RESULT_OK) {
            addVideo(data!!)
        }

        else if (requestCode == CODE_ENCODING_FINISHED) {
            binding.progressEncoding.visibility = View.INVISIBLE
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission: " + permissions[0] + "was " + grantResults[0])
            Toast.makeText(this, getString(R.string.warn_no_storage_permission), Toast.LENGTH_LONG)
                .show()
        } else {
            when (requestCode) {
                CODE_IMAGE_SEARCH -> {
                    performImagesSearch(
                        this@MainActivity, CODE_IMAGE_SEARCH)
                }
                CODE_AUDIO_SEARCH -> {
                    performAudioSearch(
                        this@MainActivity, CODE_AUDIO_SEARCH)
                }
                CODE_VIDEO_SEARCH -> {
                    performVideoSearch(
                        this@MainActivity, CODE_VIDEO_SEARCH)
                }
            }
        }
    }




    override fun onResume() {
        super.onResume()

        configureUi()
    }

    private fun configureUi() {
        if (isServiceRunning(this, EncodingService::class.java))
            binding.progressEncoding.visibility = View.VISIBLE
        else
            binding.progressEncoding.visibility = View.INVISIBLE

        val visibility = if (selectedImgUris.size > 0) View.VISIBLE else View.INVISIBLE
        binding.butClearImages.visibility = visibility
        //binding.butCreateTimeLapse.visibility = visibility

        if (selectedAudioUri != null){
            binding.butClearAudio.visibility = View.VISIBLE
            binding.tvAudioFile.text = selectedAudioUri!!.lastPathSegment
        }

        if (selectedVideoUri != null){
            binding.butClearVideo.visibility = View.VISIBLE
            binding.butCreateTimeLapse.visibility = View.VISIBLE
            binding.tvVideoFile.text = getName(this, selectedVideoUri!!) ?: ""
        }




        binding.tvSelectedCount.text = selectedImgUris.size.toString() + " images selected"

        val outFile = File(getOutputPath())
        if (outFile.exists()) {
            val thumb = ThumbnailUtils.createVideoThumbnail(outFile.absolutePath,
                MediaStore.Images.Thumbnails.FULL_SCREEN_KIND)
            binding.ivPreview.setImageBitmap(thumb)
        }
    }

    private fun requestEncodeImages() {
        if (selectedImgUris.size > 0) {
            val intent = Intent(this, EncodingService::class.java).apply {
                action = EncodingService.ACTION_ENCODE_IMAGES

                putExtra(EncodingService.KEY_OUT_PATH, getOutputPath())
                putExtra(EncodingService.KEY_IMAGES, selectedImgUris)

                if (selectedAudioUri != null) putExtra(EncodingService.KEY_AUDIO, selectedAudioUri)

                // We want this Activity to get notified once the encoding has finished
                val pi = createPendingResult(CODE_ENCODING_FINISHED, intent, 0)
                putExtra(EncodingService.KEY_RESULT_INTENT, pi)
            }

            startService(intent)

            binding.progressEncoding.visibility = View.VISIBLE
        } else {
            Toast.makeText(this@MainActivity, getString(R.string.err_one_file), Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun requestConvertVideo() {
        if (selectedVideoUri != null) {
            val intent = Intent(this, EncodingService::class.java).apply {

                action = EncodingService.ACTION_ENCODE_VIDEOS

                putExtra(EncodingService.KEY_OUT_PATH, getOutputPath())
                putExtra(EncodingService.KEY_INPUT_VID_URI, selectedVideoUri)

                // We want this Activity to get notified once the encoding has finished
                val pi = createPendingResult(CODE_ENCODING_FINISHED, intent, 0)
                putExtra(EncodingService.KEY_RESULT_INTENT, pi)
            }

            startService(intent)

            binding.progressEncoding.visibility = View.VISIBLE
        } else {
            Toast.makeText(this@MainActivity, getString(R.string.err_no_input_file),
                Toast.LENGTH_LONG).show()
        }
    }

    private fun playPreview() {
        val outFile = File(getOutputPath())
        if (outFile.exists()) {
            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) FileProvider.getUriForFile(this, "$packageName.provider", outFile)
                else Uri.parse(outFile.absolutePath)

            val intent = Intent(Intent.ACTION_VIEW, uri)
                .setDataAndType(uri,"video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setDataAndType(uri, "video/mp4")

            startActivityForResult(intent, CODE_THUMB)
            Toast.makeText(this, "starting", Toast.LENGTH_LONG).show()

        } else {
            Toast.makeText(this, getString(R.string.app_name), Toast.LENGTH_LONG).show()
        }
    }

    private fun addImages(data: Intent) {

        if (data != null) {
            if (data.data != null) {
                selectedImgUris.add(data.data!!)
            } else if (data.clipData != null) {
                val cd = data.clipData
                val unsorted = ArrayList<Uri>()
                for (i in 0 until cd!!.itemCount) {
                    val item = cd.getItemAt(i)
                    unsorted.add(item.uri)
                }

                // System file picker doesn't guarantee any kind of order when
                // Multiple files are selected, so I at least sort by path/filename
                selectedImgUris.addAll(unsorted.sortedBy { it.encodedPath })
            }
        }

        binding.tvSelectedCount.text = selectedImgUris.size.toString() + " images selected"

        if (selectedImgUris.size > 0) {
            binding.butCreateTimeLapse.visibility = View.VISIBLE
            binding.butClearImages.visibility = View.VISIBLE
        }

        if (selectedVideoUri != null) {
            binding.butCreateTimeLapse.visibility = View.VISIBLE
            binding.butClearVideo.visibility = View.VISIBLE
        }
    }

    private fun addAudio(data: Intent) {
        if (data != null) {
            if (data.data != null) {
                selectedAudioUri = data.data
                binding.tvAudioFile.text = selectedAudioUri!!.lastPathSegment
                binding.butClearAudio.visibility = View.VISIBLE
            }
        }
    }

    private fun addVideo(data: Intent) {
        if (data != null) {
            if (data.data != null) {
                selectedVideoUri = data.data
                binding.tvVideoFile.text = getName(this, selectedVideoUri!!) ?: ""
                binding.butClearVideo.visibility = View.VISIBLE
            }
        }

        if (selectedVideoUri != null) {
            binding.butCreateTimeLapse.visibility = View.VISIBLE
            binding.butClearVideo.visibility = View.VISIBLE
        }
    }

    private fun getOutputPath(): String {
        return cacheDir.absolutePath + "/" + OUT_FILE_NAME
    }

    companion object {
        const val TAG = "MainActivity"

        const val CODE_IMAGE_SEARCH = 1110
        const val CODE_AUDIO_SEARCH = 1111
        const val CODE_VIDEO_SEARCH = 1112
        const val CODE_ENCODING_FINISHED = 1113
        const val CODE_THUMB = 1114

        const val OUT_FILE_NAME = "out.mp4"
    }

}