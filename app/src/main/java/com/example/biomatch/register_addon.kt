package com.example.biomatch

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.biomatch.yolo.customview.OverlayView
import com.example.biomatch.yolo.env.ImageUtils
import com.example.biomatch.yolo.tflite.Classifier
import com.example.biomatch.yolo.tflite.DetectorFactory
import com.example.biomatch.yolo.tflite.YoloV5Classifier
import com.example.biomatch.yolo.tracking.MultiBoxTracker
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Deferred
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.abs

class register_addon : AppCompatActivity() {
    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private var detector: YoloV5Classifier? = null
    private var tracker: MultiBoxTracker? = null
    var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null
    private lateinit var cropBitmap: Bitmap
    private var previewWidth: Int? = null
    private var previewHeight: Int? = null
    private var cropSize: Int = 0
    private var cropToFrameTransform: Matrix? = null
    private var frameToCropTransform: Matrix? = null
    private var lastAnalyzedTimestamp = 0L
    private var saved_finger= ArrayList<Deferred<FloatArray>>()

    private lateinit var registerLayout: LinearLayout
    private lateinit var customCaptureLayout: FrameLayout
    private lateinit var progressBar1: ProgressBar
    private lateinit var progressBar2: ProgressBar
    private lateinit var progressBar3: ProgressBar
    private val MAINTAIN_ASPECT = true
    private var TAG = "Register Fragment"
    var rootNode: FirebaseDatabase? = null
    var reference: DatabaseReference? = null

    lateinit var registered_name_btn: AppCompatEditText
    lateinit var send_firebase: ArrayList<ArrayList<Deferred<FloatArray>>>
    lateinit var auto_phoneno: String

    private var greymapf: HashMap<Any, Bitmap?> = hashMapOf(7 to null,11 to null,15 to null,19 to null)
    private var approve1=-1.0
    private var approve2=-1.0
    private var approve3=-1.0
    private var approve4=-1.0
    var timer = 0

    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_addon)
    }
    fun runCustomView(view: View, sample: Int){
        tracker = MultiBoxTracker(this)
        OpenCVLoader.initDebug()

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // get() is used to get the instance of the future.
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider = cameraProvider,view,sample)
            // Here, we will bind the preview
        }, ContextCompat.getMainExecutor(this))


        try {
            detector = DetectorFactory.getDetector(assets, "finger_detect.tflite")
        } catch (e: IOException) {
            e.printStackTrace()

            val toast = Toast.makeText(
                this, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
        }
        cropSize = 416
        detector?.useCPU()
        detector!!.setNumThreads(1)
        trackingOverlay?.addCallback { canvas ->
            tracker?.draw(canvas)
        }
        val displayMetrics = DisplayMetrics()
        windowManager?.getDefaultDisplay()?.getMetrics(displayMetrics)
        previewWidth = 1920
        previewHeight = 1080

        val rotation = 90
        sensorOrientation = rotation - getScreenOrientation()
        cropBitmap = Bitmap.createBitmap(cropSize!!, cropSize!!, Bitmap.Config.ARGB_8888)
        tracker?.setFrameConfiguration(previewWidth!!, previewHeight!!, sensorOrientation!!)
        frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                previewWidth!!,
                previewHeight!!,
                cropSize!!,
                cropSize!!,
                sensorOrientation!!,
                MAINTAIN_ASPECT)
    }
    protected fun getScreenOrientation(): Int {
        return when (windowManager?.getDefaultDisplay()?.getRotation()) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider,view: View,sample: Int){
        val currTimestamp: Long = 0
        trackingOverlay?.postInvalidate()

        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        var imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1080,1920))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(this))
                {
                    val rotationDegress = it.imageInfo.rotationDegrees
                    println(rotationDegress)
                    val image = it.image

                    if (image != null) {

                        // Initialize the storage bitmaps once when the resolution is known.
                        var bitmap = image.toBitmap()
                        previewWidth= bitmap.width
                        previewHeight = bitmap.height

                        cropToFrameTransform = Matrix()
                        frameToCropTransform?.invert(cropToFrameTransform)

                        var canvas = cropBitmap?.let { Canvas(it) }
                        var rgbbitmap =  Bitmap.createScaledBitmap(bitmap, previewWidth!!, previewHeight!!, true)

                        canvas?.drawBitmap(rgbbitmap, frameToCropTransform!!, null)


                        val results: List<Classifier.Recognition> =
                            detector!!.recognizeImage(cropBitmap)
                        Log.e("CHECK", "run: " + results.size)
                        val mappedRecognitions: MutableList<Classifier.Recognition> =
                            LinkedList<Classifier.Recognition>()
                        var cropCopyBitmap = Bitmap.createBitmap(cropBitmap)
                        canvas = Canvas(cropCopyBitmap)
                        val paint = Paint()
                        paint.color = Color.RED
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2.0f

                        for (result in results) {
                            val location = result.location
                            if (location != null && result.confidence >= 0.5) {

                                canvas.drawRect(location, paint)
                                cropToFrameTransform?.mapRect(location);
                                result.location = location
                                mappedRecognitions.add(result)
                                val result = autoCapture(mappedRecognitions,bitmap)
                                if(result==1) {
                                    cameraProvider.unbindAll()
//                                    detector!!.close()
                                    customCaptureLayout.visibility = View.GONE
                                    registerLayout.visibility = View.VISIBLE
                                    if (sample == 1){
                                        view.findViewById<AppCompatButton>(R.id.add_sample1_btn).text =
                                            "PENDING"
                                        view.findViewById<AppCompatButton>(R.id.add_sample1_btn).isEnabled =
                                            false
                                    }
                                    if (sample == 2){
                                        view.findViewById<AppCompatButton>(R.id.add_sample2_btn).text =
                                            "PENDING"
                                        view.findViewById<AppCompatButton>(R.id.add_sample2_btn).isEnabled =
                                            false
                                    }
                                    if(sample==3) {
                                        view.findViewById<AppCompatButton>(R.id.add_sample3_btn).text =
                                            "PENDING"
                                        view.findViewById<AppCompatButton>(R.id.add_sample3_btn).isEnabled =
                                            false
                                    }
                                }
                            }
                        }
                        tracker?.trackResults(mappedRecognitions, currTimestamp)
                        trackingOverlay!!.postInvalidate()

//
//                        imageproxy.close()
//
//                    }.addOnFailureListener {
//
//                        imageproxy.close()
//
//                    }
                    }
                    it.close()
                }
            }

        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            camera!!.cameraControl.enableTorch(true)
            // Attach the viewfinder's surface provider to preview use case
