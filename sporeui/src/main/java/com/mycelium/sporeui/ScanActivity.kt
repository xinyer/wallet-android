package com.mycelium.sporeui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.CaptureActivity
import com.google.zxing.client.android.Intents
import org.parceler.Parcel
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.support.v4.app.ActivityCompat


@Parcel
class ScanActivity : Activity() {

    public val SCANNER_RESULT_CODE = 0
    public val PERMISSIONS_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA),
                    PERMISSIONS_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        } else {
            finish()
        }
    }

    fun startScanning() {
        val intent = Intent(this, CaptureActivity::class.java)
        intent.putExtra(Intents.Scan.MODE, Intents.Scan.QR_CODE_MODE)
        startActivityForResult(intent, SCANNER_RESULT_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_CANCELED) {
            setResult(resultCode)
            finish()
            return
        }

        if (requestCode != SCANNER_RESULT_CODE) {
            setResult(resultCode)
            finish()
            return
        }

        val resultFormat = intent?.getStringExtra(Intents.Scan.RESULT_FORMAT)
        if (resultFormat != BarcodeFormat.QR_CODE.toString()) {
            setResult(resultCode)
            finish()
            return
        }

        var content = intent?.getStringExtra(Intents.Scan.RESULT)?.trim()
        // Get rid of any UTF-8 BOM marker. Those should not be present, but might have slipped in nonetheless.
        if (content != null && content.length != 0 && content[0] == '\uFEFF') content = content.substring(1)
        val intent = Intent()
        intent.putExtra("RESULT", content)
        setResult(resultCode, intent)
        finish()

    }
}