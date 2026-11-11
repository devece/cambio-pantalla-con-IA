package cl.tablero.launcher

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Qué permisos pide la búsqueda de scanners según la versión de Android (Bluetooth desde la 12, ubicación de la 6 a la 11, ninguno antes). */
@RunWith(RobolectricTestRunner::class)
class ObdBluetoothPermissionsTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test @Config(sdk = [21])
    fun `en Android 5 no se pide ningun permiso`() {
        assertTrue(ObdBluetooth.missingScanPermissions(app).isEmpty())
    }

    @Test @Config(sdk = [26, 29, 30])
    fun `de Android 6 a 11 se pide la ubicacion`() {
        assertContentEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ObdBluetooth.missingScanPermissions(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(ObdBluetooth.missingScanPermissions(app).isEmpty())
    }

    @Test @Config(sdk = [33, 34])
    fun `desde Android 12 se piden los permisos de Bluetooth, y solo los que faltan`() {
        assertEquals(setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), ObdBluetooth.missingScanPermissions(app).toSet())
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        assertEquals(setOf(Manifest.permission.BLUETOOTH_CONNECT), ObdBluetooth.missingScanPermissions(app).toSet())
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        assertTrue(ObdBluetooth.missingScanPermissions(app).isEmpty())
    }
}
