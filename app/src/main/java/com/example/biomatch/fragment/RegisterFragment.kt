package com.example.biomatch.fragment

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.chaquo.python.Python
import com.example.biomatch.R
import com.example.biomatch.ml.ModelAtmTripletSiameseV1
import com.example.biomatch.ml.ModelScatDwtharrSiameseV1
import com.example.biomatch.yolo.customview.OverlayView
import com.example.biomatch.yolo.env.ImageUtils
import com.example.biomatch.yolo.tflite.Classifier
import com.example.biomatch.yolo.tflite.DetectorFactory
import com.example.biomatch.yolo.tflite.YoloV5Classifier
import com.example.biomatch.yolo.tracking.MultiBoxTracker
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.database.*
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.abs
import kotlin.system.measureTimeMillis


class RegisterFragment : Fragment() {

    private var TAG = "Register Fragment"

    private var camera: Camera? = null

    //    private lateinit var objectDetector: ObjectDetector
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



    private val MAINTAIN_ASPECT = true

    private var imageSize = 200
    var counter =0

    var timer = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view =  inflater.inflate(R.layout.fragment_register, container, false)

        previewView = view.findViewById(R.id.previewView)
        trackingOverlay = view.findViewById<OverlayView>(R.id.tracking_overlay)
        registerLayout = view.findViewById(R.id.register_frame)
        customCaptureLayout = view.findViewById(R.id.register_captureframe)
        registered_name_btn = view.findViewById<AppCompatEditText>(R.id.registrationname_edittxt)
        progressBar1 = view.findViewById(R.id.progress_bar_sample1)
        progressBar2 = view.findViewById(R.id.progress_bar_sample2)
        progressBar3 = view.findViewById(R.id.progress_bar_sample3)

        val sharedPreferences = activity?.getSharedPreferences("shared preferences",
            AppCompatActivity.MODE_PRIVATE
        )
        auto_phoneno = sharedPreferences?.getString("phoneno", "").toString()
        send_firebase = ArrayList<ArrayList<Deferred<FloatArray>>>()


        view.findViewById<AppCompatButton>(R.id.add_sample1_btn).setOnClickListener{
            registerLayout.visibility = View.INVISIBLE
            customCaptureLayout.visibility = View.VISIBLE
            greymapf = hashMapOf(7 to null,11 to null,15 to null,19 to null)
            approve1=-1.0
            approve2=-1.0
            approve3=-1.0
            approve4=-1.0
            counter=0
            val displayoutputlayout = view?.findViewById<LinearLayout>(R.id.captured_finger_reg)
            displayoutputlayout?.visibility = View.INVISIBLE
            runCustomView(view,1)
        }
        view.findViewById<AppCompatButton>(R.id.add_sample2_btn).setOnClickListener{
            registerLayout.visibility = View.INVISIBLE
            customCaptureLayout.visibility = View.VISIBLE
            greymapf = hashMapOf(7 to null,11 to null,15 to null,19 to null)
            approve1=-1.0
            approve2=-1.0
            approve3=-1.0
            approve4=-1.0
            counter=1
            val displayoutputlayout = view?.findViewById<LinearLayout>(R.id.captured_finger_reg)
            displayoutputlayout?.visibility = View.INVISIBLE
            runCustomView(view,2)
        }
        view.findViewById<AppCompatButton>(R.id.add_sample3_btn).setOnClickListener{
            registerLayout.visibility = View.INVISIBLE
            customCaptureLayout.visibility = View.VISIBLE
            greymapf = hashMapOf(7 to null,11 to null,15 to null,19 to null)
            approve1=-1.0
            approve2=-1.0
            approve3=-1.0
            approve4=-1.0
            counter=2
            val displayoutputlayout = view?.findViewById<LinearLayout>(R.id.captured_finger_reg)
            displayoutputlayout?.visibility = View.INVISIBLE
            runCustomView(view,3)
        }


