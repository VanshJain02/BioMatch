package com.example.biomatch

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chaquo.python.Python
import com.example.biomatch.DataClass.fingerprint_data
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult
import com.example.biomatch.ml.ModelScatDwtharrSiameseV1
import com.google.firebase.database.*
import kotlinx.coroutines.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis
import java.text.SimpleDateFormat


class RegisterFingerprint_temp : AppCompatActivity() {

    private val cameraRequestCode = 42
    private lateinit var photoFile: File
    private val TAG = "Registration"
    private var imageSize = 200
    private var saved_finger= ArrayList<Deferred<FloatArray>>()

    var rootNode: FirebaseDatabase? = null
    var reference: DatabaseReference? = null
    lateinit var auto_phoneno: String

    lateinit var send_firebase: ArrayList<ArrayList<Deferred<FloatArray>>>

    var counter =0
    lateinit var registered_name_btn: AppCompatEditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_fingerprint)

        var count=0
        val sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE)
        auto_phoneno = sharedPreferences.getString("phoneno", "").toString()
        send_firebase = ArrayList<ArrayList<Deferred<FloatArray>>>()

        registered_name_btn = findViewById<AppCompatEditText>(R.id.register_name)
        val add_finger_btn = findViewById<AppCompatButton>(R.id.add_fingerprint)

        add_finger_btn.setOnClickListener{
            if(findViewById<AppCompatButton>(R.id.add_fingerprint).text == "Done"){
                val intent = Intent(this@RegisterFingerprint_temp, MainHome::class.java)
                startActivity(intent)
                finish()
            }
            else {
                var intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                photoFile = getPhotoFile(count.toString() + ".png")
                count += 1
                val fileProvider =
                    FileProvider.getUriForFile(this, "com.example.biomatch.fileprovider", photoFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)
                startActivityForResult(intent, cameraRequestCode)
            }
        }

    }

    private fun getPhotoFile(s: String): File {

        val storageDirectory =  getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(s,".jpg",storageDirectory)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == AppCompatActivity.RESULT_OK && allPermissionGranted()) {
            if (requestCode == cameraRequestCode || requestCode==100) {

                var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                findViewById<AppCompatButton>(R.id.add_fingerprint).isEnabled = false

                CoroutineScope(Dispatchers.Default).launch {


                    try {
                        var greymap: HashMap<Any, Bitmap> = hashMapOf(7 to bitmap, 11 to bitmap, 15 to bitmap, 19 to bitmap)
                        var handtype: String= "handtype"

                        val job = CoroutineScope(Dispatchers.IO).launch {
                            var t1=makeGray(bitmap)
                            greymap = t1.first
                            handtype = t1.second

                        }

                        runBlocking {
                            job.join()
                        }



                        Log.d("Registration",bitmap.width.toString()+"     "+bitmap.height.toString())

                        var py = Python.getInstance()
                        if (greymap != null) {

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
                                        if (findViewById<AppCompatButton>(R.id.add_fingerprint).text == "Add Fingerprint") {
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
                                            counter+=1

                                            runOnUiThread {
                                                findViewById<AppCompatButton>(R.id.add_fingerprint).isEnabled =
                                                    true
                                                if(counter>2) {
                                                findViewById<AppCompatButton>(R.id.add_fingerprint).text =
                                                    "Done"
                                                }
                                            }
                                        } else {
                                            if (!saved_finger.isEmpty()) {
                                                val diff =
                                                    DoubleArray(process1.await().size) { (saved_finger[0].await()[it]).toDouble() - (process1.await()[it]).toDouble() }
                                                var distance =
                                                    (1 - sqrt(diff.map { it.pow(2.0) }.sum()))
                                                val diff1 =
                                                    DoubleArray(process1.await().size) { (saved_finger[1].await()[it]).toDouble() - (process2.await()[it]).toDouble() }
                                                var distance1 =
                                                    (1 - sqrt(diff1.map { it.pow(2.0) }.sum()))
                                                val diff2 =
                                                    DoubleArray(process1.await().size) { (saved_finger[2].await()[it]).toDouble() - (process3.await()[it]).toDouble() }
                                                var distance2 =
                                                    (1 - sqrt(diff2.map { it.pow(2.0) }.sum()))
                                                val diff3 =
                                                    DoubleArray(process1.await().size) { (saved_finger[3].await()[it]).toDouble() - (process4.await()[it]).toDouble() }
                                                var distance3 =
                                                    (1 - sqrt(diff3.map { it.pow(2.0) }.sum()))
                                                println("Distance1: " + distance.toString())
                                                println("Distance2: " + distance1.toString())
                                                println("Distance3: " + distance2.toString())
                                                println("Distance4: " + distance3.toString())
                                                runOnUiThread {
                                                    findViewById<AppCompatButton>(R.id.add_fingerprint).isEnabled =
                                                        true
                                                }
                                            }
                                        }
                                    }

                                    println("debug: Total time elapsed: ${executionTime}")
                                }

                            }


                    }catch (e: Exception){
                        runOnUiThread {
                            Log.d("EXCEPTION",  e.toString())
                            Toast.makeText(baseContext, "Upload Both the Images", Toast.LENGTH_LONG)
                                .show()
                            findViewById<Button>(R.id.add_fingerprint).isEnabled = true

                        }

                    }

// Releases model resources if no longer used.
                }


            }
        }
        else{
            Log.d("IMAGE","Permission Not Granted")
        }


    }

    private suspend fun processImage(key: Int, greymap: HashMap<Any, Bitmap>): FloatArray = withContext(
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


                val inputFeature0 =
                    TensorBuffer.createFixedSize(intArrayOf(1, 200, 200), DataType.FLOAT32)
                var byteBuffer1: ByteBuffer =
                    ByteBuffer.allocateDirect(4 * imageSize * imageSize)
                byteBuffer1.order(ByteOrder.nativeOrder())
                var pixel = 0
                for (i in 0 until imageSize) {
                    for (j in 0 until imageSize) {
                        //                                    for (k in 0 until 3){
                        var vals = intValues[pixel++].toInt()// RGB
                        byteBuffer1.putFloat(
                            (vals * (1F / 255f).toDouble().toBigDecimal()
                                .setScale(6, BigDecimal.ROUND_HALF_UP).toFloat())
                        )
                    }
                }


//                                    val model = SiamesemodelEnh.newInstance(this@PAGE_Matching)  //Recent and best model of siamese enh with scattering2d with input image(96,96)
                val model = ModelScatDwtharrSiameseV1.newInstance(this@RegisterFingerprint_temp) //input_image(200,200)
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


    suspend fun makeGray(bitma: Bitmap) : Pair<HashMap<Any, Bitmap>,String> {
//        val bitmap = flipBitmapHorizontally(bitma)
        val bitmap = bitma
        var rotatebool = false
        if(bitma.width>bitma.height){
            rotatebool= true
        }
        var hands = Hands(this, HandsOptions.builder()
            .setStaticImageMode(true)
            .setMaxNumHands(2)
            .setRunOnGpu(true)
            .build()
        )
        val  job = CoroutineScope(Dispatchers.Default).launch {
            hands.send(bitmap)
        }
        job.join()
        var top1: Bitmap = bitmap

        var fingers: HashMap<Any, Bitmap> = hashMapOf(7 to bitmap,11 to bitmap,15 to bitmap,19 to bitmap)
        var handtype: String = "Left"
        Log.d(TAG,"SHAPE:"+bitmap.width+"\t"+bitmap.height)

        // Connects MediaPipe Hands solution to the user-defined HandsResultImageView.
        Log.d("IMAGE","Waiting for result")
        hands.setResultListener { handsResult: HandsResult? ->
            Log.d("IMAGE","Waiting for result")
            if (handsResult != null) {
                Log.d(TAG, (handsResult.multiHandedness()::class).toString())
                Log.d(TAG, handsResult.multiHandedness().get(0).label.toString())
                handtype = handsResult.multiHandedness().get(0).label.toString()
                //                var coords: HashMap<Any,Pair<Array<Double>,Array<Int>>> = hashMapOf(7 to Pair(arrayOf<Double>(-1.0), arrayOf<Int>(-1)),11 to Pair(arrayOf<Double>(-1.0),arrayOf<Int>(-1)),15 to Pair(arrayOf<Double>(-1.0),arrayOf<Int>(-1)),19 to Pair(arrayOf<Double>(-1.0),arrayOf<Int>(-1)))
                for(key in fingers.keys) {
                    Log.d("IMAGE", "Start Cropping")
                    var direction = 0
                    var start_point = arrayOf<kotlin.Double>(((handsResult.multiHandLandmarks().get(0).landmarkList.get((key as Int) + 1).x) * bitmap.width).toDouble(), (((handsResult.multiHandLandmarks().get(0).landmarkList.get((key as Int) + 1).y) * bitmap.height)).toDouble())
                    Log.d("MATCHING",key.toString()+ " startpt"+"\t"+start_point[0].toString()+"\t"+start_point[1].toString())


                    var end_point = arrayOf<kotlin.Double>(((handsResult.multiHandLandmarks().get(0).landmarkList.get((key as Int)).x) * bitmap.width).toDouble(), (((handsResult.multiHandLandmarks().get(0).landmarkList.get((key as Int)).y) * bitmap.height)).toDouble())
                    Log.d("MATCHING",key.toString()+ " endpt"+"\t"+end_point[0].toString()+"\t"+end_point[1].toString())

                    var m = (-end_point[1]+start_point[1])/(-end_point[0]+start_point[0]+0.000001)
                    var angle= atan(m) *180/Math.PI
                    var dist_pt = sqrt((end_point[0] - start_point[0]).pow(2.0) + (end_point[1] - start_point[1]).pow(2.0))
                    Log.d("MATCHING",key.toString()+ " distpt"+"\t"+dist_pt.toString())

                    var mid_point = arrayOf<kotlin.Double>(((start_point[0] + end_point[0]) / 2), ((start_point[1] + end_point[1]) / 2))
                    var axesy = (dist_pt * 1.6 / 2)
                    Log.d("MATCHING",key.toString()+ " axesy"+"\t"+axesy.toString())

                    var axesLength: Array<Int>
                    var half_length_diag = (dist_pt*2.5)/2

                    var palm_pointx = ((handsResult.multiHandLandmarks().get(0).landmarkList.get((9)).x )* bitmap.width).toInt()
                    if(abs(angle) <45) {
                        if(palm_pointx < mid_point[0]) {
                            direction = 1
                        }
                        else {
                            direction = 2
                        }
                        axesLength = arrayOf<Int>(
                            (abs(axesy/2) + abs(axesy/6)).toInt(),
                            (abs(axesy) + abs(axesy / 10)).toInt()
                        )
                    }
                    else{
                        axesLength = arrayOf<Int>(
                            (abs(axesy) + abs(axesy / 10)).toInt(),
                            (abs(axesy / 2) + abs(axesy/6)).toInt()
                        )
                    }
                    Log.d("MATCHING",key.toString()+ "\t"+axesLength[0].toString()+"\t"+axesLength[1].toString())

                    top1 = Bitmap.createBitmap(bitmap, (mid_point[0] - axesLength[1]).toInt(), (mid_point[1] - axesLength[0]*1.2).toInt(), 2 * axesLength[1].toInt(), (3.2 * axesLength[0]-half_length_diag).toInt())

                    if(rotatebool){
                        top1 = Bitmap.createBitmap(bitmap, (mid_point[0] - axesLength[1]*1.2).toInt(), (mid_point[1] - axesLength[0]*0.9).toInt(), (1.6 * axesLength[1]).toInt(), (1.8* axesLength[0]).toInt())
                    }
                    Log.d("MATCHING",key.toString()+ "\t"+top1.getWidth().toString()+"\t"+ top1.getHeight())
                    if(abs(angle) <45) {
                        val matrix = Matrix()
                        if(direction == 1) {matrix.postRotate(270F)}
                        if(direction == 2) {matrix.postRotate(90F)}
                        top1 = Bitmap.createBitmap(
                            top1,
                            0,
                            0,
                            top1.getWidth(),
                            top1.getHeight(),
                            matrix,
                            true
                        )
                    }
                    val tmptop = flipBitmapHorizontally(top1)

                    fingers[key]=tmptop
                }

            }
            else{
                Log.d("IMAGE", "Hands error")
            }
        }
        hands.setErrorListener { message: String, e: RuntimeException? ->
            Log.d(
                "IMAGE",
                "MediaPipe Hands error"
            )
        }
        Log.d("IMAGE","MAKE GRAY PROCESSED")
        delay(2000)
        return Pair(first = fingers, second = handtype)
    }


    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }
    override fun onDestroy() {
        super.onDestroy()
    }
    private fun getStringImage(grayBitmap: Bitmap?): String? {
        var baos= ByteArrayOutputStream()
        grayBitmap?.compress(Bitmap.CompressFormat.PNG,100,baos)
        var imgByte = baos.toByteArray()
        var encodedImg = android.util.Base64.encodeToString(imgByte,android.util.Base64.DEFAULT)
        return encodedImg
    }
    fun flipBitmapHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
    }

}