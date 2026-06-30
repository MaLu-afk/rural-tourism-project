package yupay.turismo.data

import kotlinx.coroutines.flow.Flow
import yupay.turismo.data.local.AppSettings
import yupay.turismo.data.local.AppSettingsDao
import yupay.turismo.data.local.Product
import yupay.turismo.data.local.ProductDao
import yupay.turismo.data.local.Visit
import yupay.turismo.data.local.VisitDao

class DataRepository(
    private val appSettingsDao: AppSettingsDao,
    private val visitDao: VisitDao,
    private val productDao: ProductDao
) {
    val appSettings: Flow<AppSettings?> = appSettingsDao.getSettings()
    val allVisits: Flow<List<Visit>> = visitDao.getAllVisits()
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun getSettingsOnce(): AppSettings? {
        return appSettingsDao.getSettingsOnce()
    }

    suspend fun saveSettings(settings: AppSettings) {
        appSettingsDao.saveSettings(settings)
    }

    suspend fun insertVisit(visit: Visit): Long {
        return visitDao.insertVisit(visit)
    }

    suspend fun getVisitById(id: Int): Visit? {
        return visitDao.getVisitById(id)
    }

    suspend fun getVisitByUuid(uuid: String): Visit? {
        return visitDao.getByUuid(uuid)
    }

    suspend fun getProductById(id: Int): Product? {
        return productDao.getProductById(id)
    }

    suspend fun getProductByUuid(uuid: String): Product? {
        return productDao.getByUuid(uuid)
    }

    suspend fun deleteProducts(products: List<Product>) {
        products.forEach { productDao.deleteProduct(it) }
    }

    suspend fun insertProducts(products: List<Product>) {
        productDao.insertProducts(products)
    }

    suspend fun replaceAllProducts(products: List<Product>) {
        productDao.replaceProducts(products)
    }

    suspend fun applyProductMerge(deletes: List<Product>, upserts: List<Product>) {
        productDao.applyMerge(deletes, upserts)
    }

    suspend fun insertProduct(product: Product): Long {
        return productDao.insertProduct(product)
    }

    suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product)
    }

    suspend fun deleteProduct(product: Product) {
        productDao.deleteProduct(product)
    }

    suspend fun clearAllData() {
        appSettingsDao.clearSettings()
        visitDao.deleteAllVisits()
        productDao.deleteAllProducts()
    }
}