//            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
        cameraProvider.bindToLifecycle(this as LifecycleOwner,cameraSelector,imageAnalysis,preview)

    }
    private fun autoCapture(
        mappedRecognitions: MutableList<Classifier.Recognition>,
        cropCopyBitmap: Bitmap,

        ): Int {
        val top1 = ArrayList<Bitmap>()
        var fingtest = 10
        var greymap: HashMap<Any, Bitmap> = hashMapOf(7 to cropCopyBitmap, 11 to cropCopyBitmap, 15 to cropCopyBitmap, 19 to cropCopyBitmap)

        if(mappedRecognitions.size ==4) {
            for( i in mappedRecognitions){
                if(i.detectedClass==3){
                    fingtest-=1
                }
                if(i.detectedClass==2){
                    fingtest-=2
                }
                if(i.detectedClass==1){
                    fingtest-=3
                }
                if(i.detectedClass==0){
                    fingtest-=4
                }
            }
            if(fingtest==0){
                var avgFocus = 0.0
                //            timer+=1

                for (i in mappedRecognitions) {
                    Log.d("AUTOCAPTURE", i.toString())
                    //                Log.d("AUTOCAPTURE", cropCopyBitmap.width.toString()+ "   "+cropCopyBitmap.height.toString())
                    var tmp_btmp= Bitmap.createBitmap(
                        cropCopyBitmap,
                        abs(i.location.left).toInt(),
                        abs(i.location.top).toInt(),
                        abs(i.location.left - i.location.right).toInt(),
                        abs(i.location.top - i.location.bottom).toInt()
                    )

                    top1.add(tmp_btmp)
                    val focusLevel = calculateFocusLevel(tmp_btmp)
                    avgFocus+=focusLevel
                    if(i.detectedClass==0 && focusLevel>=95){
                        if(focusLevel>approve1) {
                            greymap.set(7, tmp_btmp)
                            greymapf.set(7, tmp_btmp)
                            approve1 = focusLevel

                        }

                    }
                    else if(i.detectedClass==1 && focusLevel>=85){
                        if(focusLevel>approve2) {
                            greymap.set(11, tmp_btmp)
                            greymapf.set(11, tmp_btmp)
                            approve2 = focusLevel
                        }

                    }
                    else if(i.detectedClass==2 && focusLevel>=40){
                        if(focusLevel>approve3) {
                            greymap.set(15, tmp_btmp)
                            greymapf.set(15, tmp_btmp)
                            approve3 = focusLevel
                        }

                    }
                    else if(i.detectedClass==3 && focusLevel>=75){
                        if(focusLevel>approve4) {
                            greymap.set(19, tmp_btmp)
                            greymapf.set(19, tmp_btmp)
                            approve4 = focusLevel
                        }
                    }


                    //                Log.d("AUTOCAPTURE",focusLevel.toString())
                    // Use the focus level as needed
                    Log.d("AUTOCAPTURE1",focusLevel.toString())
                    Log.d("AUTOCAPTURE1",i.detectedClass.toString())

                    //

                }

                val currentTimestamp = System.currentTimeMillis()
                if (currentTimestamp - lastAnalyzedTimestamp >= 1) {
                    if(avgFocus/4 > 65){
                        timer+=2
                    }
                    lastAnalyzedTimestamp = currentTimestamp
                }
            }
        }

        if(timer>=20 && approve1>=95 && approve2>=85 && approve3>=40 && approve4>=75){
            timer=0
            detector?.close()
            camera!!.cameraControl.enableTorch(false)
            val displayoutputlayout = findViewById<LinearLayout>(R.id.captured_finger_reg)
            displayoutputlayout?.visibility = View.VISIBLE
            findViewById<ImageView>(R.id.cap_finger1)?.setImageBitmap(greymapf.get(7))
            findViewById<ImageView>(R.id.cap_finger2)?.setImageBitmap(greymapf.get(11))
            findViewById<ImageView>(R.id.cap_finger3)?.setImageBitmap(greymapf.get(15))
            findViewById<ImageView>(R.id.cap_finger4)?.setImageBitmap(greymapf.get(19))
            return 1
        }

        return 0
    }
    fun calculateFocusLevel(bitmap: Bitmap): Double {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val laplacian = Mat()
        Imgproc.Laplacian(grayMat, laplacian, CvType.CV_64F)

        val laplacianVar = Mat()
        Core.multiply(laplacian, laplacian, laplacianVar)
        val focusMeasure = Core.mean(laplacianVar)

        return focusMeasure.`val`[0].coerceIn(0.0,100.0)
    }

    fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }



}

