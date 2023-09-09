package com.example.biomatch.fragment

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import com.example.biomatch.R
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.chaquo.python.Python
import com.example.biomatch.Constants
import com.example.biomatch.ml.ModelScatDwtharrSiameseV1
import com.example.biomatch.zcustomcalendar.CustomCalendar
import com.example.biomatch.zcustomcalendar.OnDateSelectedListener
import com.example.biomatch.zcustomcalendar.Property
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult
import kotlinx.coroutines.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis


class HomeFragment : Fragment() {

    var count=0
    private val cameraRequestCode = 42
    private lateinit var photoFile: File
    private val TAG = "Home Fragment"
    private var imageSize = 200

    private var saved_finger= ArrayList<Deferred<FloatArray>>()

    lateinit var score: TextView
    lateinit var score_value: TextView
    var progressValue = 0

    var rootNode: FirebaseDatabase? = null

    companion object{
        lateinit var identifyprogressBar: ProgressBar

    }
    var calendar: Calendar = Calendar.getInstance()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view =  inflater.inflate(R.layout.fragment_home, container, false)

        var customCalendar = view.findViewById<CustomCalendar>(R.id.custom_calender)
        identifyprogressBar = view.findViewById(R.id.progress_bar)

        val descHashMap: HashMap<Any, Property> = HashMap()
        val defaultProperty = Property()

        defaultProperty.layoutResource = R.layout.default_view
        defaultProperty.dateTextViewResource = R.id.text_view
        descHashMap["default"] = defaultProperty
        val currentProperty = Property()
        currentProperty.layoutResource = R.layout.current_view
        currentProperty.dateTextViewResource = R.id.text_view
        descHashMap["current"] = currentProperty
        val presentProperty = Property()
        presentProperty.layoutResource = R.layout.present_view
        presentProperty.dateTextViewResource = R.id.text_view
        descHashMap["present"] = presentProperty

        val absentProperty = Property()
        absentProperty.layoutResource = R.layout.absent_view
        absentProperty.dateTextViewResource =R.id.text_view
        descHashMap["absent"] = absentProperty
        customCalendar.setMapDescToProp(descHashMap)
        var dateHashmap: HashMap<Int, Any> = HashMap()
//        dateHashmap[calendar.get(Calendar.DAY_OF_MONTH)] = "current"
//        dateHashmap[1] = "present"
//        dateHashmap[2] = "absent"
//        dateHashmap[3] = "present"
//        dateHashmap[4] = "absent"
//        dateHashmap[20] = "present"
//        dateHashmap[30] = "absent"
        dateHashmap = calender_attendance()

        customCalendar.setDate(calendar, dateHashmap)

        customCalendar.setOnDateSelectedListener(OnDateSelectedListener { view, selectedDate, desc -> // get string date
            val sDate: String = (selectedDate[Calendar.DAY_OF_MONTH].toString() + "/" + (selectedDate[Calendar.MONTH] + 1).toString() + "/" + selectedDate[Calendar.YEAR].toString())

            // display date in toast
            Toast.makeText(
                activity,
                sDate,
                Toast.LENGTH_SHORT
            ).show()
        })


        score = view.findViewById<TextView>(R.id.identify_score)
        score_value = view.findViewById<TextView>(R.id.identify_score_value)
        identifyprogressBar.max = 1000
        identifyprogressBar.progress = 0







