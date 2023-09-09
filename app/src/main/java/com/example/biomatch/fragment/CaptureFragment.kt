package com.example.biomatch.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Rect
import android.media.Image
import android.os.Bundle
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.fragment.app.Fragment
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.android.Utils.bitmapToMat
import org.opencv.android.Utils.matToBitmap
import org.opencv.core.*
import org.opencv.core.CvType.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.filter2D
import org.opencv.imgproc.Imgproc.medianBlur
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.*
import kotlin.system.measureTimeMillis


class CaptureFragment : Fragment(){

    private var TAG = "Capture Fragment"

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

    private lateinit var identifystatus: TextView
    private lateinit var identifyname: TextView

    private val MAINTAIN_ASPECT = true

    private var imageSize = 200

    var timer = 0

    private var greymapf: HashMap<Any, Bitmap?> = hashMapOf(7 to null,11 to null,15 to null,19 to null)
    private var approve1=-1.0
    private var approve2=-1.0
    private var approve3=-1.0
    private var approve4=-1.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view =  inflater.inflate(R.layout.fragment_capture, container, false)

        previewView = view.findViewById(R.id.previewView)
        trackingOverlay = view.findViewById<OverlayView>(R.id.tracking_overlay)
        identifystatus = view.findViewById(R.id.capture_identifystatus)
        identifyname = view.findViewById(R.id.capture_identifyname)

        greymapf = hashMapOf(7 to null,11 to null,15 to null,19 to null)
        approve1=-1.0
        approve2=-1.0
        approve3=-1.0
        approve4=-1.0

