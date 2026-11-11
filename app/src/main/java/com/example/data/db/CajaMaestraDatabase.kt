package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        InventoryItem::class,
        Sale::class,
        Purchase::class,
        ExtraMovement::class,
        RentalLoan::class,
        Client::class,
        Supplier::class,
        Employee::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CajaMaestraDatabase : RoomDatabase() {
    abstract fun dao(): CajaMaestraDao

    companion object {
        @Volatile
        private var INSTANCE: CajaMaestraDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): CajaMaestraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CajaMaestraDatabase::class.java,
                    "caja_maestra_db"
                )
                    .fallbackToDestructiveMigration()
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
                    pin = "1234"
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
                    pin = "2222"
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
                    pin = "3333"
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
                    type = ItemType.PRODUCTO
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
        }
    }
}
