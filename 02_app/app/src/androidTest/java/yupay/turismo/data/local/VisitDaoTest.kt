package yupay.turismo.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas para [VisitDao] usando una base de datos Room EN MEMORIA.
 *
 * Requieren un emulador o dispositivo físico (se ejecutan con el runner de Android,
 * no en la JVM del host). Verifican las operaciones básicas de persistencia de visitas:
 * inserción, consulta por uuid, filtrado de visitas no sincronizadas y borrado total.
 *
 * Ubicación: app/src/androidTest/java/yupay/turismo/data/local/VisitDaoTest.kt
 */
@RunWith(AndroidJUnit4::class)
class VisitDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: VisitDao

    @Before
    fun crearBaseDeDatosEnMemoria() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // inMemoryDatabaseBuilder: la BD vive solo durante la prueba y no toca el disco.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.visitDao()
    }

    @After
    fun cerrarBaseDeDatos() {
        db.close()
    }

    @Test
    fun insertarYLeerPorUuid() = runBlocking {
        val visita = Visit(nationality = "Perú", nationalityFlag = "PE")
        dao.insertVisit(visita)

        val recuperada = dao.getByUuid(visita.uuid)
        assertNotNull(recuperada)
        assertEquals("Perú", recuperada!!.nationality)
    }

    @Test
    fun visitaNueva_apareceComoNoSincronizada() = runBlocking {
        // Una visita recién creada tiene remoteId = null → debe figurar como pendiente de subir.
        val visita = Visit(nationality = "Francia", nationalityFlag = "FR")
        dao.insertVisit(visita)

        val noSincronizadas = dao.getUnsynced()
        assertTrue(noSincronizadas.any { it.uuid == visita.uuid })
    }

    @Test
    fun borrarTodo_dejaLaTablaVacia() = runBlocking {
        dao.insertVisit(Visit(nationality = "Chile", nationalityFlag = "CL"))
        dao.deleteAllVisits()

        assertTrue(dao.getAllOnce().isEmpty())
    }
}
