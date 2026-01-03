package com.example.clickapp

import android.os.Parcel
import android.os.Parcelable

data class ClickableElement(
    val text: String,
    val contentDescription: String,
    val className: String,
    val x: Int,
    val y: Int,
    val bounds: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(text)
        parcel.writeString(contentDescription)
        parcel.writeString(className)
        parcel.writeInt(x)
        parcel.writeInt(y)
        parcel.writeString(bounds)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ClickableElement> {
        override fun createFromParcel(parcel: Parcel): ClickableElement {
            return ClickableElement(parcel)
        }

        override fun newArray(size: Int): Array<ClickableElement?> {
            return arrayOfNulls(size)
        }
    }

    fun getDisplayString(): String {
        val type = className.substringAfterLast(".").replace("AppCompat", "")
        return "$text ($type)"
    }
}
