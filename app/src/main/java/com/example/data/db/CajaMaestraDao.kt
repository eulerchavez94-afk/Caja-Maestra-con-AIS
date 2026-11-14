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

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryItemByIdSync(id: Long): InventoryItem?

    @Query("UPDATE inventory_items SET stock = stock - :quantity WHERE id = :id")
    suspend fun reduceStock(id: Long, quantity: Double)

    @Query("UPDATE inventory_items SET stock = stock + :quantity WHERE id = :id")
    suspend fun addStock(id: Long, quantity: Double)

    // Inventory Movements History (Room v5)
    @Query("SELECT * FROM inventory_movements ORDER BY date DESC, id DESC")
    fun getAllInventoryMovements(): Flow<List<InventoryMovement>>

    @Query("SELECT * FROM inventory_movements WHERE itemId = :itemId ORDER BY date DESC, id DESC")
    fun getMovementsForInventoryItem(itemId: Long): Flow<List<InventoryMovement>>

    @Query("SELECT * FROM inventory_movements ORDER BY id ASC")
    suspend fun getAllInventoryMovementsSync(): List<InventoryMovement>

    @Query("SELECT * FROM inventory_movements WHERE itemId = :itemId ORDER BY date DESC, id DESC")
    suspend fun getMovementsForInventoryItemSync(itemId: Long): List<InventoryMovement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryMovement(movement: InventoryMovement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInventoryMovements(movements: List<InventoryMovement>)

    @Query("DELETE FROM inventory_movements")
    suspend fun clearInventoryMovements()

    // Sales
    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE type = :type ORDER BY date DESC")
    fun getSalesByType(type: SaleType): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleByIdSync(id: Long): Sale?

    @Query("SELECT * FROM sales WHERE folio = :folio LIMIT 1")
    suspend fun getSaleByFolioSync(folio: String): Sale?

    @Query("SELECT folio FROM sales WHERE type = :type")
    suspend fun getSaleFoliosByTypeSync(type: SaleType): List<String>

    @Query("SELECT COUNT(*) FROM sales WHERE folio = :folio")
    suspend fun countSalesWithFolio(folio: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale): Long

    @Update
    suspend fun updateSale(sale: Sale)

    @Delete
    suspend fun deleteSale(sale: Sale)

    // Purchases
    @Query("SELECT * FROM purchases ORDER BY date DESC")
    fun getAllPurchases(): Flow<List<Purchase>>

    @Query("SELECT folio FROM purchases")
    suspend fun getPurchaseFoliosSync(): List<String>

    @Query("SELECT COUNT(*) FROM purchases WHERE folio = :folio")
    suspend fun countPurchasesWithFolio(folio: String): Int

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

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientByIdSync(id: Long): Client?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    // Suppliers
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierByIdSync(id: Long): Supplier?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier): Long

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    // Employees
    @Query("SELECT * FROM employees ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees ORDER BY name ASC")
    suspend fun getAllEmployeesSync(): List<Employee>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    // Cash Register Shifts & Drawer Movements
    @Query("SELECT * FROM cash_shifts ORDER BY openedAt DESC")
    fun getAllCashShifts(): Flow<List<CashRegisterShift>>

    @Query("SELECT * FROM cash_shifts WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1")
    fun getOpenCashShift(): Flow<CashRegisterShift?>

    @Query("SELECT * FROM cash_shifts WHERE isOpen = 1 ORDER BY openedAt DESC LIMIT 1")
    suspend fun getOpenCashShiftSync(): CashRegisterShift?

    @Query("SELECT * FROM cash_shifts WHERE id = :id LIMIT 1")
    suspend fun getCashShiftByIdSync(id: Long): CashRegisterShift?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashShift(shift: CashRegisterShift): Long

    @Update
    suspend fun updateCashShift(shift: CashRegisterShift)

    @Query("SELECT * FROM cash_shift_movements WHERE shiftId = :shiftId ORDER BY date DESC")
    fun getMovementsForShift(shiftId: Long): Flow<List<CashShiftMovement>>

    @Query("SELECT * FROM cash_shift_movements WHERE shiftId = :shiftId ORDER BY date DESC")
    suspend fun getMovementsForShiftSync(shiftId: Long): List<CashShiftMovement>

    @Query("SELECT * FROM cash_shift_movements ORDER BY date DESC")
    fun getAllShiftMovements(): Flow<List<CashShiftMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashShiftMovement(movement: CashShiftMovement): Long

    @Query("SELECT * FROM sales WHERE shiftId = :shiftId ORDER BY date DESC")
    suspend fun getSalesForShiftSync(shiftId: Long): List<Sale>

    // Clear all tables for secure data wipe & atomic restore
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

    @Query("DELETE FROM clients")
    suspend fun clearClients()

    @Query("DELETE FROM suppliers")
    suspend fun clearSuppliers()

    @Query("DELETE FROM employees")
    suspend fun clearEmployees()

    @Query("DELETE FROM cash_shifts")
    suspend fun clearCashShifts()

    @Query("DELETE FROM cash_shift_movements")
    suspend fun clearCashShiftMovements()

    // Synchronous full-table reads for consistent backup & integrity verification
    @Query("SELECT * FROM inventory_items ORDER BY id ASC")
    suspend fun getAllInventoryItemsSync(): List<InventoryItem>

    @Query("SELECT * FROM sales ORDER BY id ASC")
    suspend fun getAllSalesSync(): List<Sale>

    @Query("SELECT * FROM purchases ORDER BY id ASC")
    suspend fun getAllPurchasesSync(): List<Purchase>

    @Query("SELECT * FROM extra_movements ORDER BY id ASC")
    suspend fun getAllExtraMovementsSync(): List<ExtraMovement>

    @Query("SELECT * FROM rentals_loans ORDER BY id ASC")
    suspend fun getAllRentalsLoansSync(): List<RentalLoan>

    @Query("SELECT * FROM clients ORDER BY id ASC")
    suspend fun getAllClientsSync(): List<Client>

    @Query("SELECT * FROM suppliers ORDER BY id ASC")
    suspend fun getAllSuppliersSync(): List<Supplier>

    @Query("SELECT * FROM cash_shifts ORDER BY id ASC")
    suspend fun getAllCashShiftsSync(): List<CashRegisterShift>

    @Query("SELECT * FROM cash_shift_movements ORDER BY id ASC")
    suspend fun getAllShiftMovementsSync(): List<CashShiftMovement>

    // Batch inserts preserving entity IDs for coordinated restore
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInventoryItems(items: List<InventoryItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSales(sales: List<Sale>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPurchases(purchases: List<Purchase>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllExtraMovements(movements: List<ExtraMovement>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRentalsLoans(rentals: List<RentalLoan>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllClients(clients: List<Client>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuppliers(suppliers: List<Supplier>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEmployees(employees: List<Employee>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCashShifts(shifts: List<CashRegisterShift>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCashShiftMovements(movements: List<CashShiftMovement>)

    // Persistent atomic folio sequences
    @Query("SELECT * FROM folio_sequences WHERE sequenceKey = :key LIMIT 1")
    suspend fun getFolioSequenceSync(key: String): FolioSequence?

    @Query("SELECT * FROM folio_sequences ORDER BY sequenceKey ASC")
    suspend fun getAllFolioSequencesSync(): List<FolioSequence>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolioSequence(sequence: FolioSequence)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFolioSequences(sequences: List<FolioSequence>)

    @Query("DELETE FROM folio_sequences")
    suspend fun clearFolioSequences()
}
