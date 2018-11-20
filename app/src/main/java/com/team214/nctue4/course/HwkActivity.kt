package com.team214.nctue4.course

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.team214.nctue4.R
import com.team214.nctue4.ann.AnnAttachmentAdapter
import com.team214.nctue4.client.E3Client
import com.team214.nctue4.client.E3ClientFactory
import com.team214.nctue4.client.E3Type
import com.team214.nctue4.model.CourseItem
import com.team214.nctue4.model.HwkItem
import com.team214.nctue4.utility.downloadFile
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_assign.*
import kotlinx.android.synthetic.main.progress_bar.*
import kotlinx.android.synthetic.main.status_error.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.PatternSyntaxException

class HwkActivity : AppCompatActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var url: String
    private lateinit var fileName: String
    lateinit var client: E3Client
    private lateinit var courseItem: CourseItem
    lateinit var hwkItem: HwkItem
    private var disposable: Disposable? = null
    private var requestComplete = false

    override fun onDestroy() {
        disposable?.dispose()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        setContentView(R.layout.activity_assign)
        setSupportActionBar(toolbar)
        courseItem = intent?.extras?.getParcelable("courseItem")!!
        hwkItem = intent?.extras?.getParcelable("hwkItem")!!
        toolbar.title = courseItem.courseName
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        getData()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.download_assign, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_download_assign -> {
                if (!requestComplete) {
                    Toast.makeText(this, getString(R.string.wait), Toast.LENGTH_SHORT).show()
                } else {
                    val dialog = HwkDialog()
                    val bundle = Bundle()
                    bundle.putParcelable("hwkItem", hwkItem)
                    dialog.arguments = bundle
                    dialog.show(supportFragmentManager, "TAG")
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getData() {
        requestComplete = false
        error_request.visibility = View.GONE
        progress_bar?.visibility = View.VISIBLE
        client = E3ClientFactory.createFromHwk(this, hwkItem)
        disposable = client.getHwkDetail(hwkItem, courseItem)
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { progress_bar?.visibility = View.GONE }
            .subscribeBy(
                onNext = {
                    hwkItem = it
                    requestComplete = true
                    showData(it)
                },
                onError = {
                    error_request?.visibility = View.VISIBLE
                    error_request_retry?.setOnClickListener { getData() }
                }
            )
    }


    private fun showData(hwkItem: HwkItem) {
        error_request.visibility = View.GONE
        // replace <img src="/...> to <img src="http://e3.nctu.edu.tw/..."
        val content =
            if (hwkItem.e3Type == E3Type.OLD) {
                try {
                    Regex("(?<=(<img[.\\s\\S^>]{0,300}src[ \n]{0,300}=[ \n]{0,300}\"))(/)(?=([^/]))")
                        .replace(hwkItem.content!!, "http://e3.nctu.edu.tw/")
                } catch (e: PatternSyntaxException) {
                    hwkItem.content
                }
            } else hwkItem.content
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN)
        assign_start_date.text = sdf.format(hwkItem.startDate)
        assign_end_date.text = sdf.format(hwkItem.endDate)
        ann_title.text = hwkItem.name
        assign_content_web_view.settings.defaultTextEncodingName = "utf-8"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            assign_content_web_view.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        assign_content_web_view.loadData(content, "text/html; charset=utf-8", "UTF-8")
        assign_content_web_view.setBackgroundColor(Color.TRANSPARENT)
        assign_attach.layoutManager = LinearLayoutManager(this)
        assign_attach.adapter = AnnAttachmentAdapter(this, hwkItem.attachItems) {
            url = it.url
            fileName = it.name
            downloadFile(fileName, url, this, this, assign_root, client.getCookie()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                }
            }

        }
        assign_container?.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            0 -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    downloadFile(fileName, url, this, this, assign_root, client.getCookie(), null)
                }
                return
            }
        }
    }
}