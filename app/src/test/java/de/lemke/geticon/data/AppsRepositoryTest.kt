package de.lemke.geticon.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppsRepositoryTest {

    private lateinit var appsRepository: AppsRepository
    private val mockContext: Context = mock()
    private val mockPackageManager: PackageManager = mock()

    @Before
    fun setUp() {
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        appsRepository = AppsRepository(mockContext)
    }

    @Test
    fun `get returns empty list when PackageManager throws exception`() = runTest {
        whenever(mockPackageManager.getInstalledPackages(any<Int>())).thenThrow(RuntimeException("Test Exception"))
        // For SDK < TIRAMISU, getInstalledPackages(0) is called.
        // For SDK >= TIRAMISU, getInstalledPackages(PackageManager.PackageInfoFlags.of(GET_META_DATA.toLong())) is called.
        // We need to mock both versions if we want to be thorough, or rely on Mockito's default lenient behavior for unmocked methods if applicable.
        // However, for this specific test, throwing for any Int flags should cover it.


        val apps = appsRepository.get()

        assertTrue(apps.isEmpty())
    }

    @Test
    fun `get returns list of App objects`() = runTest {
        val packageInfo1 = PackageInfo().apply {
            packageName = "com.example.app1"
            applicationInfo = ApplicationInfo().apply {
                flags = 0 // Not a system app
                // mock loadLabel to prevent NullPointerException
                whenever(loadLabel(mockPackageManager)).thenReturn("App 1")
            }
        }
        val packageInfo2 = PackageInfo().apply {
            packageName = "com.example.app2"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_SYSTEM // System app
                whenever(loadLabel(mockPackageManager)).thenReturn("App 2")
            }
        }
        val packageInfos = listOf(packageInfo1, packageInfo2)

        // Mock getInstalledPackages for SDK < TIRAMISU
        whenever(mockPackageManager.getInstalledPackages(PackageManager.GET_META_DATA)).thenReturn(packageInfos)
        // Mock getInstalledPackages for SDK >= TIRAMISU
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            whenever(mockPackageManager.getInstalledPackages(any<PackageManager.PackageInfoFlags>())).thenReturn(packageInfos)
        }


        val apps = appsRepository.get()

        assertEquals(2, apps.size)

        assertEquals("com.example.app1", apps[0].packageName)
        assertEquals("App 1", apps[0].label)
        assertFalse(apps[0].isSystemApp)

        assertEquals("com.example.app2", apps[1].packageName)
        assertEquals("App 2", apps[1].label)
        assertTrue(apps[1].isSystemApp)
    }

    @Test
    fun `get caches results after first call`() = runTest {
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.app"
            applicationInfo = ApplicationInfo().apply {
                flags = 0
                whenever(loadLabel(mockPackageManager)).thenReturn("App")
            }
        }
        val packageInfos = listOf(packageInfo)

        // Mock getInstalledPackages for SDK < TIRAMISU
        whenever(mockPackageManager.getInstalledPackages(PackageManager.GET_META_DATA)).thenReturn(packageInfos)
        // Mock getInstalledPackages for SDK >= TIRAMISU
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            whenever(mockPackageManager.getInstalledPackages(any<PackageManager.PackageInfoFlags>())).thenReturn(packageInfos)
        }

        // First call - should fetch from PackageManager
        val apps1 = appsRepository.get()
        assertEquals(1, apps1.size)

        // Second call - should return cached results
        // To ensure it's cached, we can change what PackageManager would return now
        // or verify that getInstalledPackages was called only once.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            whenever(mockPackageManager.getInstalledPackages(any<PackageManager.PackageInfoFlags>())).thenReturn(emptyList())
        } else {
            whenever(mockPackageManager.getInstalledPackages(PackageManager.GET_META_DATA)).thenReturn(emptyList())
        }

        val apps2 = appsRepository.get()
        assertEquals(1, apps2.size) // Still 1, meaning it used the cache
        assertEquals("com.example.app", apps2[0].packageName)

        // Verify PackageManager.getInstalledPackages was called only once (or twice depending on SDK, but effectively once for the data)
        // This is tricky with the SDK version split. A simpler check is that the data remains the same.
        // For a more robust check, one might need to use ArgumentCaptor or verify number of invocations based on SDK.
    }
}
