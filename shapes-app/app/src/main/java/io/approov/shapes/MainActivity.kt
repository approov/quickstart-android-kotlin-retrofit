// Main activity for Approov Shapes App Demo (using Retrofit)
//
// MIT License
//
// Copyright (c) 2016-present, Critical Blue Ltd.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
// (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
// publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
// ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package io.approov.shapes

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import io.approov.shapes.ShapesClientInstance.retrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : Activity() {
    private var activity: Activity? = null
    private var statusView: View? = null
    private var statusImageView: ImageView? = null
    private var statusTextView: TextView? = null
    private var connectivityCheckButton: Button? = null
    private var shapesCheckButton: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activity = this

        // find controls
        statusView = findViewById(R.id.viewStatus)
        statusImageView = findViewById<View>(R.id.imgStatus) as ImageView
        statusTextView = findViewById(R.id.txtStatus)
        connectivityCheckButton = findViewById(R.id.btnConnectionCheck)
        shapesCheckButton = findViewById(R.id.btnShapesCheck)

        // handle connection check
        connectivityCheckButton!!.setOnClickListener(View.OnClickListener {
            // hide status
            activity!!.runOnUiThread(Runnable { statusView!!.setVisibility(View.INVISIBLE) })
            // Make a Retrofit request to get hello text
            val service = retrofitInstance!!.create(ShapesService::class.java)
            val call = service.hello
            call!!.enqueue(object : Callback<HelloModel?> {
                override fun onResponse(call: Call<HelloModel?>, response: Response<HelloModel?>) {
                    val imgId: Int
                    var message = "Http status code " + response.code()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Connectivity call successful")
                        imgId = R.drawable.hello
                        message = response.body()!!.text
                    } else {
                        Log.d(TAG, "Connectivity call unsuccessful")
                        imgId = R.drawable.confused
                    }
                    val msg = message
                    activity!!.runOnUiThread(Runnable {
                        statusImageView!!.setImageResource(imgId)
                        statusTextView!!.setText(msg)
                        statusView!!.setVisibility(View.VISIBLE)
                    })
                }

                override fun onFailure(call: Call<HelloModel?>, t: Throwable) {
                    Log.d(TAG, "Connectivity call failed")
                    val imgId = R.drawable.confused
                    val msg = "Request failed: " + t.message
                    activity!!.runOnUiThread(Runnable {
                        statusImageView!!.setImageResource(imgId)
                        statusTextView!!.setText(msg)
                        statusView!!.setVisibility(View.VISIBLE)
                    })
                }
            })
        })

        // handle getting shapes
        shapesCheckButton!!.setOnClickListener(View.OnClickListener {
            // hide status
            activity!!.runOnUiThread(Runnable { statusView!!.setVisibility(View.INVISIBLE) })
            // Make a Retrofit request to get a shape
            val service = retrofitInstance!!.create(ShapesService::class.java)
            val call = service.shape
            call!!.enqueue(object : Callback<ShapeModel?> {
                override fun onResponse(call: Call<ShapeModel?>, response: Response<ShapeModel?>) {
                    var imgId = R.drawable.confused
                    val msg = "Http status code " + response.code()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Shapes call successful")
                        val shape = response.body()
                        if (shape!!.shape.equals("square", ignoreCase = true)) {
                            imgId = R.drawable.square
                        } else if (shape.shape.equals("circle", ignoreCase = true)) {
                            imgId = R.drawable.circle
                        } else if (shape.shape.equals("rectangle", ignoreCase = true)) {
                            imgId = R.drawable.rectangle
                        } else if (shape.shape.equals("triangle", ignoreCase = true)) {
                            imgId = R.drawable.triangle
                        }
                    } else {
                        Log.d(TAG, "Shapes call unsuccessful")
                    }
                    val finalImgId = imgId
                    activity!!.runOnUiThread(Runnable {
                        statusImageView!!.setImageResource(finalImgId)
                        statusTextView!!.setText(msg)
                        statusView!!.setVisibility(View.VISIBLE)
                    })
                }

                override fun onFailure(call: Call<ShapeModel?>, t: Throwable) {
                    Log.d(TAG, "Shapes call failed")
                    val imgId = R.drawable.confused
                    val msg = "Request failed: " + t.message
                    activity!!.runOnUiThread(Runnable {
                        statusImageView!!.setImageResource(imgId)
                        statusTextView!!.setText(msg)
                        statusView!!.setVisibility(View.VISIBLE)
                    })
                }
            })
        })
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}