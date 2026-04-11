package com.example.cookgpt

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.example.cookgpt.data.Nutrient
import com.example.cookgpt.util.WaveView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

class WellnessActivity : AppCompatActivity() {

    private lateinit var tvCaloriesValue: TextView
    private lateinit var tvProteinValue: TextView
    private lateinit var tvCarbsValue: TextView
    private lateinit var tvWaterCount: TextView
    private lateinit var tvBmiValue: TextView
    private lateinit var tvBmiStatus: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvHeight: TextView
    private lateinit var waveView: WaveView

    private lateinit var pbCalories: ProgressBar
    private lateinit var pbProtein: ProgressBar
    private lateinit var pbCarbs: ProgressBar

    private lateinit var lineChart: LineChart
    private lateinit var cardAnalytics: MaterialCardView
    private lateinit var btnChartFilter: MaterialButton

    private var currentWater = 0
    private val waterGoal = 8
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private lateinit var database: DatabaseReference
    private var currentFilter = "Weekly"
    
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wellness)

        // Bind Views
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        tvCaloriesValue = findViewById(R.id.tvCaloriesValue)
        tvProteinValue = findViewById(R.id.tvProteinValue)
        tvCarbsValue = findViewById(R.id.tvCarbsValue)
        tvWaterCount = findViewById(R.id.tvWaterCount)
        tvBmiValue = findViewById(R.id.tvBmiValue)
        tvBmiStatus = findViewById(R.id.tvBmiStatus)
        tvWeight = findViewById(R.id.tvWeight)
        tvHeight = findViewById(R.id.tvHeight)
        waveView = findViewById(R.id.waveView)

        pbCalories = findViewById(R.id.pbCalories)
        pbProtein = findViewById(R.id.pbProtein)
        pbCarbs = findViewById(R.id.pbCarbs)

        lineChart = findViewById(R.id.lineChart)
        cardAnalytics = findViewById(R.id.cardAnalytics)
        btnChartFilter = findViewById(R.id.btnChartFilter)

        setupLineChart()

        findViewById<MaterialButton>(R.id.btnViewAnalytics).setOnClickListener {
            if (cardAnalytics.visibility == View.GONE) {
                cardAnalytics.visibility = View.VISIBLE
                btnChartFilter.text = "Weekly"
                loadWeeklyData()
            } else {
                cardAnalytics.visibility = View.GONE
            }
        }

        btnChartFilter.setOnClickListener { showFilterMenu() }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            database = FirebaseDatabase.getInstance().reference.child("users").child(userId)
            loadMacrosAndWater()
        }

        loadBodyMetrics()

        findViewById<ImageButton>(R.id.btnWaterPlus).setOnClickListener {
            if (currentWater < waterGoal) {
                currentWater++
                updateWaterUI()
                userId?.let { database.child("water").child(today).setValue(currentWater) }
            }
        }
    }

    private fun setupLineChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateX(1000)
        }
    }

    private fun showFilterMenu() {
        val popup = PopupMenu(this, btnChartFilter)
        popup.menu.add("Weekly")
        popup.menu.add("Monthly")
        popup.menu.add("Yearly")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Weekly" -> {
                    currentFilter = "Weekly"
                    btnChartFilter.text = "Weekly"
                    loadWeeklyData()
                }
                "Monthly" -> {
                    showMonthYearPicker(isMonth = true)
                }
                "Yearly" -> {
                    showMonthYearPicker(isMonth = false)
                }
            }
            true
        }
        popup.show()
    }

    private fun showMonthYearPicker(isMonth: Boolean) {
        val calendar = Calendar.getInstance()
        if (isMonth) {
            val months = arrayOf("January", "February", "March", "April", "May", "June", 
                                "July", "August", "September", "October", "November", "December")
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select Month")
            builder.setItems(months) { _, which ->
                selectedMonth = which
                currentFilter = "Monthly"
                btnChartFilter.text = "Monthly: ${months[which]}"
                loadMonthlyData(selectedMonth, selectedYear)
            }
            builder.show()
        } else {
            val years = mutableListOf<String>()
            val currentYear = calendar.get(Calendar.YEAR)
            for (i in 0..5) {
                years.add((currentYear - i).toString())
            }
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select Year")
            builder.setItems(years.toTypedArray()) { _, which ->
                selectedYear = years[which].toInt()
                currentFilter = "Yearly"
                btnChartFilter.text = "Yearly: $selectedYear"
                loadYearlyData(selectedYear)
            }
            builder.show()
        }
    }

    private fun fetchDataInRange(start: String, end: String, callback: (DataSnapshot) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("users").child(userId).child("macros")
            .orderByKey().startAt(start).endAt(end)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadWeeklyData() {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.SUNDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startOfWeek = dateFormat.format(calendar.time)
        
        val weekCal = calendar.clone() as Calendar
        weekCal.add(Calendar.DAY_OF_YEAR, 6)
        val endOfWeek = dateFormat.format(weekCal.time)

        fetchDataInRange(startOfWeek, endOfWeek) { snapshot ->
            val calEntries = mutableListOf<Entry>()
            val proEntries = mutableListOf<Entry>()
            val carbEntries = mutableListOf<Entry>()
            val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            
            val dataMap = mutableMapOf<String, DataSnapshot>()
            for (child in snapshot.children) {
                dataMap[child.key ?: ""] = child
            }

            val tempCal = calendar.clone() as Calendar
            for (i in 0..6) {
                val dateStr = dateFormat.format(tempCal.time)
                val dayData = dataMap[dateStr]
                
                val cals = dayData?.child("calories")?.getValue(Int::class.java) ?: 0
                val pro = dayData?.child("protein")?.getValue(Int::class.java) ?: 0
                val carb = dayData?.child("carbs")?.getValue(Int::class.java) ?: 0
                
                calEntries.add(Entry(i.toFloat(), cals.toFloat()))
                proEntries.add(Entry(i.toFloat(), pro.toFloat()))
                carbEntries.add(Entry(i.toFloat(), carb.toFloat()))
                tempCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            updateChart(calEntries, proEntries, carbEntries, labels)
        }
    }

    private fun loadMonthlyData(month: Int, year: Int) {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startOfMonth = dateFormat.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val endOfMonth = dateFormat.format(cal.time)

        fetchDataInRange(startOfMonth, endOfMonth) { snapshot ->
            val weekSums = Array(4) { FloatArray(3) }
            val weekCounts = IntArray(4)

            for (child in snapshot.children) {
                val dateStr = child.key ?: ""
                val dayOfMonth = dateStr.takeLast(2).toInt()
                val weekIndex = when {
                    dayOfMonth <= 7 -> 0
                    dayOfMonth <= 14 -> 1
                    dayOfMonth <= 21 -> 2
                    else -> 3
                }
                
                weekSums[weekIndex][0] += (child.child("calories").getValue(Int::class.java) ?: 0).toFloat()
                weekSums[weekIndex][1] += (child.child("protein").getValue(Int::class.java) ?: 0).toFloat()
                weekSums[weekIndex][2] += (child.child("carbs").getValue(Int::class.java) ?: 0).toFloat()
                weekCounts[weekIndex]++
            }

            val calEntries = mutableListOf<Entry>()
            val proEntries = mutableListOf<Entry>()
            val carbEntries = mutableListOf<Entry>()
            val labels = listOf("Week 1", "Week 2", "Week 3", "Week 4")

            for (i in 0..3) {
                val div = if (weekCounts[i] > 0) weekCounts[i].toFloat() else 1f
                calEntries.add(Entry(i.toFloat(), weekSums[i][0] / div))
                proEntries.add(Entry(i.toFloat(), weekSums[i][1] / div))
                carbEntries.add(Entry(i.toFloat(), weekSums[i][2] / div))
            }
            updateChart(calEntries, proEntries, carbEntries, labels)
        }
    }

    private fun loadYearlyData(year: Int) {
        val startOfYear = "$year-01-01"
        val endOfYear = "$year-12-31"

        fetchDataInRange(startOfYear, endOfYear) { snapshot ->
            val monthSums = Array(12) { FloatArray(3) }
            val monthCounts = IntArray(12)

            for (child in snapshot.children) {
                val dateStr = child.key ?: ""
                if (dateStr.length >= 7) {
                    val monthPart = dateStr.substring(5, 7).toInt() - 1
                    if (monthPart in 0..11) {
                        monthSums[monthPart][0] += (child.child("calories").getValue(Int::class.java) ?: 0).toFloat()
                        monthSums[monthPart][1] += (child.child("protein").getValue(Int::class.java) ?: 0).toFloat()
                        monthSums[monthPart][2] += (child.child("carbs").getValue(Int::class.java) ?: 0).toFloat()
                        monthCounts[monthPart]++
                    }
                }
            }

            val calEntries = mutableListOf<Entry>()
            val proEntries = mutableListOf<Entry>()
            val carbEntries = mutableListOf<Entry>()
            val labels = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

            for (i in 0..11) {
                val div = if (monthCounts[i] > 0) monthCounts[i].toFloat() else 1f
                calEntries.add(Entry(i.toFloat(), monthSums[i][0] / div))
                proEntries.add(Entry(i.toFloat(), monthSums[i][1] / div))
                carbEntries.add(Entry(i.toFloat(), monthSums[i][2] / div))
            }
            updateChart(calEntries, proEntries, carbEntries, labels)
        }
    }

    private fun updateChart(calEntries: List<Entry>, proEntries: List<Entry>, carbEntries: List<Entry>, labels: List<String>) {
        val calDataSet = LineDataSet(calEntries, "Calories").apply {
            color = Color.parseColor("#10B981")
            setCircleColor(Color.parseColor("#10B981"))
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#10B981")
            fillAlpha = 30
        }

        val proDataSet = LineDataSet(proEntries, "Protein").apply {
            color = Color.parseColor("#3B82F6")
            setCircleColor(Color.parseColor("#3B82F6"))
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val carbDataSet = LineDataSet(carbEntries, "Carbs").apply {
            color = Color.parseColor("#F97316")
            setCircleColor(Color.parseColor("#F97316"))
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx >= 0 && idx < labels.size) labels[idx] else ""
            }
        }
        lineChart.xAxis.labelCount = labels.size

        lineChart.data = LineData(listOf(calDataSet, proDataSet, carbDataSet))
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    private fun loadMacrosAndWater() {
        database.child("water").child(today).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                currentWater = snapshot.getValue(Int::class.java) ?: 0
                updateWaterUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        database.child("macros").child(today).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cals = snapshot.child("calories").getValue(Int::class.java) ?: 0
                val pro = snapshot.child("protein").getValue(Int::class.java) ?: 0
                val carb = snapshot.child("carbs").getValue(Int::class.java) ?: 0

                tvCaloriesValue.text = "$cals / 2000 kcal"
                tvProteinValue.text = "$pro / 150g"
                tvCarbsValue.text = "$carb / 250g"

                pbCalories.progress = cals
                pbProtein.progress = pro
                pbCarbs.progress = carb
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadBodyMetrics() {
        val prefs = getSharedPreferences("user_data", Context.MODE_PRIVATE)
        val weightStr = prefs.getString("weight", "0") ?: "0"
        val heightStr = prefs.getString("height", "0") ?: "0"

        val weight = weightStr.toFloatOrNull() ?: 0f
        val height = heightStr.toFloatOrNull() ?: 0f

        tvWeight.text = "$weight kg"
        tvHeight.text = "$height cm"

        if (weight > 0 && height > 0) {
            val bmi = weight / (height / 100).pow(2)
            val roundedBmi = (bmi * 10).roundToInt() / 10.0
            tvBmiValue.text = roundedBmi.toString()

            val status = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25.0 -> "Normal Weight"
                bmi < 30.0 -> "Overweight"
                else -> "Obese"
            }
            tvBmiStatus.text = status
        }
    }

    private fun updateWaterUI() {
        tvWaterCount.text = "You've had $currentWater of $waterGoal glasses"
        waveView.setProgress(currentWater.toFloat() / waterGoal.toFloat())
    }
}
