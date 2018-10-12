package br.com.stv.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.*


//https://github.com/googlesamples/android-Camera2Basic/blob/master/kotlinApp/Application/src/main/java/com/example/android/camera2basic/Camera2BasicFragment.kt
// https://github.com/eddydn/AndroidCamera2API/blob/master/app/src/main/java/edmt/dev/androidcamera2api/MainActivity.java


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView.surfaceTextureListener = surfaceTextureListener
        btnCapture.setOnClickListener {
            takePicture()
        }
    }


    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraId: String
    private var cameraCaptureSessions: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null


    private var file: File? = null
    private var REQUEST_CAMERA_PERMISSION = 200
    private var flashSupported: Boolean? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private var sensorOrientation = 0

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview();
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }

    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {

        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = false

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    private fun takePicture() {
        cameraDevice?.let {
            val cameraManager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice!!.id)
                var jpegSizes: Array<Size>? = null
                cameraCharacteristics?.let {
                    jpegSizes = it.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG)
                    sensorOrientation = it.get(CameraCharacteristics.SENSOR_ORIENTATION)
                    jpegSizes
                }

                var width = 640
                var height = 480

                if (jpegSizes != null && jpegSizes!!.size > 0) {
                    width = jpegSizes!![0].getWidth();
                    height = jpegSizes!![0].getHeight();
                }

                val reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
                val outputSurface = ArrayList<Surface>(2)
                outputSurface.add(reader.surface)
                outputSurface.add(Surface(textureView.surfaceTexture))

                val captureBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureBuilder.addTarget(reader.surface)
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

//Check orientation base on device
                val rotation = windowManager.defaultDisplay.rotation
                captureBuilder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360
                )

                val path =
                    Environment.getExternalStorageDirectory().toString() + "/" + UUID.randomUUID().toString() + ".jpg"

                file = File(path)


                val readerListener = object : ImageReader.OnImageAvailableListener {
                    override fun onImageAvailable(imageReader: ImageReader) {
                        var image: Image? = null
                        try {
                            image = reader.acquireLatestImage()
                            val buffer = image!!.getPlanes()[0].getBuffer()
                            val bytes = ByteArray(buffer.capacity())
                            buffer.get(bytes)
                            save(bytes)

                        } catch (e: FileNotFoundException) {
                            e.printStackTrace()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } finally {
                            run {
                                if (image != null)
                                    image!!.close()
                            }
                        }
                    }

                    @Throws(IOException::class)
                    private fun save(bytes: ByteArray) {
                        var outputStream: OutputStream? = null
                        try {
                            outputStream = FileOutputStream(file)
                            outputStream!!.write(bytes)
                        } finally {
                            if (outputStream != null)
                                outputStream!!.close()
                        }
                    }
                }

                reader.setOnImageAvailableListener(readerListener, backgroundHandler)
                val captureListener = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        Toast.makeText(this@MainActivity, "Saved $file", Toast.LENGTH_SHORT).show()
                        createCameraPreview()
                    }
                }

                cameraDevice?.createCaptureSession(outputSurface, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun onConfigured(p0: CameraCaptureSession) {
                        try {
                            p0.capture(captureBuilder.build(), captureListener, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace();
                        }
                    }

                }, backgroundHandler)

            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }


        }
    }


    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]
            //Check realtime permission if run higher API 23
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CAMERA_PERMISSION
                )
                return
            }
            manager.openCamera(cameraId, stateCallback, null)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }


    private fun createCameraPreview() {
        try {
            val texture = textureView.surfaceTexture!!
            texture.setDefaultBufferSize(imageDimension!!.getWidth(), imageDimension!!.getHeight())
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder!!.addTarget(surface)
            cameraDevice!!.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    if (cameraDevice == null)
                        return
                    cameraCaptureSessions = cameraCaptureSession
                    updatePreview()
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Changed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun updatePreview() {
        if (cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show()
        captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions?.setRepeatingRequest(captureRequestBuilder?.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume();
        startBackgroundThread()
        if (textureView.isAvailable())
            openCamera()
        else
            textureView.setSurfaceTextureListener(surfaceTextureListener)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera Background")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.getLooper())
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }


    companion object {

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

    }

}

