package com.example.inwentarz

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth

class OknoPracownika : AppCompatActivity() {

    lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var logoutButton: Button
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()  // Ukrycie ActionBar
        setContentView(R.layout.activity_okno_pracownika)

        // Pobranie referencji do UI
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        logoutButton = findViewById(R.id.logout_button)  // Dodaj ten przycisk w XML!

        // Pobranie danych użytkownika
        preferences = getSharedPreferences("userPrefs", MODE_PRIVATE)

        val adapter = ViewPagerAdapter(supportFragmentManager, lifecycle)
        viewPager.adapter = adapter

        // Ustawienie nazw zakładek
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "WZ"
                1 -> "KONTRAHENCI"
                2 -> "TOWARY"
                3 -> "PODGLĄD"
                4 -> "RAPORTY"
                5 -> "Stany Magazynowe" // nowy fragment
                6 -> "Ustawienia"
                else -> "?"
            }
        }.attach()

        // Obsługa wylogowania użytkownika
        logoutButton.setOnClickListener {
            logOutUser()
        }
    }

    /**
     * Funkcja do wylogowania użytkownika
     */
    private fun logOutUser() {
        preferences.edit().clear().apply() // Usunięcie zapamiętanych danych logowania
        FirebaseAuth.getInstance().signOut() // Wylogowanie z Firebase

        val intent = Intent(this, Logowanie::class.java)
        startActivity(intent)
        finish() // Zamknięcie tego ekranu, by nie można było wrócić przyciskiem "wstecz"
    }
}
