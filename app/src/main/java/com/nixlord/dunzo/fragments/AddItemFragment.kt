package com.nixlord.dunzo.fragments

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.model.Document
import com.nixlord.dunzo.MainActivity
import com.nixlord.dunzo.R
import com.nixlord.dunzo.azure.ComputerVision
import com.nixlord.dunzo.azure.SpellCheck
import com.nixlord.dunzo.ml.TextScanner
import com.nixlord.dunzo.model.Product
import com.nixlord.dunzo.model.Seller
import com.nixlord.dunzo.util.DataCreator
import com.nixlord.dunzo.util.DataFusion
import com.phoenixoverlord.pravega.base.BaseActivity
import com.phoenixoverlord.pravega.extensions.Firebase
import com.phoenixoverlord.pravega.extensions.logDebug
import com.phoenixoverlord.pravega.extensions.logError
import com.phoenixoverlord.pravega.toast
import kotlinx.android.synthetic.main.fragment_new_product.*
import java.io.File
import java.util.regex.Pattern

class AddItemFragment : Fragment() {
    lateinit var imageFile : File

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_new_product, container, false)
    }

//    fun segment(sentence: String) {
//        var lines = sentence
//            .split("\"")
//            .flatMap {
//                it.split(" ")
//            }
//            .filter { ! (it.contains("qty", true) || it.contains("price", true) || it.contains("amount", true)) }
//            .forEach { logDebug(it) }
//
//
//
//
//    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        segment(""" "Qty Price Amount""ALU PYAZ KACHO""4.000""28.44 113.76""KHAMAN DHOKLA""3.000""23.70 71.10""JALEBI""0.300 331.75""99.53""LACCHA RABDI""2.000""37.91""75.82 """)
            val product = Product()
        val seller = Seller()
        var type = "Food"

        cameraButton.setOnClickListener {
            activity.apply {
                (activity as MainActivity).withPermissions(
                    Manifest.permission.CAMERA
                ).execute {
                    (activity as MainActivity).takePhoto("Select Bill")
                        .addOnSuccessListener {
                            it.forEach { image ->
                                imageFile = image
                                (activity as MainActivity).toast("Please Wait for 15seconds, Dialling Satya from Azure(API call)")
                                ComputerVision.recognize(image, {

                                    val lines = DataCreator.deserializeText(it)
                                    var acc = ""
                                    var itemIndex = 9999999
                                    var phoneIndex = 9999999



                                    seller.name = lines[0]

                                    lines.mapIndexed { index, line ->
                                        logDebug(line)
                                        if (index != 0) {
                                            if (line.contains("ph", true) && line.contains("no", true) ) {
                                                seller.phoneNo = line
                                                logDebug("Phone", line)
                                                seller.address = acc
                                                logDebug("Address", acc)
                                                acc = ""
                                                phoneIndex = index

                                            }


                                            val markerFound = arrayListOf<String>("item", "qty", "price", "amount")
                                                .map { line.contains(it, true) }
                                                .reduce { acc, truth -> acc || truth }

                                            if (index > phoneIndex && markerFound) {
                                                acc = ""
                                                itemIndex = index
                                            }
                                            if (index > itemIndex && line.contains("total", true)) {
                                                if (acc != "!@#$%^&*()") {
                                                product.name = acc
                                                logDebug("name", acc)
                                                product.price = acc
                                                logDebug("price", acc)
                                                acc = "!@#$%^&*()"
                                                    }
                                            }
                                            if (acc != "!@#$%^&*()")
                                                acc += line
                                        }
                                    }



//
//                                    DataFusion.createProduct(
//                                        lines,
//                                        TextScanner.parts(lines)
//                                    )

                                }, {
                                    logError(it)
                                })
                                productImage.setImageBitmap(BitmapFactory.decodeFile("$image"))
                            }
                        }
                }
            }
        }

//        azureSpellCheck.setOnClickListener {
//            SpellCheck.predict("kachor")
//        }

        setupSpinner(productType, R.array.types) { selected -> type = selected }

        uploadButton.setOnClickListener {
            logDebug("")
            product.type = type


            (activity as MainActivity).toast("Uploading Products")

            //logDebug("UPLOAD   " + product.name)
            //"Qty Price Amount""ALU PYAZ KACHO""4.000""28.44 113.76""KHAMAN DHOKLA""3.000""23.70 71.10""JALEBI""0.300 331.75""99.53""LACCHA RABDI""2.000""37.91""75.82"
            val productList = DataFusion.extract(product.name)

            productList
                .map { Pair(it, Firebase.firestore.collection("product").document()) }
                .map { (product, ref) ->
                    product.type = type
                    product.id = ref.id
                    Pair(product, ref)
                }
                .forEach { (product, productRef) ->

                    Firebase.firestore.collection("seller")
                        .whereEqualTo("name", seller.name)
                        .get()
                        .addOnSuccessListener {
                            var sellerID = "NOT_FOUND"
                            it.documents.forEach { snapshot ->
//                                logDebug("SNAPSHOT:ID", snapshot.id)
                                sellerID = snapshot.id
                            }

                            logDebug("SNAPSHOT:ID", sellerID)
                            if (sellerID == "NOT_FOUND") {
                                logDebug("Creating Seller")
                                val sellerRef = Firebase.firestore.collection("seller").document()
                                sellerID = sellerRef.id
                                seller.id = sellerID
                                sellerRef.set(seller)
                            }

                            seller.id = sellerID

                            product.stores.add(sellerID)
                            productRef.set(product)


                            logDebug("pr:id", product.id)
                            logDebug("pr:name", product.name)
                            logDebug("pr:price", product.price)
                            logDebug("pr:type", product.type)
                            logDebug("sel:id", seller.id)
                            logDebug("sel:name", seller.name)
                            logDebug("sel:phn", seller.phoneNo)
                            logDebug("sel:add", seller.address)
                        }
                        .addOnFailureListener {
                            val sellerRef = Firebase.firestore.collection("seller").document()
                            sellerRef.set(seller)
                            val sellerID = sellerRef.id

                            product.id = productRef.id
                            product.stores.add(sellerID)
                            productRef.set(product)

                            logError(Error("FAILURE"))
                            logDebug("pr:id", product.id)
                            logDebug("pr:name", product.name)
                            logDebug("pr:price", product.price)
                            logDebug("pr:type", product.type)
                            logDebug("sel:id", seller.id)
                            logDebug("sel:name", seller.name)
                            logDebug("sel:phn", seller.phoneNo)
                            logDebug("sel:add", seller.address)

                        }
                }
        }
    }

    fun setupSpinner(spinner : Spinner, textArrayResID : Int, onItemSelected : (String) -> Unit) {
        ArrayAdapter.createFromResource(
            this.activity as Context,
            textArrayResID,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                logDebug("Nothing Selected")
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onItemSelected(parent?.getItemAtPosition(position) as String)
            }
        }
    }
}