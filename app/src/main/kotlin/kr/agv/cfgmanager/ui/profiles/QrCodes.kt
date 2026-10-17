package kr.agv.cfgmanager.ui.profiles

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/** 프로필 QR 그림. 크기는 픽셀. 흰 바탕은 [kr.agv.cfgmanager.ui.theme.AgvQrColors] 을 쓰는 화면이 깐다. */
fun profileQrBitmap(text: String, sizePx: Int): Bitmap =
    BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
