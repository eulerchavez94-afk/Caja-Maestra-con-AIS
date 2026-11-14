package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

@Database(
    entities = [
        InventoryItem::class,
        Sale::class,
        Purchase::class,
        ExtraMovement::class,
        RentalLoan::class,
        Client::class,
        Supplier::class,
        Employee::class,
        CashRegisterShift::class,
        CashShiftMovement::class,
        FolioSequence::class,
        InventoryMovement::class
    ],
    version = 5,
    exportSchema = false
)
abstract class CajaMaestraDatabase : RoomDatabase() {
    abstract fun dao(): CajaMaestraDao

    companion object {
        const val KEY_VENTA = "VENTA"
        const val KEY_COTIZACION = "COTIZACION"
        const val KEY_COMPRA = "COMPRA"

        const val PREFIX_VENTA = "F-"
        const val PREFIX_COTIZACION = "COT-"
        const val PREFIX_COMPRA = "OC-"

        @Volatile
        private var INSTANCE: CajaMaestraDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sales ADD COLUMN shiftId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_shiftId ON sales(shiftId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cash_shifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        cashierName TEXT NOT NULL,
                        cashierRole TEXT NOT NULL,
                        openedAt INTEGER NOT NULL,
                        closedAt INTEGER,
                        openingFloat REAL NOT NULL,
                        cashSalesTotal REAL NOT NULL,
                        cardSalesTotal REAL NOT NULL,
                        transferSalesTotal REAL NOT NULL,
                        creditSalesTotal REAL NOT NULL,
                        cashEntriesTotal REAL NOT NULL,
                        cashWithdrawalsTotal REAL NOT NULL,
                        expectedCash REAL NOT NULL,
                        countedCash REAL NOT NULL,
                        difference REAL NOT NULL,
                        nextTurnFloat REAL NOT NULL,
                        salesCount INTEGER NOT NULL,
                        isOpen INTEGER NOT NULL,
                        openingNotes TEXT NOT NULL,
                        closingNotes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_shifts_openedAt ON cash_shifts(openedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_shifts_isOpen ON cash_shifts(isOpen)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cash_shift_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        shiftId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        reason TEXT NOT NULL,
                        performedBy TEXT NOT NULL,
                        date INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_shift_movements_shiftId ON cash_shift_movements(shiftId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_shift_movements_date ON cash_shift_movements(date)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folio_sequences (
                        sequenceKey TEXT NOT NULL PRIMARY KEY,
                        lastNumber INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                var maxVenta = 0L
                var maxCotizacion = 0L
                db.query("SELECT folio, type FROM sales").use { cursor ->
                    val folioIdx = cursor.getColumnIndexOrThrow("folio")
                    val typeIdx = cursor.getColumnIndexOrThrow("type")
                    while (cursor.moveToNext()) {
                        val folio = cursor.getString(folioIdx) ?: ""
                        val type = cursor.getString(typeIdx) ?: ""
                        if (type == SaleType.COTIZACION.name || folio.startsWith(PREFIX_COTIZACION, ignoreCase = true)) {
                            val num = extractNumericSuffix(folio, PREFIX_COTIZACION)
                            if (num > maxCotizacion) maxCotizacion = num
                        } else {
                            val num = extractNumericSuffix(folio, PREFIX_VENTA)
                            if (num > maxVenta) maxVenta = num
                        }
                    }
                }

                var maxCompra = 0L
                db.query("SELECT folio FROM purchases").use { cursor ->
                    val folioIdx = cursor.getColumnIndexOrThrow("folio")
                    while (cursor.moveToNext()) {
                        val folio = cursor.getString(folioIdx) ?: ""
                        val num = extractNumericSuffix(folio, PREFIX_COMPRA)
                        if (num > maxCompra) maxCompra = num
                    }
                }

                db.execSQL(
                    "INSERT OR REPLACE INTO folio_sequences (sequenceKey, lastNumber) VALUES (?, ?)",
                    arrayOf<Any>(KEY_VENTA, maxVenta)
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO folio_sequences (sequenceKey, lastNumber) VALUES (?, ?)",
                    arrayOf<Any>(KEY_COTIZACION, maxCotizacion)
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO folio_sequences (sequenceKey, lastNumber) VALUES (?, ?)",
                    arrayOf<Any>(KEY_COMPRA, maxCompra)
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_items ADD COLUMN isRawMaterial INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE inventory_items
                    SET isRawMaterial = 1
                    WHERE type = 'PRODUCTO'
                      AND isWaste = 0
                      AND (
                        LOWER(category) LIKE '%materia prima%' OR
                        LOWER(category) LIKE '%insumo%' OR
                        LOWER(category) LIKE '%ingrediente%' OR
                        LOWER(category) LIKE '%empaque%' OR
                        LOWER(category) LIKE '%granel%' OR
                        LOWER(notes) LIKE '%materia prima%' OR
                        LOWER(name) LIKE '%materia prima%'
                      )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemId INTEGER NOT NULL,
                        itemCode TEXT NOT NULL,
                        itemName TEXT NOT NULL,
                        movementType TEXT NOT NULL,
                        quantityDelta REAL NOT NULL,
                        previousStock REAL NOT NULL,
                        newStock REAL NOT NULL,
                        referenceFolio TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        performedBy TEXT NOT NULL,
                        date INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_itemId ON inventory_movements(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_date ON inventory_movements(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_movements_movementType ON inventory_movements(movementType)")
            }
        }

        fun extractNumericSuffix(folio: String, expectedPrefix: String): Long {
            val clean = folio.trim()
            if (clean.isEmpty()) return 0L
            val afterPrefix = if (clean.startsWith(expectedPrefix, ignoreCase = true)) {
                clean.substring(expectedPrefix.length)
            } else {
                // Only match compatible numeric suffix if exact prefix matches or fallback if digits after '-'
                return 0L
            }
            return afterPrefix.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }

        fun deriveFolioSequences(
            sales: List<Sale>,
            purchases: List<Purchase>,
            existing: List<FolioSequence> = emptyList()
        ): List<FolioSequence> {
            val existingMap = existing.associateBy { it.sequenceKey }
            val maxVentaFromSales = sales
                .filter { it.type == SaleType.VENTA }
                .maxOfOrNull { extractNumericSuffix(it.folio, PREFIX_VENTA) } ?: 0L
            val maxCotFromSales = sales
                .filter { it.type == SaleType.COTIZACION }
                .maxOfOrNull { extractNumericSuffix(it.folio, PREFIX_COTIZACION) } ?: 0L
            val maxCompraFromPurchases = purchases
                .maxOfOrNull { extractNumericSuffix(it.folio, PREFIX_COMPRA) } ?: 0L

            return listOf(
                FolioSequence(
                    sequenceKey = KEY_VENTA,
                    lastNumber = maxOf(existingMap[KEY_VENTA]?.lastNumber ?: 0L, maxVentaFromSales)
                ),
                FolioSequence(
                    sequenceKey = KEY_COTIZACION,
                    lastNumber = maxOf(existingMap[KEY_COTIZACION]?.lastNumber ?: 0L, maxCotFromSales)
                ),
                FolioSequence(
                    sequenceKey = KEY_COMPRA,
                    lastNumber = maxOf(existingMap[KEY_COMPRA]?.lastNumber ?: 0L, maxCompraFromPurchases)
                )
            )
        }

        internal fun readSqliteUserVersion(dbFile: File): Int? {
            if (!dbFile.exists() || dbFile.length() < 64L) return null
            return try {
                RandomAccessFile(dbFile, "r").use { raf ->
                    val header = ByteArray(16)
                    raf.seek(0L)
                    raf.readFully(header)
                    val magic = String(header, Charsets.US_ASCII)
                    if (!magic.startsWith("SQLite format 3")) return null
                    val versionBytes = ByteArray(4)
                    raf.seek(60L)
                    raf.readFully(versionBytes)
                    ByteBuffer.wrap(versionBytes).int
                }
            } catch (_: Exception) {
                null
            }
        }

        internal fun salvageLegacyV1DatabaseIfPresent(context: Context, dbName: String) {
            val dbFile = context.getDatabasePath(dbName)
            val userVersion = readSqliteUserVersion(dbFile) ?: return
            if (userVersion == 1) {
                val backupDir = File(context.filesDir, "db_safety_backup").apply { mkdirs() }
                val stamp = System.currentTimeMillis()
                val salvageMain = File(backupDir, "${dbName}_v1_$stamp.db")
                dbFile.copyTo(salvageMain, overwrite = true)
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    val sidecar = File("${dbFile.absolutePath}$suffix")
                    if (sidecar.exists()) {
                        sidecar.copyTo(File(backupDir, "${dbName}_v1_${stamp}.db$suffix"), overwrite = true)
                    }
                }
                throw IllegalStateException(
                    "Se detectó una base de datos heredada v1 sin migración verificable. Se guardó una copia de seguridad en ${salvageMain.absolutePath} para evitar borrado destructivo."
                )
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): CajaMaestraDatabase {
            return INSTANCE ?: synchronized(this) {
                salvageLegacyV1DatabaseIfPresent(context.applicationContext, "caja_maestra_db")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CajaMaestraDatabase::class.java,
                    "caja_maestra_db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.dao())
                    }
                }
            }
        }

        suspend fun populateInitialData(dao: CajaMaestraDao) {
            // Seed Employees
            dao.insertEmployee(
                Employee(
                    name = "Carlos Mendoza",
                    position = "Gerente General",
                    salary = 18500.0,
                    phone = "555-123-4567",
                    email = "carlos@cajamaestra.pos",
                    address = "Calle Reforma #45",
                    role = UserRole.DUENO,
                    pin = ""
                )
            )
            dao.insertEmployee(
                Employee(
                    name = "Laura Martínez",
                    position = "Administradora de Sucursal",
                    salary = 12000.0,
                    phone = "555-987-6543",
                    email = "laura@cajamaestra.pos",
                    address = "Av. Hidalgo #120",
                    role = UserRole.ADMINISTRADOR,
                    pin = ""
                )
            )
            dao.insertEmployee(
                Employee(
                    name = "Pedro Ramírez",
                    position = "Cajero / Vendedor",
                    salary = 8500.0,
                    phone = "555-456-7890",
                    email = "pedro@cajamaestra.pos",
                    address = "Col. Narvarte #78",
                    role = UserRole.TRABAJADOR,
                    pin = ""
                )
            )

            // Seed Clients
            dao.insertClient(
                Client(
                    name = "María Elena Soto",
                    company = "Restaurante El Rincón",
                    phone = "555-321-7890",
                    email = "maria.soto@elrincon.com",
                    address = "Calle Los Pinos #12",
                    creditBalance = 1450.0,
                    notes = "Cliente frecuente - Crédito autorizado a 15 días"
                )
            )
            dao.insertClient(
                Client(
                    name = "Juan Pablo Vargas",
                    company = "Consultoría Digital",
                    phone = "555-654-3210",
                    email = "jp.vargas@techsol.mx",
                    address = "Av. Insurgentes Sur #450",
                    creditBalance = 0.0,
                    notes = "Pago de contado inmediato"
                )
            )
            dao.insertClient(
                Client(
                    name = "Empresa Agropecuaria El Roble",
                    company = "Agro Roble S.A.",
                    phone = "555-888-9999",
                    email = "compras@agroroble.com",
                    address = "Carretera Nacional Km 14",
                    creditBalance = 3800.0,
                    notes = "Crédito a 30 días"
                )
            )

            // Seed Suppliers
            val sup1Id = dao.insertSupplier(
                Supplier(
                    name = "Distribuidora Mayorista del Centro",
                    company = "Grupo Distribuidor MX",
                    contactName = "Ing. Roberto Alarcón",
                    phone = "555-777-6655",
                    email = "ventas@distribuidoracentro.com",
                    address = "Bodega 4, Central de Abastos",
                    suppliedProducts = "Café gourmet en grano, Tazas térmicas, Insumos secos",
                    paymentTerms = "Crédito a 30 días",
                    notes = "Entregas programadas días martes y jueves por la mañana."
                )
            )
            val sup2Id = dao.insertSupplier(
                Supplier(
                    name = "Proveedora de Empaques y Papel Térmico",
                    company = "Pack & Supplies S.A.",
                    contactName = "Lic. Andrea Morales",
                    phone = "555-333-2211",
                    email = "contacto@packsupplies.com",
                    address = "Parque Industrial Norte Bodega #9",
                    suppliedProducts = "Bobinas de papel térmico 58mm, Cajas para empaque, Bolsas biodegradables",
                    paymentTerms = "Contado (Transferencia bancaria)",
                    notes = "Descuento del 5% por compras mayores a 50 paquetes."
                )
            )
            val sup3Id = dao.insertSupplier(
                Supplier(
                    name = "Tecnología POS y Accesorios MX",
                    company = "Hardware POS Solutions",
                    contactName = "Fernando Quiroz",
                    phone = "555-444-1234",
                    email = "soporte@poshardware.mx",
                    address = "Plaza de la Tecnología Local 204",
                    suppliedProducts = "Lectores de código de barras USB/Bluetooth, Impresoras térmicas 58mm",
                    paymentTerms = "Crédito a 15 días",
                    notes = "Garantía de 1 año directa en equipos."
                )
            )

            // Seed Purchases (Historial de compras a proveedores)
            dao.insertPurchase(
                Purchase(
                    folio = "OC-00201",
                    supplierId = sup1Id,
                    supplierName = "Distribuidora Mayorista del Centro",
                    total = 4250.0,
                    date = System.currentTimeMillis() - 3600000L * 72,
                    isCredit = true,
                    notes = "Lote de 50 paquetes de Café Gourmet 500g",
                    itemsJson = """[{"name":"Café Gourmet de Altura 500g","quantity":50,"unitPrice":85.0,"subtotal":4250.0,"unit":"Pza"}]"""
                )
            )
            dao.insertPurchase(
                Purchase(
                    folio = "OC-00202",
                    supplierId = sup2Id,
                    supplierName = "Proveedora de Empaques y Papel Térmico",
                    total = 1800.0,
                    date = System.currentTimeMillis() - 3600000L * 36,
                    isCredit = false,
                    notes = "20 Packs Papel Térmico 58mm",
                    itemsJson = """[{"name":"Papel Térmico 58mm (Pack 10)","quantity":20,"unitPrice":90.0,"subtotal":1800.0,"unit":"Paq"}]"""
                )
            )

            // Seed Inventory Items
            // Productos
            dao.insertItem(
                InventoryItem(
                    name = "Café Gourmet de Altura 500g",
                    code = "7501001",
                    costPrice = 85.0,
                    salePrice = 145.0,
                    stock = 34.0,
                    minStock = 8.0,
                    category = "Alimentos y Bebidas",
                    unit = "Pza",
                    type = ItemType.PRODUCTO
                )
            )
            dao.insertItem(
                InventoryItem(
                    name = "Taza Cerámica Térmica Negra",
                    code = "7501002",
                    costPrice = 45.0,
                    salePrice = 95.0,
                    stock = 18.0,
                    minStock = 5.0,
                    category = "Accesorios",
                    unit = "Pza",
                    type = ItemType.PRODUCTO
                )
            )
            dao.insertItem(
                InventoryItem(
                    name = "Papel Térmico para Recibo 58mm (Pack 10)",
                    code = "7501003",
                    costPrice = 90.0,
                    salePrice = 160.0,
                    stock = 4.0, // Low stock on purpose to trigger alerts!
                    minStock = 6.0,
                    category = "Insumos POS",
                    unit = "Paq",
                    type = ItemType.PRODUCTO,
                    isRawMaterial = true
                )
            )
            dao.insertItem(
                InventoryItem(
                    name = "Lector de Código de Barras USB / BT",
                    code = "7501004",
                    costPrice = 320.0,
                    salePrice = 580.0,
                    stock = 12.0,
                    minStock = 3.0,
                    category = "Tecnología POS",
                    unit = "Pza",
                    type = ItemType.PRODUCTO
                )
            )

            // Manufactura (Productos elaborados / combos)
            dao.insertItem(
                InventoryItem(
                    name = "Kit Punto de Venta Emprendedor (Lector + Papel + Setup)",
                    code = "MAN-001",
                    costPrice = 450.0,
                    salePrice = 890.0,
                    stock = 7.0,
                    minStock = 2.0,
                    category = "Kits y Paquetes",
                    unit = "Kit",
                    type = ItemType.MANUFACTURA,
                    notes = "Elaborado con 1 Lector + 1 Pack Papel + Configuración"
                )
            )
            dao.insertItem(
                InventoryItem(
                    name = "Combo Cafetería Premium (Café + Taza + Empaque)",
                    code = "MAN-002",
                    costPrice = 135.0,
                    salePrice = 250.0,
                    stock = 15.0,
                    minStock = 4.0,
                    category = "Combos",
                    unit = "Kit",
                    type = ItemType.MANUFACTURA
                )
            )

            // Servicios
            dao.insertItem(
                InventoryItem(
                    name = "Instalación y Configuración POS en Sitio",
                    code = "SRV-001",
                    costPrice = 100.0,
                    salePrice = 450.0,
                    stock = 999.0,
                    minStock = 0.0,
                    category = "Soporte Técnico",
                    unit = "Servicio",
                    type = ItemType.SERVICIO
                )
            )
            dao.insertItem(
                InventoryItem(
                    name = "Mantenimiento Preventivo de Impresoras Térmicas",
                    code = "SRV-002",
                    costPrice = 50.0,
                    salePrice = 280.0,
                    stock = 999.0,
                    minStock = 0.0,
                    category = "Mantenimiento",
                    unit = "Servicio",
                    type = ItemType.SERVICIO
                )
            )

            // Merma / Desperdicio
            dao.insertItem(
                InventoryItem(
                    name = "Taza Cerámica con Fisura de Fábrica",
                    code = "MER-001",
                    costPrice = 45.0,
                    salePrice = 0.0,
                    stock = 2.0,
                    minStock = 0.0,
                    category = "Mermas",
                    unit = "Pza",
                    type = ItemType.PRODUCTO,
                    isWaste = true,
                    notes = "Producto dañado en transporte de proveedor"
                )
            )

            // Seed Sales
            val now = System.currentTimeMillis()
            dao.insertSale(
                Sale(
                    folio = "F-00101",
                    type = SaleType.VENTA,
                    status = SaleStatus.PAGADA,
                    customerName = "María Elena Soto",
                    subtotal = 385.0,
                    discount = 0.0,
                    total = 385.0,
                    paidAmount = 400.0,
                    changeAmount = 15.0,
                    paymentMethod = "Efectivo",
                    date = now - 3600000L * 4,
                    sellerName = "Carlos Mendoza",
                    itemsJson = """[{"name":"Café Gourmet de Altura 500g","quantity":2,"unitPrice":145.0,"subtotal":290.0,"unit":"Pza"},{"name":"Taza Cerámica Térmica Negra","quantity":1,"unitPrice":95.0,"subtotal":95.0,"unit":"Pza"}]"""
                )
            )
            dao.insertSale(
                Sale(
                    folio = "F-00102",
                    type = SaleType.VENTA,
                    status = SaleStatus.PAGADA,
                    customerName = "Juan Pablo Vargas",
                    subtotal = 890.0,
                    discount = 40.0,
                    total = 850.0,
                    paidAmount = 850.0,
                    changeAmount = 0.0,
                    paymentMethod = "Tarjeta",
                    date = now - 3600000L * 2,
                    sellerName = "Pedro Ramírez",
                    itemsJson = """[{"name":"Kit Punto de Venta Emprendedor","quantity":1,"unitPrice":890.0,"subtotal":890.0,"unit":"Kit"}]"""
                )
            )
            dao.insertSale(
                Sale(
                    folio = "F-00103",
                    type = SaleType.VENTA,
                    status = SaleStatus.CREDITO,
                    customerName = "Empresa Agropecuaria El Roble",
                    subtotal = 1450.0,
                    discount = 0.0,
                    total = 1450.0,
                    paidAmount = 0.0,
                    changeAmount = 0.0,
                    paymentMethod = "Crédito 15 días",
                    date = now - 3600000L * 24,
                    sellerName = "Laura Martínez",
                    itemsJson = """[{"name":"Café Gourmet de Altura 500g","quantity":10,"unitPrice":145.0,"subtotal":1450.0,"unit":"Pza"}]"""
                )
            )
            dao.insertSale(
                Sale(
                    folio = "COT-00045",
                    type = SaleType.COTIZACION,
                    status = SaleStatus.PENDIENTE,
                    customerName = "Restaurante El Rincón",
                    subtotal = 2190.0,
                    discount = 100.0,
                    total = 2090.0,
                    paidAmount = 0.0,
                    changeAmount = 0.0,
                    paymentMethod = "Por definir",
                    date = now - 3600000L * 12,
                    sellerName = "Carlos Mendoza",
                    itemsJson = """[{"name":"Kit Punto de Venta Emprendedor","quantity":2,"unitPrice":890.0,"subtotal":1780.0,"unit":"Kit"},{"name":"Papel Térmico 58mm","quantity":3,"unitPrice":160.0,"subtotal":480.0,"unit":"Paq"}]"""
                )
            )

            // Seed Extra Movements
            dao.insertExtraMovement(
                ExtraMovement(
                    type = MovementType.GASTO,
                    concept = "Pago de Servicio de Energía Eléctrica",
                    category = "Servicios Básicos",
                    amount = 620.0,
                    date = now - 3600000L * 48,
                    notes = "Recibo bimestre CFE"
                )
            )
            dao.insertExtraMovement(
                ExtraMovement(
                    type = MovementType.INGRESO,
                    concept = "Comisión por cobro de servicios externos",
                    category = "Comisiones",
                    amount = 180.0,
                    date = now - 3600000L * 8,
                    notes = "Cobro de servicios de corresponsalía"
                )
            )

            // Seed Rentals (Equipment and furniture rentals)
            dao.insertRentalLoan(
                RentalLoan(
                    isRental = true,
                    contactName = "Cafetería La Esmeralda",
                    itemOrReason = "Renta de Impresora Térmica Portátil 58mm",
                    amount = 350.0,
                    depositAmount = 500.0,
                    startDate = now - 3600000L * 72,
                    dueDate = now + 3600000L * 96,
                    isReturnedOrPaid = false,
                    notes = "Evento de fin de semana"
                )
            )

            // Seed initial atomic folio high-water marks matching historical seed records
            dao.insertAllFolioSequences(
                listOf(
                    FolioSequence(KEY_VENTA, 103L),
                    FolioSequence(KEY_COTIZACION, 45L),
                    FolioSequence(KEY_COMPRA, 202L)
                )
            )
        }
    }
}
