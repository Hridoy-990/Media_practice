package com.example.media_practice.media

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import java.security.InvalidParameterException

/**
 * @author Md Jahirul Islam Hridoy
 * Created on 13,March,2022
 */

/**
 * This class muxes audio and video tracks from 2 separate files into one video file. The muxer expects audio and
 * video tracks to be already in compressed format that is compatible with the final output video file format (in this
 * example I'm using MPEG4).
 *
 * If you need to perform format conversion before muxing, check out the AudioTrackToAacConvertor.
 */
class AudioToVideoMuxer {
    var videoExtractor: MediaExtractor? = null
    var audioExtractor: MediaExtractor? = null
    var muxer: MediaMuxer? = null
    val maxChunkSize = 1024 * 1024

    var videoIndex = -1
    var audioIndex = -1

    val buffer = ByteBuffer.allocate(maxChunkSize)
    val bufferInfo = MediaCodec.BufferInfo()

    fun mux(audioFile: String, videoFile: String, outFile: String) {
        try {
            init(audioFile, videoFile, outFile)

            muxVideo()

            muxAudio()
        } finally {
            cleanup()
        }
    }

    private fun init(audioFile: String, videoFile: String, outFile: String) {
        // Init extractors which will get encoded frames
        videoExtractor = MediaExtractor()
        videoExtractor!!.setDataSource(videoFile)
        val videoFormat = selectVideoTrack(videoExtractor!!)

        audioExtractor = MediaExtractor()
        audioExtractor!!.setDataSource(audioFile)
        val audioFormat = selectAudioTrack(audioExtractor!!)

        // Init muxer
        muxer = MediaMuxer(outFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        videoIndex = muxer!!.addTrack(videoFormat)
        audioIndex = muxer!!.addTrack(audioFormat)
        muxer!!.start()
    }

    private fun selectAudioTrack(extractor: MediaExtractor): MediaFormat {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                extractor.selectTrack(i)
                return format
            }
        }

        throw InvalidParameterException("File contains no audio track")
    }

    private fun selectVideoTrack(extractor: MediaExtractor): MediaFormat {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)!!.startsWith("video/")) {
                extractor.selectTrack(i)
                return format
            }
        }

        throw InvalidParameterException("File contains no video track")
    }

    @SuppressLint("WrongConstant")

    /**
    getSampleTime() gives you the number of microseconds since the beginning of the track until the start of the current sample.
    getSampleFlags() gives you flags that are used by MediaCodec.
    MediaMuxer is kind of the counterpart to MediaExtractor. It can take various tracks with audio and video and mux them into one final media file.
    writeSampleData takes BufferInfor object as last argument. We can initialize the BufferInfo argument with information we get directly from MediaExtractor or MediaCodec.
     */

    // Extract all frames from selected track
    private fun muxVideo() {
        while (true) {
            val chunkSize = videoExtractor!!.readSampleData(buffer, 0)

            if (chunkSize >= 0) {

                bufferInfo.presentationTimeUs = videoExtractor!!.sampleTime
                bufferInfo.flags = videoExtractor!!.sampleFlags
                bufferInfo.size = chunkSize
                // Write encoded frames to muxer
                muxer!!.writeSampleData(videoIndex, buffer, bufferInfo)

                videoExtractor!!.advance()

            } else {
                break
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun muxAudio() {
        while (true) {
            val chunkSize = audioExtractor!!.readSampleData(buffer, 0)

            if (chunkSize > 0) {
                bufferInfo.presentationTimeUs = audioExtractor!!.sampleTime
                bufferInfo.flags = audioExtractor!!.sampleFlags
                bufferInfo.size = chunkSize

                muxer!!.writeSampleData(audioIndex, buffer, bufferInfo)
                audioExtractor!!.advance()
            } else {
                break
            }
        }
    }

    private fun cleanup() {
        muxer?.stop()
        muxer?.release()
        muxer = null

        videoExtractor?.release()
        videoExtractor = null

        audioExtractor?.release()
        audioExtractor = null

        videoIndex = -1
        audioIndex = -1
    }


}