        tracker = MultiBoxTracker(activity)
        OpenCVLoader.initDebug()

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity())

        cameraProviderFuture.addListener({
            // get() is used to get the instance of the future.
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider = cameraProvider,view)
            // Here, we will bind the preview
        }, getMainExecutor(requireActivity()))


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

        return view
    }
    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    protected fun getScreenOrientation(): Int {
        return when (activity?.windowManager?.getDefaultDisplay()?.getRotation()) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    fun convertRectCoordinates(
        x: Float, y: Float, width: Float, height: Float,
        previewHeight: Int,
        previewWidth: Int,
        score: Float): ArrayList<Float> {
        // Calculate the scaling factor for each dimension
        val scaleFactorX = previewWidth / 416f
        val scaleFactorY = previewHeight/ 416f

        // Scale the coordinates and dimensions
        val newX = (x * scaleFactorX)
        val newY = (y * scaleFactorY)
        val newWidth = (width * scaleFactorX)
        val newHeight = (height * scaleFactorY)

        // Return the new coordinates and dimensions as a list
        return arrayListOf(newX, newY, newWidth, newHeight,score)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider,view: View){
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
                                if(result==1){
                                    cameraProvider.unbindAll()
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

    fun removeBackground(image: Bitmap): Bitmap {
        val imageMat = Mat()
        bitmapToMat(image, imageMat)

        // Convert image to grayscale
        val grayscaleMat = Mat()
        Imgproc.cvtColor(imageMat, grayscaleMat, Imgproc.COLOR_BGR2GRAY)

        // Apply thresholding to separate object from the background
        val thresholdMat = Mat()
        Imgproc.threshold(grayscaleMat, thresholdMat, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        // Create a mask with the threshold result
        val maskMat = Mat()
        val foreground = Scalar(0.0)
        val background = Scalar(255.0)
        Core.compare(thresholdMat, foreground, maskMat, Core.CMP_EQ)

        // Apply the mask to the original image
        val resultMat = Mat()
        imageMat.copyTo(resultMat, maskMat)

        // Convert the result back to Bitmap
        val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
        matToBitmap(resultMat, resultBitmap)

        return resultBitmap
    }
    private fun autoCapture(
        mappedRecognitions: MutableList<Classifier.Recognition>,
        cropCopyBitmap: Bitmap,

        ): Int {
        val top1 = ArrayList<Bitmap>()
        var fingtest = 10
        var greymap: HashMap<Any, Bitmap> = hashMapOf(7 to cropCopyBitmap, 11 to cropCopyBitmap, 15 to cropCopyBitmap, 19 to cropCopyBitmap)

        if(mappedRecognitions.size ==4 || mappedRecognitions.size ==3) {
            for( i in mappedRecognitions){
                if(i.detectedClass==2){
                    fingtest-=1
                }
                if(i.detectedClass==3){
                    fingtest-=2
                }
                if(i.detectedClass==1){
                    fingtest-=3
                }
                if(i.detectedClass==0){
                    fingtest-=4
                }
            }
            if(fingtest==1 || fingtest==0){
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
                    if(i.detectedClass==0 && focusLevel>=90){
                        if(focusLevel>approve1) {
                            greymap.set(7, tmp_btmp)
                            activity?.runOnUiThread {
                                identifystatus.visibility = View.VISIBLE
                                identifystatus.text =
                                    i.detectedClass.toString() + ": " + focusLevel.toString()
                            }
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

        if(timer>=20 && approve1>=90 && approve2>=85 && approve3>=40 && approve4>=75){
            previewView.visibility = View.GONE
            trackingOverlay?.visibility = View.GONE
            val displayoutputlayout = view?.findViewById<LinearLayout>(R.id.display_Output_layout)
            displayoutputlayout?.visibility = View.VISIBLE
//            view?.findViewById<ImageView>(R.id.finger1)?.setImageBitmap(top1[0])
//            view?.findViewById<ImageView>(R.id.finger2)?.setImageBitmap(top1[1])
//            view?.findViewById<ImageView>(R.id.finger3)?.setImageBitmap(top1[2])
//            view?.findViewById<ImageView>(R.id.finger4)?.setImageBitmap(top1[3])
            view?.findViewById<ImageView>(R.id.finger1)?.setImageBitmap(greymapf.get(7))
            view?.findViewById<ImageView>(R.id.finger2)?.setImageBitmap(greymapf.get(11))
            view?.findViewById<ImageView>(R.id.finger3)?.setImageBitmap(greymapf.get(15))
            view?.findViewById<ImageView>(R.id.finger4)?.setImageBitmap(greymapf.get(19))
            timer=0
            detector?.close()
            camera!!.cameraControl.enableTorch(false)
//            cameraProviderFuture.cancel()
            processing(greymapf)
            return 1
        }

        return 0
    }

    private inner class FocusAnalyzer(private val focusMeasurementCallback: (Int) -> Unit) :
        ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            // Perform focus measurement on the captured image
            val focusMeasurement = calculateFocus(image)
            focusMeasurementCallback(focusMeasurement)
            image.close()
        }

        private fun calculateFocus(image: ImageProxy): Int {
            // Perform your focus measurement calculation here
            // You can access the image data using image.planes[i].buffer

            // Example: Calculate focus as the average pixel value
            val buffer = image.planes[0].buffer
            val pixelArray = ByteArray(buffer.remaining())
            buffer.get(pixelArray)
            val totalPixels = pixelArray.size
            val sum = pixelArray.sumOf { it.toInt() and 0xFF }
            val averagePixel = sum / totalPixels

            // Convert average pixel value to a focus value between 0 and 100
            return (averagePixel.toDouble() / 255.0 * 100).toInt()
        }
    }
    private suspend fun processImage(key: Int, greymap: HashMap<Any, Bitmap?>): FloatArray = withContext(
        Dispatchers.Default){
        Log.d(TAG,key.toString())
        var dist = (-1.0).toFloat()
        var anc_enc = FloatArray(128)
        try {
            Log.d(TAG,Thread.currentThread().toString()+" is run")


            var py = Python.getInstance()
            var pyObj = py.getModule("myscript")
//            val prediction = ArrayList<Float>()
//
//            var imagestr = getStringImage(greymap[key])
//            var obj = pyObj.callAttr("main", imagestr,3)
//            var imgstr = obj.toString()
//            var data = Base64.decode(imgstr, Base64.DEFAULT)
//            var btmp = BitmapFactory.decodeByteArray(data, 0, data.size)

            var mask = greymap[key]?.let { removeBackground(it) }
//            var median= mask?.let { applyMedianBlur(it,3) }
            var atm_img = mask?.let { applyAdaptiveMeanThresholding(it) }
            val btmp = atm_img?.let { applyHistogramEqualization(atm_img) }



            activity?.runOnUiThread {
                if (key == 7) {
                    view?.findViewById<ImageView>(R.id.finger1)?.setImageBitmap(btmp)
                } else if (key == 11) {
                    view?.findViewById<ImageView>(R.id.finger2)?.setImageBitmap(btmp)
                } else if (key == 15) {
                    view?.findViewById<ImageView>(R.id.finger3)?.setImageBitmap(btmp)
                } else if (key == 19) {
                    view?.findViewById<ImageView>(R.id.finger4)?.setImageBitmap(btmp)
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

                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, imageSize, imageSize), DataType.FLOAT32)
//                TensorBuffer.createFixedSize(intArrayOf(1, 200, 200), DataType.FLOAT32)
                var byteBuffer1: ByteBuffer =  ByteBuffer.allocateDirect(4 *imageSize*imageSize)

//                    ByteBuffer.allocateDirect(4 * imageSize * imageSize)
                byteBuffer1.order(ByteOrder.nativeOrder())
                var pixel = 0
                for (i in 0 until 200) {
                    for (j in 0 until 200) {
//                        for(k in 0 until 3)
                        //                                    for (k in 0 until 3){
                        var vals = intValues[pixel++].toInt()// RGB
                        byteBuffer1.putFloat(
                            (vals * (1F / 255f).toDouble().toBigDecimal()
                                .setScale(6, BigDecimal.ROUND_HALF_UP).toFloat())
                        )
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
//    private fun calculateFocusLevel(bitmap: Bitmap): Float {
//        // Calculate focus level of the fingertip using your preferred algorithm
//        // You can use OpenCV or other image processing libraries for more advanced calculations
//        // Here's a simple example using average pixel intensity as focus level
//        var totalIntensity = 0
//        for (y in 0 until bitmap.height) {
//            for (x in 0 until bitmap.width) {
//                val pixel = bitmap.getPixel(x, y)
//                val intensity = (pixel and 0xFF) + ((pixel shr 8) and 0xFF) + ((pixel shr 16) and 0xFF)
//                totalIntensity += intensity
//            }
//        }
//        val averageIntensity = totalIntensity.toFloat() / (bitmap.width * bitmap.height * 3)
//        return averageIntensity
//    }
//    fun calculateFocusLevel(bitmap: Bitmap): Int {
//        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1)
//        Utils.bitmapToMat(bitmap, mat)
//
//        val grayMat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1)
//        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
//
//        val laplacian = Mat()
//        Imgproc.Laplacian(grayMat, laplacian, CV_32F)
//
//        val mean = Core.mean(laplacian)
//        val focus = mean.`val`[0]
//
//        // Normalize focus value to range 0-100
//        val normalizedFocus = ((focus - 1000) / 4000 * 100).toInt().coerceIn(0, 100)
//
//        return normalizedFocus
//    }

//    fun calculateFocusLevel(bitmap: Bitmap): Int {
//        val mat = Mat()
//        Utils.bitmapToMat(bitmap, mat)
//
//        val grayMat = Mat()
//        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
//m
//        val gradientX = Mat()
//        val gradientY = Mat()
//        Imgproc.Sobel(grayMat, gradientX, CvType.CV_32F, 1, 0)
//        Imgproc.Sobel(grayMat, gradientY, CvType.CV_32F, 0, 1)
//
//        val gradientMagnitude = Mat()
//        Core.magnitude(gradientX, gradientY, gradientMagnitude)
//
//        val focus = Core.mean(gradientMagnitude).`val`[0]
//
//        // Normalize focus value to range 0-100
//        val normalizedFocus = (focus / 100.0 * 100).toInt().coerceIn(0, 100)
//
//        return normalizedFocus
//    }

    fun processing(greymap: HashMap<Any, Bitmap?>){
        CoroutineScope(Dispatchers.IO).launch {

            activity?.runOnUiThread{
//                view?.findViewById<ImageView>(R.id.finger1)?.setImageBitmap()

                identifystatus.visibility = View.VISIBLE
                identifystatus.text = "PROCESSING, PLEASE WAIT:)"
            }
            val executionTime = measureTimeMillis {
                val process1: Deferred<FloatArray> = async {
                    println("debug: processing 7 from Thread: ${Thread.currentThread().name}")
                    processImage(7, greymap)
//                    greymap[7]?.let { applyAdaptiveMeanThresholding(it) }
                }
//
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
////                activity?.runOnUiThread{
////                    view?.findViewById<ImageView>(R.id.finger1)?.setImageBitmap(process1.await())
////                    view?.findViewById<ImageView>(R.id.finger2)?.setImageBitmap(process2.await())
////                    view?.findViewById<ImageView>(R.id.finger3)?.setImageBitmap(process3.await())
////                    view?.findViewById<ImageView>(R.id.finger4)?.setImageBitmap(top1[3])
////                }
//                saved_finger.add(process1)
//                saved_finger.add(process2)
//                saved_finger.add(process3)
//                saved_finger.add(process4)
//                getFireBaseData(saved_finger.awaitAll())
            }
//            println("debug: Total time elapsed: ${executionTime}")
        }

    }
    fun applyAdaptiveMeanThresholding(image: Bitmap, block_size: Int = 7, subtraction_const: Int = 1): Bitmap {
        val imageMat = Mat()
        bitmapToMat(image, imageMat)

        val grayMat = Mat()
        Imgproc.cvtColor(imageMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val thresholdedMat = Mat()
        Imgproc.adaptiveThreshold(
            grayMat,
            thresholdedMat,
            255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY,
            block_size,
            subtraction_const.toDouble()
        )

        val thresholdedBitmap = Bitmap.createBitmap(
            thresholdedMat.cols(),
            thresholdedMat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(thresholdedMat, thresholdedBitmap)

        return invertColors(thresholdedBitmap)
    }
    fun invertColors(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val invertedBitmap = Bitmap.createBitmap(width, height, bitmap.config)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val invertedPixel = pixel.inv() and 0x00FFFFFF or (pixel and -0x01000000)
                invertedBitmap.setPixel(x, y, invertedPixel)
            }
        }

        return invertedBitmap
    }
    fun getFireBaseData(saved_finger: List<FloatArray>) {
        activity?.runOnUiThread{
            identifystatus.visibility = View.VISIBLE
            identifystatus.text = "MATCHING"
        }


        val reference =
            FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("data")
        reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val length = snapshot.childrenCount.toInt()
                    val x = snapshot.children
                    println(length)
                    val name = ArrayList<String>()
                    var answer_arr = ArrayList<ArrayList<Double>>()
                    for (i in x) {
                        var pred_array = ArrayList<ArrayList<Double>>()
                        i.key?.let { name.add(it) }
                        println(i.key)
                        for (j in i.children) {
                            var compare1 = ArrayList<Float>()
                            var compare2 = ArrayList<Float>()
                            var compare3 = ArrayList<Float>()
                            var compare4 = ArrayList<Float>()

                            for (k in j.child("7").children) {
                                k.getValue(Float::class.java)?.let { compare1.add(it) }
                            }
                            for (k in j.child("11").children) {
                                k.getValue(Float::class.java)?.let { compare2.add(it) }
                            }
                            for (k in j.child("15").children) {
                                k.getValue(Float::class.java)?.let { compare3.add(it) }
                            }
                            for (k in j.child("19").children) {
                                k.getValue(Float::class.java)?.let { compare4.add(it) }
                            }
                            val diff =
                                DoubleArray(compare1.size) { (saved_finger[0][it]).toDouble() - (compare1[it]).toDouble() }
                            var distance = (1 - sqrt(diff.map { it.pow(2.0) }.sum()))
                            val diff1 =
                                DoubleArray(compare2.size) { (saved_finger[1][it]).toDouble() - (compare2[it]).toDouble() }
                            var distance1 = (1 - sqrt(diff1.map { it.pow(2.0) }.sum()))
                            val diff2 =
                                DoubleArray(compare3.size) { (saved_finger[2][it]).toDouble() - (compare3[it]).toDouble() }
                            var distance2 = (1 - sqrt(diff2.map { it.pow(2.0) }.sum()))
                            val diff3 =
                                DoubleArray(compare4.size) { (saved_finger[3][it]).toDouble() - (compare4[it]).toDouble() }
                            var distance3 = (1 - sqrt(diff3.map { it.pow(2.0) }.sum()))

                            pred_array.add(
                                arrayListOf(
                                    distance,
                                    distance1,
                                    distance2,
                                    distance3
                                )
                            )
                        }
                        println(pred_array)

                        var sum1=0.0
                        var sum2=0.0
                        var sum3=0.0
                        var sum4=0.0

                        for(i in 0..(pred_array.size-1)) {
                            sum1+=pred_array[i][0]
                            sum2+=pred_array[i][1]
                            sum3+=pred_array[i][2]
                            sum4+=pred_array[i][3]
                        }
                        answer_arr.add(arrayListOf((sum1/(pred_array.size)), (sum2/(pred_array.size)), (sum3/(pred_array.size)) ,(sum4/(pred_array.size))))


                    }
                    var max=-1.0
                    var id=-1
                    println(answer_arr)
                    for(i in 0..answer_arr.size-1){
                        if(((answer_arr[i][0]*0.45+answer_arr[i][1]*0.4+answer_arr[i][2]*0.1+answer_arr[i][3]*0.05))>max){
                            max = ((answer_arr[i][0]*0.45+answer_arr[i][1]*0.4+answer_arr[i][2]*0.1+answer_arr[i][3]*0.05))
                            id = i
                        }
                    }
                    println(max)
                    println(id)
                    activity?.runOnUiThread{
                        if(max>0.5){

                            identifyname.visibility = View.VISIBLE
                            identifyname.text = name[id]
                            identifystatus.visibility = View.VISIBLE
                            identifystatus.text = "Score = "+max.toString()
                        }
                        else{
                            identifyname.visibility = View.VISIBLE
                            identifystatus.visibility = View.VISIBLE
                            identifystatus.text = "Score = "+max.toString()
                            identifyname.text = "Unknown Person"
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
    fun calculateFocusLevel(bitmap: Bitmap): Double {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        val laplacian = Mat()
        Imgproc.Laplacian(grayMat, laplacian, CV_64F)

        val laplacianVar = Mat()
        Core.multiply(laplacian, laplacian, laplacianVar)
        val focusMeasure = Core.mean(laplacianVar)

        return focusMeasure.`val`[0].coerceIn(0.0,100.0)
    }
    fun applyMedianBlur(image: Bitmap, kernelSize: Int): Bitmap {
        // Convert Bitmap to OpenCV Mat
        val mat = Mat(image.height, image.width, CvType.CV_8UC1)
        Utils.bitmapToMat(image, mat)

        // Apply median blur
        Imgproc.medianBlur(mat, mat, kernelSize)

        // Convert the modified Mat back to Bitmap
        val resultImage = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, resultImage)

        return resultImage
    }
    fun applyHistogramEqualization(image: Bitmap): Bitmap {
        // Convert Bitmap to OpenCV Mat
        val mat = Mat()
        Utils.bitmapToMat(image, mat)

        // Convert the image to grayscale
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)

        // Apply histogram equalization
        Imgproc.equalizeHist(mat, mat)

        // Convert the modified Mat back to Bitmap
        val resultImage = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, resultImage)

        return resultImage
    }
}

