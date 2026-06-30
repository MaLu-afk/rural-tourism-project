package yupay.turismo.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE category = :category")
    fun getProductsByCategory(category: String): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Transaction
    suspend fun replaceProducts(products: List<Product>) {
        deleteAllProducts()
        insertProducts(products)
    }

    /**
     * Aplica un merge en UNA sola transacción (borra los eliminados y upserta el resto), de modo que
     * Room invalide la tabla una única vez y el observador de `allProducts` no vea estados parciales.
     */
    @Transaction
    suspend fun applyMerge(deletes: List<Product>, upserts: List<Product>) {
        deletes.forEach { deleteProduct(it) }
        upserts.forEach { insertProduct(it) }
    }

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Int): Product?

    @Query("SELECT * FROM products")
    suspend fun getAllOnce(): List<Product>

    @Query("SELECT * FROM products WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: Long): Product?

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Product?

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}