        return view
    }
    private fun calender_attendance(): HashMap<Int, Any>{
        var dateHashmap: HashMap<Int, Any> = HashMap()
        calendar = Calendar.getInstance()
        println(calendar.get(Calendar.MONTH))
        println(calendar.get(Calendar.DAY_OF_MONTH))
        dateHashmap[calendar.get(Calendar.DAY_OF_MONTH)] = "current"
        val firebase = FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")
        val reference = firebase.getReference("Attendance").child("Vansh").child(calendar.get(Calendar.YEAR).toString()).child((calendar.get(Calendar.MONTH)+1).toString())


        reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val length = snapshot.childrenCount.toInt()
                    val x = snapshot.children
                    println(length)
                    val name = ArrayList<String>()
                    for (i in x) {
                        i.key?.let { name.add(it) }
                        println(i.key)
                        dateHashmap[i.key!!.toInt()] = "present"
                        }
                    view?.findViewById<CustomCalendar>(R.id.custom_calender)?.setDate(calendar, dateHashmap)

                }

                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
        println(dateHashmap)
        return dateHashmap
    }

    public fun identify_finger(){
        var intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoFile = getPhotoFile(count.toString() + ".png")
        count += 1
        val fileProvider =
            activity?.let { FileProvider.getUriForFile(it, "com.example.biomatch.fileprovider", photoFile) }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)
        startActivityForResult(intent, cameraRequestCode)
    }

    private fun getPhotoFile(s: String): File {
        val storageDirectory =  activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(s,".jpg",storageDirectory)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == AppCompatActivity.RESULT_OK && allPermissionGranted()) {
            if (requestCode == cameraRequestCode || requestCode==100) {
                identifyprogressBar.visibility=View.VISIBLE
                identifyprogressBar.progress=0
                identifyprogressBar.max = 1000
                progressValue += 100
                ObjectAnimator.ofInt(identifyprogressBar, "progress", progressValue).setDuration(10000)
                    .start()
//                    ObjectAnimator.ofInt(progressBarPercent, "text", (progressValue/10))
//                        .setDuration(10000).start()


                var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)


                CoroutineScope(Dispatchers.Default).launch {


                    try {
                        var greymap: HashMap<Any, Bitmap> = hashMapOf(7 to bitmap, 11 to bitmap, 15 to bitmap, 19 to bitmap)
                        var handtype: String= "handtype"

                        val job = CoroutineScope(Dispatchers.IO).launch {
                            var t1=makeGray(bitmap)
                            greymap = t1.first
                            handtype = t1.second

                        }
                        activity?.runOnUiThread {
                            progressValue += 600
                            ObjectAnimator.ofInt(identifyprogressBar, "progress", progressValue)
                                .setDuration(20000).start()
//                            ObjectAnimator.ofInt(progressBarPercent, "text", (progressValue/10))
//                                .setDuration(20000).start()
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
                                    saved_finger.add(process1)
                                    saved_finger.add(process2)
                                    saved_finger.add(process3)
                                    saved_finger.add(process4)
                                    getFireBaseData(saved_finger.awaitAll())

//                                    if (!saved_finger.isEmpty()) {
//                                        val diff =
//                                            DoubleArray(process1.await().size) { (saved_finger[0].await()[it]).toDouble() - (process1.await()[it]).toDouble() }
//                                        var distance =
//                                            (1 - sqrt(diff.map { it.pow(2.0) }.sum()))
//                                        val diff1 =
//                                            DoubleArray(process1.await().size) { (saved_finger[1].await()[it]).toDouble() - (process2.await()[it]).toDouble() }
//                                        var distance1 =
//                                            (1 - sqrt(diff1.map { it.pow(2.0) }.sum()))
//                                        val diff2 =
//                                            DoubleArray(process1.await().size) { (saved_finger[2].await()[it]).toDouble() - (process3.await()[it]).toDouble() }
//                                        var distance2 =
//                                            (1 - sqrt(diff2.map { it.pow(2.0) }.sum()))
//                                        val diff3 =
//                                            DoubleArray(process1.await().size) { (saved_finger[3].await()[it]).toDouble() - (process4.await()[it]).toDouble() }
//                                        var distance3 =
//                                            (1 - sqrt(diff3.map { it.pow(2.0) }.sum()))
//                                        println("Distance1: " + distance.toString())
//                                        println("Distance2: " + distance1.toString())
//                                        println("Distance3: " + distance2.toString())
//                                        println("Distance4: " + distance3.toString())
//                                        runOnUiThread {
//                                            findViewById<AppCompatButton>(R.id.add_fingerprint).isEnabled =
//                                                true
//                                        }
//                                    }
                                }
                                println("debug: Total time elapsed: ${executionTime}")
                            }

                        }


                    }catch (e: Exception){
                        activity?.runOnUiThread {
                            Log.d("EXCEPTION",  e.toString())
                            Toast.makeText(activity, "Upload Both the Images", Toast.LENGTH_LONG)
                                .show()
                            identifyprogressBar.visibility=View.INVISIBLE

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
                val model =
                    activity?.let { ModelScatDwtharrSiameseV1.newInstance(it) } //input_image(200,200)
                // Creates inputs for reference.
                // Creates inputs for reference.
                Log.d("", inputFeature0.toString())
                inputFeature0.loadBuffer(byteBuffer1)
                // Runs model inference and gets result.
                val outputs = model!!.process(inputFeature0)
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
        var hands = Hands(activity, HandsOptions.builder()
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
            activity?.let { it1 ->
                ContextCompat.checkSelfPermission(
                    it1, it
                )
            } == PackageManager.PERMISSION_GRANTED
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

    fun getFireBaseData(saved_finger: List<FloatArray>) {
        activity?.runOnUiThread {
            progressValue = 950
            ObjectAnimator.ofInt(identifyprogressBar, "progress", progressValue).setDuration(2000).start()

//        ObjectAnimator.ofInt(progressBarPercent, "text", (progressValue/10))
//            .setDuration(2000).start()
        }

        val firebase = FirebaseDatabase.getInstance("https://biomatch-96b5e-default-rtdb.asia-southeast1.firebasedatabase.app")
        val reference = firebase.getReference("data")
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
                        progressValue=1000
                        identifyprogressBar.progress = identifyprogressBar.max
//                        ObjectAnimator.ofInt(progressBarPercent, "text", (progressValue/10))
//                            .setDuration(200).start()
                        if(max>0.25){

                            score.visibility = View.VISIBLE
                            score.text = name[id]
                            score_value.visibility = View.VISIBLE
                            score_value.text =   (DecimalFormat("###.##").format(max * 100)).toString()
                            val dialogBinding= layoutInflater.inflate(R.layout.confirmation_matching_dialog,null)
                            val dialog = Dialog(activity!!)
                            dialogBinding.findViewById<TextView>(R.id.identified_person).text = name[id]
                            dialog.setContentView(dialogBinding)
                            dialog.setCancelable(false)
                            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                            dialog.show()


                            val yesbtn = dialogBinding.findViewById<Button>(R.id.confirmation_matching_btn_yes)
                            val nobtn = dialogBinding.findViewById<Button>(R.id.confirmation_matching_btn_no)
                            yesbtn.setOnClickListener{
//                                var map = hashMapOf<String,Int>("Attendance" to calendar.get(Calendar.DAY_OF_MONTH))
//                                firebase.collection("data").document("Attendance").set(map).addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully written!") }
//                                    .addOnFailureListener { e -> Log.w(TAG, "Error writing document", e) }
                                val reference1 = firebase!!.getReference("Attendance")
                                reference1.child(name[id]).child(calendar.get(Calendar.YEAR).toString() +"/"+(calendar.get(Calendar.MONTH)+1).toString()+"/"+calendar.get(Calendar.DAY_OF_MONTH).toString()).setValue("Yes")
                                Toast.makeText(activity,"Attendance Marked",Toast.LENGTH_SHORT).show()


                                dialog.dismiss()
                                identifyprogressBar.visibility=View.INVISIBLE
                                progressValue=0
                                score.visibility = View.INVISIBLE
                                score_value.visibility = View.INVISIBLE
                            }
                            nobtn.setOnClickListener{
//                                var ans=0
//                                if((final_answer.toInt())==0){
//                                    ans = 1
//                                }
//                                var map = hashMapOf<String,Float>("real" to ans.toFloat(),"pred" to final_answer.toFloat(),"1" to prediction[0],"2" to prediction[1] ,"3" to prediction[2],"4" to prediction[3])
//                                firebase.collection("data").document(outputUri.name).set(map).addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully written!") }
//                                    .addOnFailureListener { e -> Log.w(TAG, "Error writing document", e) }

                                dialog.dismiss()
                                identifyprogressBar.visibility=View.INVISIBLE
                                progressValue=0
                                score.visibility = View.INVISIBLE
                                score_value.visibility = View.INVISIBLE
                            }
                        }
                        else{
                            score.visibility = View.VISIBLE
                            score_value.visibility = View.VISIBLE
                            score_value.text = (DecimalFormat("###.##").format(max * 100)).toString()
                            score.text = "Unknown Person"
                            val dialogBinding= layoutInflater.inflate(R.layout.confirmation_matching_dialog,null)
                            val dialog = Dialog(activity!!)
                            dialogBinding.findViewById<TextView>(R.id.dialogbox_text1).text = "Could Not Identify Person"
                            dialogBinding.findViewById<TextView>(R.id.dialogbox_text2).text = "Want To Try Again?"
                            dialog.setContentView(dialogBinding)
                            dialog.setCancelable(false)
                            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                            dialog.show()


                            val yesbtn = dialogBinding.findViewById<Button>(R.id.confirmation_matching_btn_yes)
                            val nobtn = dialogBinding.findViewById<Button>(R.id.confirmation_matching_btn_no)
                            yesbtn.setOnClickListener{
                                photoFile = File("")
                                identify_finger()
                                dialog.dismiss()
                                identifyprogressBar.visibility=View.INVISIBLE
                                progressValue=0
                                score.visibility = View.INVISIBLE
                                score_value.visibility = View.INVISIBLE
                            }
                            nobtn.setOnClickListener{
                                dialog.dismiss()
                                identifyprogressBar.visibility=View.INVISIBLE
                                progressValue=0
                                score.visibility = View.INVISIBLE
                                score_value.visibility = View.INVISIBLE
                            }
                        }




                    }



                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


}