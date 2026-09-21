package com.example.data.model

// Ensure your class has a parameterless default constructor for Firestore
data class Recipe(
    val title: String = "",
    val prepTimeMinutes: Int = 0,
    val ingredients: List<String> = emptyList(),
    val instructions: String = ""
)
