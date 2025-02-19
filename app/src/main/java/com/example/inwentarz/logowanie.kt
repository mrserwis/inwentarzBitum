package com.example.inwentarz

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class Logowanie : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var zapamietajCheckBox: CheckBox
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logowanie)

        auth = FirebaseAuth.getInstance()
        preferences = getSharedPreferences("userPrefs", MODE_PRIVATE)

        emailEditText = findViewById(R.id.email)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.login_button)
        progressBar = findViewById(R.id.paseklogowania)
        zapamietajCheckBox = findViewById(R.id.zapamietajCheckBox)

        checkLoggedInUser()

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Proszę wypełnić wszystkie pola", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = ProgressBar.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = ProgressBar.GONE
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        Toast.makeText(this, "Witaj ${user?.email}", Toast.LENGTH_SHORT).show()

                        if (zapamietajCheckBox.isChecked) {
                            saveLoginState(email)
                        }

                        val intent = Intent(this, OknoPracownika::class.java)
                        intent.putExtra("userName", user?.email)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Błąd logowania: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun checkLoggedInUser() {
        val savedEmail = preferences.getString("user_email", null)
        if (savedEmail != null && auth.currentUser != null) {
            val intent = Intent(this, OknoPracownika::class.java)
            intent.putExtra("userName", savedEmail)
            startActivity(intent)
            finish()
        }
    }

    private fun saveLoginState(email: String) {
        val editor = preferences.edit()
        editor.putString("user_email", email)
        editor.apply()
    }
}
