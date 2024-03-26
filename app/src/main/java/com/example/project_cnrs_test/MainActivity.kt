@file:Suppress(
    "PrivatePropertyName",
    "PrivatePropertyName",
    "PrivatePropertyName",
    "PrivatePropertyName"
)

package com.example.project_cnrs_test

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition.getClient
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var firebaseVisionImage: InputImage
    private lateinit var firebaseVisionTextRecognizer: TextRecognizer
    private lateinit var varImageButton: Button
    private lateinit var varOCRButton: Button
    private lateinit var varEnregistrerTexte: Button
    private lateinit var varImagePreview: ImageView
    private lateinit var varTextView: TextView
    private lateinit var photoFile: File
    private lateinit var varTakenPicture: Uri
    private lateinit var varUriCroppedTakenPicture: Uri
    private lateinit var varBitmapCroppedTakenPicture: Bitmap
    private val FILE_NAME = "photo.png"
    private val REQUEST_CODE = 42
    private val PERMISSION_ALL = 1
    private val PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        varImagePreview = findViewById(R.id.capture_image_preview)

        varTextView = findViewById(R.id.text_resultat)

        varOCRButton = findViewById(R.id.button_OCR)
        varOCRButton.isEnabled = false

        varImageButton = findViewById(R.id.button_image)
        varImageButton.isEnabled = false

        varEnregistrerTexte = findViewById(R.id.button_save_text)
        varEnregistrerTexte.isEnabled = false

        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
            toast("Permission Denied")
        } else {
            varImageButton.isEnabled = true
            setCaptureButton()
            toast("Permission Already Given")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            openApplicationSettings()
            toast("Permission Denied. Go To Setting And Enable It")
        } else {
            varImageButton.isEnabled = true
            setCaptureButton()
            toast("Permission Granted")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            //got image from camera now crop it
            CropImage.activity(varTakenPicture)
                     .setGuidelines(CropImageView.Guidelines.ON)
                     .start(this)
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            //got cropped image
            val result = CropImage.getActivityResult(data)
            varUriCroppedTakenPicture = result?.uriContent!!
            varImagePreview.setImageURI(varUriCroppedTakenPicture)
        }
    }

    override fun onResume() {
        super.onResume()

        if (hasImage(varImagePreview)) {
            varOCRButton.isEnabled = true
            setOCRButton()
        }
    }

    private fun openApplicationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun hasPermissions(context: Context, vararg permissions: String): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasImage(view: ImageView): Boolean {
        val drawable = view.drawable
        var hasImage = drawable != null
        if (hasImage && drawable is BitmapDrawable) {
            hasImage = drawable.bitmap != null
        }
        return hasImage
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun setCaptureButton() {
        varImageButton.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                photoFile = getPhotoFile(FILE_NAME)

                varTakenPicture = FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".provider",
                    photoFile
                )
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, varTakenPicture)
                if (takePictureIntent.resolveActivity(this.packageManager) != null)
                    try {
                        startActivityForResult(takePictureIntent, REQUEST_CODE)
                    } catch (e: ActivityNotFoundException) {
                        Log.e("Error : ", "$e")
                    }
                else
                    toast("Unable to open camera")
            } else {
                openApplicationSettings()
                toast("Permission Denied. Go To Setting And Enable It")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setOCRButton() {
        varOCRButton.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                varTextView.text = ""
                varBitmapCroppedTakenPicture = (varImagePreview.drawable as BitmapDrawable).bitmap
                firebaseVisionImage = InputImage.fromBitmap(varBitmapCroppedTakenPicture, 0)
                firebaseVisionTextRecognizer = getClient()

                firebaseVisionTextRecognizer.process(firebaseVisionImage)
                    .addOnSuccessListener { firebaseVisionText ->
                        processResultText(firebaseVisionText)

                        if (varTextView.text.isNotEmpty()) {
                            varEnregistrerTexte.isEnabled = true
                            setEnregistrerTexteButton()
                        }
                    }
                    .addOnFailureListener {
                        varTextView.text = "Failed"
                    }

            } else {
                openApplicationSettings()
                toast("Permission Denied. Go To Setting And Enable It")
            }
        }
    }

    private fun setEnregistrerTexteButton() {
        varEnregistrerTexte.setOnClickListener {
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processResultText(resultText: Text) {
        if (resultText.textBlocks.size == 0) {
            varTextView.text = "No Text Found in the picture"
            return
        }

        for (block in resultText.textBlocks) {
            val blockText = block.text
            varTextView.append("$blockText ")
        }
    }

    private fun getPhotoFile(fileName: String): File {
        val storageDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(fileName, ".png", storageDirectory)
    }

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

}