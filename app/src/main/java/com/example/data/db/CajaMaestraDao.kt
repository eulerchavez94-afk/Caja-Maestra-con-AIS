package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CajaMaestraDao {
    // Inventory
    @Query("SELECT * FROM inventory_items ORDER BY name ASC")
    fun getAllInventoryItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE type = :type ORDER BY name ASC")
    fun getInventoryItemsByType(type: ItemType): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE stock <= minStock")
    fun getLowStockItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE isWaste = 1")
    fun getWasteItems(): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("UPDATE inventory_items SET stock = stock - :quantity WHERE id = :id")
    suspend fun reduceStock(id: Long, quantity: Double)

    @Query("UPDATE inventory_items SET stock = stock + :quantity WHERE id = :id")
    suspend fun addStock(id: Long, quantity: Double)

    // Sales
    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE type = :type ORDER BY date DESC")
    fun getSalesByType(type: SaleType): Flow<List<Sale>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale): Long

    @Update
    suspend fun updateSale(sale: Sale)

    @Delete
    suspend fun deleteSale(sale: Sale)

    // Purchases
    @Query("SELECT * FROM purchases ORDER BY date DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Delete
    suspend fun deletePurchase(purchase: Purchase)

    // Extra movements
    @Query("SELECT * FROM extra_movements ORDER BY date DESC")
    fun getAllExtraMovements(): Flow<List<ExtraMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtraMovement(movement: ExtraMovement): Long

    @Delete
    suspend fun deleteExtraMovement(movement: ExtraMovement)

    // Rentals & Loans
    @Query("SELECT * FROM rentals_loans ORDER BY startDate DESC")
    fun getAllRentalsLoans(): Flow<List<RentalLoan>>

    @Query("SELECT * FROM rentals_loans WHERE isRental = :isRental ORDER BY startDate DESC")
    fun getRentalsLoansByType(isRental: Boolean): Flow<List<RentalLoan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRentalLoan(item: RentalLoan): Long

    @Update
    suspend fun updateRentalLoan(item: RentalLoan)

    @Delete
    suspend fun deleteRentalLoan(item: RentalLoan)

    // Clients
    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<Client>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    // Suppliers
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    // Employees
    @Query("SELECT * FROM employees ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    // Clear all tables for secure data wipe
    @Query("DELETE FROM sales")
    suspend fun clearSales()

    @Query("DELETE FROM purchases")
    suspend fun clearPurchases()

    @Query("DELETE FROM inventory_items")
    suspend fun clearInventory()

    @Query("DELETE FROM extra_movements")
    suspend fun clearExtraMovements()

    @Query("DELETE FROM rentals_loans")
    suspend fun clearRentalsLoans()
}
