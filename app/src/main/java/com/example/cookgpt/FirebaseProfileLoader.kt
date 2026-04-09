package com.example.cookgpt

import com.google.firebase.database.FirebaseDatabase

object FirebaseProfileLoader {
    fun loadUserProfile(
        uid: String,
        onSuccess: (UserHealthProfile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = UserHealthProfile(
                    name             = snapshot.child("name").getValue(String::class.java) ?: "",
                    allergies        = snapshot.child("allergies").getValue(String::class.java) ?: "",
                    dietType         = snapshot.child("dietType").getValue(String::class.java) ?: "",
                    fitnessGoal      = snapshot.child("goal").getValue(String::class.java) ?: "",
                    calorieGoal      = snapshot.child("calorieGoal").getValue(String::class.java) ?: "",
                    weightGoal       = snapshot.child("weightGoal").getValue(String::class.java) ?: "",
                    dislikedFoods    = snapshot.child("dislikedFoods").getValue(String::class.java) ?: "",
                    preferredCuisine = snapshot.child("preferredCuisine").getValue(String::class.java) ?: "",
                    age              = snapshot.child("age").getValue(String::class.java) ?: "",
                    weight           = snapshot.child("weight").getValue(String::class.java) ?: "",
                    height           = snapshot.child("height").getValue(String::class.java) ?: "",
                    gender           = snapshot.child("gender").getValue(String::class.java) ?: ""
                )
                onSuccess(profile)
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Failed to load profile")
            }
    }
}
