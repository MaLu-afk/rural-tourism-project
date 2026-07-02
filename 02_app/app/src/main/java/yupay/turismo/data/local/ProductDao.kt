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

    @Query("DELETE FROM products WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: Long): Int

    @Query("SELECT * FROM products WHERE remoteId IS NULL ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE remoteId IS NULL")
    fun countUnsyncedFlow(): Flow<Int>

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): Product?

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}