        return view
    }

    fun runCustomView(view: View,sample: Int){
        tracker = MultiBoxTracker(activity)
        OpenCVLoader.initDebug()

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity())

        cameraProviderFuture.addListener({
            // get() is used to get the instance of the future.
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider = cameraProvider,view,sample)
            // Here, we will bind the preview
        }, ContextCompat.getMainExecutor(requireActivity()))


        try {
            detector = DetectorFactory.getDetector(requireActivity().assets, "finger_detect.tflite")
        } catch (e: IOException) {
            e.printStackTrace()

            val toast = Toast.makeText(
                activity, "Classifier could not be initialized", Toast.LENGTH_SHORT
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
        activity?.windowManager?.getDefaultDisplay()?.getMetrics(displayMetrics)
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
        return when (activity?.windowManager?.getDefaultDisplay()?.getRotation()) {
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
                it.setAnalyzer(ContextCompat.getMainExecutor(requireActivity()))
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
            val displayoutputlayout = view?.findViewById<LinearLayout>(R.id.captured_finger_reg)
            displayoutputlayout?.visibility = View.VISIBLE
            view?.findViewById<ImageView>(R.id.cap_finger1)?.setImageBitmap(greymapf.get(7))
            view?.findViewById<ImageView>(R.id.cap_finger2)?.setImageBitmap(greymapf.get(11))
            view?.findViewById<ImageView>(R.id.cap_finger3)?.setImageBitmap(greymapf.get(15))
            view?.findViewById<ImageView>(R.id.cap_finger4)?.setImageBitmap(greymapf.get(19))
            processing(greymapf)
            return 1
        }

        return 0
    }
//    private suspend fun processImage(key: Int, greymap: HashMap<Any, Bitmap?>): FloatArray = withContext(
//        Dispatchers.Default){
//        Log.d(TAG,key.toString())
//        var dist = (-1.0).toFloat()
//        var anc_enc = FloatArray(128)
//        try {
//            Log.d(TAG,Thread.currentThread().toString()+" is run")
//
//
//            var py = Python.getInstance()
//            var pyObj = py.getModule("myscript")
//            val prediction = ArrayList<Float>()
//
//            var imagestr = getStringImage(greymap[key])
//            var obj = pyObj.callAttr("main", imagestr,3)
//            var imgstr = obj.toString()
//            var data = Base64.decode(imgstr, Base64.DEFAULT)
//            var btmp = BitmapFactory.decodeByteArray(data, 0, data.size)
//            activity?.runOnUiThread {
//                if (key == 7) {
//                    view?.findViewById<ImageView>(R.id.cap_finger1)?.setImageBitmap(btmp)
//                } else if (key == 11) {
//                    view?.findViewById<ImageView>(R.id.cap_finger2)?.setImageBitmap(btmp)
//                } else if (key == 15) {
//                    view?.findViewById<ImageView>(R.id.cap_finger3)?.setImageBitmap(btmp)
//                } else if (key == 19) {
//                    view?.findViewById<ImageView>(R.id.cap_finger4)?.setImageBitmap(btmp)
//                }
//            }
//
//
//
//            Log.d("IMAGE", greymap[key].toString())
//            if (btmp != null) {
////                    var image1 = Bitmap.createScaledBitmap(btmp, imageSize, imageSize, false)
////                    var image2 = Bitmap.createScaledBitmap(btmp1, imageSize, imageSize, false)
//                Log.d(TAG, "scaled done")
//
//                Log.d("IMAGE", "NEXT2")
//
//
//                var pyObj = py.getModule("myscript")
//
//                var imagestr = getStringImage(btmp)
//                var obj = pyObj.callAttr("getpixel", imagestr)
//                var imgstr = obj.toString()
//                val intValues = imgstr.split(" ")
//
//
//                val inputFeature0 =
//                    TensorBuffer.createFixedSize(intArrayOf(1, 200, 200), DataType.FLOAT32)
//                var byteBuffer1: ByteBuffer =
//                    ByteBuffer.allocateDirect(4 * imageSize * imageSize)
//                byteBuffer1.order(ByteOrder.nativeOrder())
//                var pixel = 0
//                for (i in 0 until imageSize) {
//                    for (j in 0 until imageSize) {
//                        //                                    for (k in 0 until 3){
//                        var vals = intValues[pixel++].toInt()// RGB
//                        byteBuffer1.putFloat(
//                            (vals * (1F / 255f).toDouble().toBigDecimal()
//                                .setScale(6, BigDecimal.ROUND_HALF_UP).toFloat())
//                        )
//                    }
//                }
//
//
////                                    val model = SiamesemodelEnh.newInstance(this@PAGE_Matching)  //Recent and best model of siamese enh with scattering2d with input image(96,96)
//                val model = ModelScatDwtharrSiameseV1.newInstance(requireActivity()) //input_image(200,200)
//                // Creates inputs for reference.
//                // Creates inputs for reference.
//                Log.d("", inputFeature0.toString())
//                inputFeature0.loadBuffer(byteBuffer1)
//                // Runs model inference and gets result.
//                val outputs = model.process(inputFeature0)
//                val outputFeature0 = outputs.outputFeature0AsTensorBuffer
//
//
//                var confidence = outputFeature0.floatArray
//
//                anc_enc = confidence
//
//                model.close()
//
//
//            }
//            Log.d("IMAGE", "PYTHON SCRIPT Processed")
//
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//
//        anc_enc
//
//    }

    private suspend fun processImage(key: Int, greymap: HashMap<Any, Bitmap?>): FloatArray = withContext(
        Dispatchers.Default){
        Log.d(TAG,key.toString())
        var dist = (-1.0).toFloat()
        var anc_enc = FloatArray(128)
        try {
            Log.d(TAG,Thread.currentThread().toString()+" is run")


            var py = Python.getInstance()
            var pyObj = py.getModule("myscript")
            val prediction = ArrayList<Float>()

            var imagestr = getStringImage(greymap[key])
            var obj = pyObj.callAttr("main", imagestr,3)
            var imgstr = obj.toString()
            var data = Base64.decode(imgstr, Base64.DEFAULT)
            var btmp = BitmapFactory.decodeByteArray(data, 0, data.size)

            activity?.runOnUiThread {
                if (key == 7) {
                    view?.findViewById<ImageView>(R.id.cap_finger1)?.setImageBitmap(btmp)
                } else if (key == 11) {
                    view?.findViewById<ImageView>(R.id.cap_finger2)?.setImageBitmap(btmp)
                } else if (key == 15) {
                    view?.findViewById<ImageView>(R.id.cap_finger3)?.setImageBitmap(btmp)
                } else if (key == 19) {
                    view?.findViewById<ImageView>(R.id.cap_finger4)?.setImageBitmap(btmp)
                }
            }


            Log.d("IMAGE", greymap[key].toString())
            if (btmp != null) {
//                    var image1 = Bitmap.createScaledBitmap(btmp, imageSize, imageSize, false)
//                    var image2 = Bitmap.createScaledBitmap(btmp1, imageSize, imageSize, false)
                Log.d(TAG, "scaled done")

                Log.d("IMAGE", "NEXT2")


                var pyObj = py.getModule("myscript")

                var imagestr = getStringImage(btmp)
                var obj = pyObj.callAttr("getpixel", imagestr)
                var imgstr = obj.toString()
                val intValues = imgstr.split(" ")
                println(intValues.size)

                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 200, 200), DataType.FLOAT32)
//                TensorBuffer.createFixedSize(intArrayOf(1, 200, 200), DataType.FLOAT32)
                var byteBuffer1: ByteBuffer =
//                    ByteBuffer.allocateDirect(4 *225*174*3)

                    ByteBuffer.allocateDirect(4 * imageSize * imageSize)
                byteBuffer1.order(ByteOrder.nativeOrder())
                var pixel = 0
                for (i in 0 until 200) {
                    for (j in 0 until 200) {
//                        for(k in 0 until 3) {
                            //                                    for (k in 0 until 3){
                            var vals = intValues[pixel++].toInt()// RGB
                            byteBuffer1.putFloat(
                                (vals * (1F / 255f).toDouble().toBigDecimal()
                                    .setScale(6, BigDecimal.ROUND_HALF_UP).toFloat())
                            )
//                        }
                    }
                }


//                                    val model = SiamesemodelEnh.newInstance(this@PAGE_Matching)  //Recent and best model of siamese enh with scattering2d with input image(96,96)
                val model = ModelScatDwtharrSiameseV1.newInstance(requireActivity()) //input_image(200,200)
//                val model = ModelAtmTripletSiameseV1.newInstance(requireActivity()) //input_image(200,200)

                // Creates inputs for reference.
                // Creates inputs for reference.
                Log.d("", inputFeature0.toString())
                inputFeature0.loadBuffer(byteBuffer1)
                // Runs model inference and gets result.
                val outputs = model.process(inputFeature0)
                val outputFeature0 = outputs.outputFeature0AsTensorBuffer


                var confidence = outputFeature0.floatArray

                anc_enc = confidence

                model.close()


            }
            Log.d("IMAGE", "PYTHON SCRIPT Processed")

        } catch (e: IOException) {
            e.printStackTrace()
        }

        anc_enc

    }

    private fun getStringImage(grayBitmap: Bitmap?): String? {
        var baos= ByteArrayOutputStream()
        grayBitmap?.compress(Bitmap.CompressFormat.PNG,100,baos)
        var imgByte = baos.toByteArray()
        var encodedImg = android.util.Base64.encodeToString(imgByte,android.util.Base64.DEFAULT)
        return encodedImg
    }
    fun processing(greymap123: HashMap<Any, Bitmap?>){
        var greymap = greymapf
        var progressval = 100
//        activity?.runOnUiThread {
//            increaseProgressBar(progressval,25000)
//            progressval+=900
//        }
        CoroutineScope(Dispatchers.IO).launch {
            val executionTime = measureTimeMillis {
                val process1: Deferred<FloatArray> = async {
                    println("debug: processing 7 from Thread: ${Thread.currentThread().name}")
                    processImage(7, greymap)
                }

                val process2: Deferred<FloatArray> = async {
                    println("debug: processing 11 from Thread: ${Thread.currentThread().name}")
                    processImage(11, greymap)
                }

                val process3: Deferred<FloatArray> = async {
                    println("debug: processing 15 from Thread: ${Thread.currentThread().name}")
                    processImage(15, greymap)
                }

                val process4: Deferred<FloatArray> = async {
                    println("debug: processing 19 from Thread: ${Thread.currentThread().name}")
                    processImage(19, greymap)
                }


                println("debug: Score for process1: ${process1.await()}")
                println("debug: Score for process2: ${process2.await()}")
                println("debug: Score for process3: ${process3.await()}")
                println("debug: Score for process4: ${process4.await()}")
                saved_finger.add(process1)
                saved_finger.add(process2)
                saved_finger.add(process3)
                saved_finger.add(process4)



                var arr= kotlin.collections.ArrayList<Float>()
                for( x in process1.await()){
                    arr.add(x.toFloat())
                }
                saveFireBaseData("7",arr)
                var arr1= kotlin.collections.ArrayList<Float>()
                for( x in process2.await()){
                    arr1.add(x.toFloat())
                }
                saveFireBaseData("11",arr1)
                var arr2= kotlin.collections.ArrayList<Float>()
                for( x in process3.await()){
                    arr2.add(x.toFloat())
                }
                saveFireBaseData("15",arr2)
                var arr3= kotlin.collections.ArrayList<Float>()
                for( x in process4.await()){
                    arr3.add(x.toFloat())
                }
                saveFireBaseData("19",arr3)

                send_firebase.add(saved_finger)
                saved_finger = ArrayList<Deferred<FloatArray>>()
            }
            println("debug: Total time elapsed: ${executionTime}")
        }

    }
    fun saveFireBaseData(finger_name: String, send:ArrayList<Float>) {
        Log.d("SAVING", "DATA")
        val reference1: DatabaseReference
        rootNode = FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")

        reference1 = rootNode!!.getReference("data")
        if(registered_name_btn.text!!.length >0) {
            reference1.child(registered_name_btn.text.toString()).child(counter.toString())
                .child(finger_name).setValue(send)
        }
        activity?.runOnUiThread {
            increaseProgressBar(1000,2000)
            if (counter == 0){
                view?.findViewById<AppCompatButton>(R.id.add_sample1_btn)?.text =
                    "DONE"

            }
            if (counter == 1){
                view?.findViewById<AppCompatButton>(R.id.add_sample2_btn)?.text =
                    "DONE"

            }
            if(counter==2) {
                view?.findViewById<AppCompatButton>(R.id.add_sample3_btn)?.text =
                    "DONE"

            }
        }

    }

    fun increaseProgressBar(value: Int,time: Long){
        if(counter==0){
            ObjectAnimator.ofInt(progressBar1, "progress", value).setDuration(time).start()
        }
        else if(counter==1){
            ObjectAnimator.ofInt(progressBar2, "progress", value).setDuration(time).start()
        }
        else if(counter==2){
            ObjectAnimator.ofInt(progressBar3, "progress", value).setDuration(time).start()
        }
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





}