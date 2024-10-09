package com.isvisoft.flutter_screen_recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.ActivityCompat
import com.foregroundservice.ForegroundService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.IOException

class FlutterScreenRecordingPlugin() :
        MethodCallHandler, PluginRegistry.ActivityResultListener, FlutterPlugin, ActivityAware {

    var mScreenDensity: Int = 0
    var mMediaRecorder: MediaRecorder? = null
    var mProjectionManager: MediaProjectionManager? = null
    var mMediaProjection: MediaProjection? = null
    var mMediaProjectionCallback: MediaProjectionCallback? = null
    var mVirtualDisplay: VirtualDisplay? = null
    var mDisplayWidth: Int = 1280
    var mDisplayHeight: Int = 800
    var videoName: String? = ""
    var mFileName: String? = ""
    var recordAudio: Boolean? = false
    private val SCREEN_RECORD_REQUEST_CODE = 333

    private lateinit var _result: MethodChannel.Result

    var pluginBinding: FlutterPlugin.FlutterPluginBinding? = null
    var activityBinding: ActivityPluginBinding? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        println("Request Code: $requestCode")
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                mMediaProjectionCallback = MediaProjectionCallback()
                mMediaProjection = mProjectionManager?.getMediaProjection(resultCode, data!!)
                mMediaProjection?.registerCallback(mMediaProjectionCallback!!, null)
                mVirtualDisplay = createVirtualDisplay()
                _result.success(true)
                return true
            } else {
                _result.success(false)
            }
        }
        return false
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        val appContext = pluginBinding!!.applicationContext

        when (call.method) {
            "initIntent" -> {}
            "startRecordScreen" -> {
                try {

                    _result = result

                    mProjectionManager =
                            appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as
                                    MediaProjectionManager?

                    val permissionIntent = mProjectionManager?.createScreenCaptureIntent()
                    var title = call.argument<String?>("title") ?: "Your screen is being recorded"
                    var message =
                            call.argument<String?>("message") ?: "Your screen is being recorded"
                    videoName = call.argument<String?>("name")
                    recordAudio = call.argument<Boolean?>("audio")

                    permissionIntent?.let {
                        ActivityCompat.startActivityForResult(
                                activityBinding!!.activity,
                                it,
                                SCREEN_RECORD_REQUEST_CODE,
                                null
                        )

                        ForegroundService.startService(appContext, title, message)
                        val metrics = appContext.getResources().getDisplayMetrics()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            mMediaRecorder = MediaRecorder(appContext)
                        } else {
                            @Suppress("DEPRECATION") mMediaRecorder = MediaRecorder()
                        }

                        mScreenDensity = metrics.densityDpi
                        calculateResolution(metrics)
                        mMediaRecorder?.let { startRecordScreen() }
                    }
                            ?: result.success(false)
                } catch (e: Exception) {
                    println("Error onMethodCall startRecordScreen")
                    println(e.message)
                    result.success(false)
                }
            }
            "stopRecordScreen" -> {
                try {
                    ForegroundService.stopService(appContext)
                    if (mMediaRecorder != null) {
                        stopRecordScreen()
                        result.success(mFileName)
                    } else {
                        result.success("")
                    }
                } catch (e: Exception) {
                    result.success("")
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun calculateResolution(metrics: DisplayMetrics) {
        val deviceAspectRatio = metrics.widthPixels.toDouble() / metrics.heightPixels.toDouble()

        // Determine the maximum target resolution based on device density
        val maxTargetResolution = if (metrics.density >= 3.0f) 1920.0 else 1280.0

        // Calculate the target resolution while maintaining aspect ratio
        val targetAspectRatio = minOf(deviceAspectRatio, maxTargetResolution / maxTargetResolution)
        val targetWidth =
                if (deviceAspectRatio > targetAspectRatio) {
                    metrics.widthPixels
                } else {
                    (metrics.heightPixels * targetAspectRatio).toInt()
                }
        val targetHeight =
                if (deviceAspectRatio < targetAspectRatio) {
                    metrics.heightPixels
                } else {
                    (metrics.widthPixels / targetAspectRatio).toInt()
                }

        // Scale the resolution to fit within the target dimensions
        val scaleFactor =
                minOf(
                        metrics.widthPixels.toFloat() / targetWidth,
                        metrics.heightPixels.toFloat() / targetHeight
                )

        if ((targetHeight * scaleFactor).toInt() <= 1920) {
            mDisplayHeight = (targetHeight * scaleFactor).toInt()
        }
        mDisplayHeight = 1920
        if ((targetWidth * scaleFactor).toInt() <= 1920) {
            mDisplayWidth = (targetWidth * scaleFactor).toInt()
        }
        mDisplayWidth = 1080

        println("Scaled Density")
        println(mScreenDensity)
        println("Original Resolution ")
        println("${metrics.widthPixels} x ${metrics.heightPixels}")
        println("Calculated Resolution ")
        println("$mDisplayWidth x $mDisplayHeight")
    }

    fun startRecordScreen() {
        try {
            try {
                mFileName = pluginBinding!!.applicationContext.externalCacheDir?.absolutePath
                mFileName += "/$videoName.mp4"
            } catch (e: IOException) {
                println("Error creating name")
                return
            }
            mMediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (recordAudio!!) {
                mMediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
                mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                mMediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            } else {
                mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            }
            mMediaRecorder?.setOutputFile(mFileName)
            mMediaRecorder?.setVideoSize(mDisplayWidth, mDisplayHeight)
            mMediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder?.setVideoEncodingBitRate(5 * mDisplayWidth * mDisplayHeight)
            mMediaRecorder?.setVideoFrameRate(60)

            mMediaRecorder?.prepare()
            mMediaRecorder?.start()
        } catch (e: IOException) {
            Log.d("--INIT-RECORDER", e.message + "")
            println("Error startRecordScreen")
            println(e.message)
        }
    }

    fun stopRecordScreen() {
        try {
            println("stopRecordScreen")
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()
            println("stopRecordScreen success")
        } catch (e: Exception) {
            Log.d("--INIT-RECORDER", e.message + "")
            println("stopRecordScreen error")
            println(e.message)
        } finally {
            stopScreenSharing()
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        try {
            return mMediaProjection?.createVirtualDisplay(
                    "MainActivity",
                    mDisplayWidth,
                    mDisplayHeight,
                    mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mMediaRecorder?.surface,
                    null,
                    null
            )
        } catch (e: Exception) {
            println("createVirtualDisplay err")
            println(e.message)
            return null
        }
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay?.release()
            if (mMediaProjection != null && mMediaProjectionCallback != null) {
                mMediaProjection?.unregisterCallback(mMediaProjectionCallback!!)
                mMediaProjection?.stop()
                mMediaProjection = null
            }
            Log.d("TAG", "MediaProjection Stopped")
        }
    }

    inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            mMediaRecorder?.reset()
            mMediaProjection = null
            stopScreenSharing()
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        pluginBinding = binding
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        val channel = MethodChannel(pluginBinding!!.binaryMessenger, "flutter_screen_recording")
        channel.setMethodCallHandler(this)
        activityBinding!!.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivity() {}
}
