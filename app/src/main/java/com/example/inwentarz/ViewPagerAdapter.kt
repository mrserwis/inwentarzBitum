package com.example.inwentarz

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle)
    : FragmentStateAdapter(fragmentManager, lifecycle) {

    // Kolejność fragmentów decyduje, co jest na pozycji 0,1,2,3
    private val fragmentList = listOf(
        WZFragment(),
        OdbiorcyFragment(),
        TowaryFragment(),
        PodgladFragment(),
        RaportyFragment(),
        StanyFragment(),
        ProfilFragment()// nowy fragment
    )

    override fun getItemCount(): Int = fragmentList.size

    override fun createFragment(position: Int): Fragment = fragmentList[position]
}
