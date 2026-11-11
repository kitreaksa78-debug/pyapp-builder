package com.example.deviceinfoapp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvDeviceInfo = findViewById<TextView>(R.id.tvDeviceInfo)
        tvDeviceInfo.text = getFullDeviceInfo()
    }

    private fun getFullDeviceInfo(): String {
        val sb = StringBuilder()

        // 1. Hardware & System Info
        sb.append("=== 1. ព័ត៌មានប្រព័ន្ធ (SYSTEM & HARDWARE) ===\n")
        sb.append("• ម៉ាក (Brand): ").append(Build.BRAND).append("\n")
        sb.append("• ម៉ូឌែល (Model): ").append(Build.MODEL).append("\n")
        sb.append("• ក្រុមហ៊ុនផលិត (Manufacturer): ").append(Build.MANUFACTURER).append("\n")
        sb.append("• ឧបករណ៍ (Device/Board): ").append(Build.DEVICE).append(" / ").append(Build.BOARD).append("\n")
        sb.append("• Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("• CPU Architecture (ABI): ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n\n")

        // 2. Display Info
        sb.append("=== 2. អេក្រង់ (DISPLAY & RESOLUTION) ===\n")
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        sb.append("• ភាពច្បាស់ (Resolution): ").append(metrics.widthPixels).append(" x ").append(metrics.heightPixels).append(" px\n")
        sb.append("• ភាពក្រាស់ (DPI Density): ").append(metrics.densityDpi).append(" dpi\n\n")

        // 3. Storage Info
        sb.append("=== 3. អង្គចងចាំ (STORAGE) ===\n")
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalGB = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
            val availableGB = (availableBlocks * blockSize) / (1024 * 1024 * 1024)

            sb.append("• ទំហំសរុប (Total Storage): ").append(totalGB).append(" GB\n")
            sb.append("• ទំហំទំនេរ (Available Storage): ").append(availableGB).append(" GB\n\n")
        } catch (e: Exception) {
            sb.append("• ពុំអាចអានព័ត៌មាន Storage បាន\n\n")
        }

        // 4. Battery Info
        sb.append("=== 4. ថាមពលថ្ម (BATTERY) ===\n")
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        sb.append("• កម្រិតថ្ម (Percentage): ").append(batteryPct).append("%\n")
        sb.append("• ស្ថានភាពសាក (Status): ").append(if (isCharging) "កំពុងសាកថ្ម (Charging)" else "មិនបានសាក (Discharging)").append("\n\n")

        // 5. Sensors Info
        sb.append("=== 5. សែនស័រដែលមាន (SENSORS) ===\n")
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensorList: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
        sb.append("• ចំនួន Sensor សរុប: ").append(sensorList.size).append("\n")
        for ((index, sensor) in sensorList.take(8).withIndex()) {
            sb.append("  ${index + 1}. ").append(sensor.name).append("\n")
        }
        if (sensorList.size > 8) {
            sb.append("  ... និង ${sensorList.size - 8} ផ្សេងទៀត\n")
        }

        return sb.toString()
    }
}
