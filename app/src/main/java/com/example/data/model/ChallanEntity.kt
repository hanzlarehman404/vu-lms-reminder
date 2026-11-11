package com.example.data.model

data class ChallanEntity(
    val challanNo: String,
    val challanType: String,
    val payableAmount: String,
    val paidAmount: String,
    val balance: String,
    val dueDate: String,
    val paidDate: String,
    val isPaid: Boolean,
    val printUrl: String = "",
    val payCardUrl: String = "",
    val payControlName: String = "",
    val viewState: String = "",
    val viewStateGenerator: String = "",
    val previousPage: String = ""
)
