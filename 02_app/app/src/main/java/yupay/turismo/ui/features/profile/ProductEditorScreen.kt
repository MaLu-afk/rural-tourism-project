package yupay.turismo.ui.features.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.data.local.DiscountType
import yupay.turismo.data.local.Product
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.UnsavedChangesDialog
import yupay.turismo.utils.UiTranslations
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(
    productId: Int?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"
    
    val businessCategory = settings?.businessCategory ?: "Varios"

    var product by remember { mutableStateOf<Product?>(null) }
    var isLoading by remember { mutableStateOf(productId != null) }

    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("S/") }
    var category by remember { mutableStateOf("") }
    var discountValue by remember { mutableStateOf("") }
    var discountType by remember { mutableStateOf(DiscountType.PERCENTAGE) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }

    var showExitDialog by remember { mutableStateOf(false) }
    var showInvalidDateWarning by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf<Pair<String, Long?>?>(null) } // "start" or "end"

    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // Adjustment for UTC offset to ensure today is selectable
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                
                val todayUtc = calendar.timeInMillis - calendar.timeZone.getOffset(calendar.timeInMillis)
                return utcTimeMillis >= todayUtc
            }
        }
    )

    LaunchedEffect(productId) {
        if (productId != null) {
            val p = viewModel.getProductById(productId)
            p?.let {
                product = it
                name = it.name
                price = it.basePrice.toString()
                currency = it.currency
                category = it.category
                discountValue = if (it.discountValue > 0) it.discountValue.toString() else ""
                discountType = it.discountType
                startDate = it.discountStartDate
                endDate = it.discountEndDate
            }
            isLoading = false
        } else {
            category = if (businessCategory != "Varios") businessCategory else "Alimentación"
        }
    }

    val hasChanges = remember(name, price, currency, category, discountValue, discountType, startDate, endDate, product) {
        if (product == null) {
            name.isNotEmpty() || price.isNotEmpty() || discountValue.isNotEmpty()
        } else {
            name != product!!.name || 
            price != product!!.basePrice.toString() || 
            currency != product!!.currency || 
            category != product!!.category || 
            discountValue != (if (product!!.discountValue > 0) product!!.discountValue.toString() else "") || 
            discountType != product!!.discountType || 
            startDate != product!!.discountStartDate || 
            endDate != product!!.discountEndDate
        }
    }

    BackHandler(enabled = hasChanges) {
        showExitDialog = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = UiTranslations.getString(context, if (productId == null) "catalog_add_product" else "catalog_edit_product", language),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { if (hasChanges) showExitDialog = true else onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nombre
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(UiTranslations.getString(context, "catalog_product_name", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Precio
                    OutlinedTextField(
                        value = price,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) price = it },
                        label = { Text(UiTranslations.getString(context, "catalog_product_price", language)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        singleLine = true
                    )

                    // Moneda
                    var isCurrencyExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = isCurrencyExpanded,
                        onExpandedChange = { isCurrencyExpanded = !isCurrencyExpanded },
                        modifier = Modifier.width(100.dp)
                    ) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(UiTranslations.getString(context, "catalog_product_currency", language)) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCurrencyExpanded) }
                        )
                        ExposedDropdownMenu(expanded = isCurrencyExpanded, onDismissRequest = { isCurrencyExpanded = false }) {
                            listOf("S/", "$", "€").forEach { curr ->
                                DropdownMenuItem(text = { Text(curr) }, onClick = { currency = curr; isCurrencyExpanded = false })
                            }
                        }
                    }
                }

                // Categoría (Sector)
                if (businessCategory == "Varios") {
                    var isCatExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = isCatExpanded,
                        onExpandedChange = { isCatExpanded = !isCatExpanded }
                    ) {
                        OutlinedTextField(
                            value = UiTranslations.translateService(category, language, context),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(UiTranslations.getString(context, "catalog_product_category", language)) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCatExpanded) }
                        )
                        ExposedDropdownMenu(expanded = isCatExpanded, onDismissRequest = { isCatExpanded = false }) {
                            listOf("Hospedaje", "Alimentación", "Artesanía").forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(UiTranslations.translateService(cat, language, context)) },
                                    onClick = { category = cat; isCatExpanded = false }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = UiTranslations.translateService(category, language, context),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(UiTranslations.getString(context, "catalog_product_category", language)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = false
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Descuento
                Text(UiTranslations.getString(context, "catalog_discount_active", language), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discountValue,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) discountValue = it },
                        label = { Text(UiTranslations.getString(context, "discount_label", language)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        singleLine = true,
                        suffix = { Text(if (discountType == DiscountType.FIXED) currency else "%") }
                    )
                    IconButton(
                        onClick = { discountType = if (discountType == DiscountType.FIXED) DiscountType.PERCENTAGE else DiscountType.FIXED },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Fechas
                DateSelectionRow(
                    label = UiTranslations.getString(context, "catalog_discount_start", language),
                    date = startDate,
                    language = language,
                    onDateChange = { startDate = it }
                )

                DateSelectionRow(
                    label = UiTranslations.getString(context, "catalog_discount_end", language),
                    date = endDate,
                    language = language,
                    onDateChange = { endDate = it }
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val basePrice = price.toDoubleOrNull() ?: 0.0
                        val dValue = discountValue.toDoubleOrNull() ?: 0.0
                        
                        // Validar fechas antes de guardar
                        val areDatesValid = (startDate == null && endDate == null) || 
                                          (startDate != null && endDate != null && startDate!! <= endDate!!)
                        
                        if (!areDatesValid && discountValue.isNotEmpty() && dValue > 0) {
                            showInvalidDateWarning = true
                        } else {
                            val finalStartDate = if (areDatesValid) startDate else null
                            val finalEndDate = if (areDatesValid) endDate else null
                            
                            // Al editar se parte del producto existente con .copy(...) para PRESERVAR
                            // id/uuid/remoteId/createdAt (descartarlos provocaba un duplicado con datos
                            // viejos vía la nube). Se actualiza lastModified para que el merge P2P por
                            // lastModified haga ganar este cambio.
                            val existing = product
                            val newProduct = if (existing != null) {
                                existing.copy(
                                    name = name,
                                    basePrice = basePrice,
                                    currency = currency,
                                    category = category,
                                    discountValue = dValue,
                                    discountType = discountType,
                                    discountStartDate = finalStartDate,
                                    discountEndDate = finalEndDate,
                                    lastModified = System.currentTimeMillis()
                                )
                            } else {
                                Product(
                                    name = name,
                                    basePrice = basePrice,
                                    currency = currency,
                                    category = category,
                                    discountValue = dValue,
                                    discountType = discountType,
                                    discountStartDate = finalStartDate,
                                    discountEndDate = finalEndDate
                                )
                            }
                            if (productId == null) viewModel.addProduct(newProduct) else viewModel.updateProduct(newProduct)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = name.isNotBlank() && price.isNotEmpty()
                ) {
                    Text(UiTranslations.getString(context, "btn_save", language), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showInvalidDateWarning) {
            AlertDialog(
                onDismissRequest = { showInvalidDateWarning = false },
                title = { Text(UiTranslations.getString(context, "product_invalid_dates_title", language)) },
                text = { Text(UiTranslations.getString(context, "product_invalid_dates_desc", language)) },
                confirmButton = {
                    TextButton(onClick = {
                        val basePrice = price.toDoubleOrNull() ?: 0.0
                        val dValue = discountValue.toDoubleOrNull() ?: 0.0
                        // Igual que en el guardado normal: al editar se preserva la identidad con .copy().
                        val existing = product
                        val newProduct = if (existing != null) {
                            existing.copy(
                                name = name,
                                basePrice = basePrice,
                                currency = currency,
                                category = category,
                                discountValue = dValue,
                                discountType = discountType,
                                discountStartDate = null,
                                discountEndDate = null,
                                lastModified = System.currentTimeMillis()
                            )
                        } else {
                            Product(
                                name = name,
                                basePrice = basePrice,
                                currency = currency,
                                category = category,
                                discountValue = dValue,
                                discountType = discountType,
                                discountStartDate = null,
                                discountEndDate = null
                            )
                        }
                        if (productId == null) viewModel.addProduct(newProduct) else viewModel.updateProduct(newProduct)
                        showInvalidDateWarning = false
                        onBack()
                    }) {
                        Text(UiTranslations.getString(context, "btn_continue", language))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInvalidDateWarning = false }) {
                        Text(UiTranslations.getString(context, "btn_cancel", language))
                    }
                }
            )
        }

        if (showExitDialog) {
            UnsavedChangesDialog(
                language = language,
                onContinueEditing = { showExitDialog = false },
                onExitWithoutSaving = onBack,
                onDismiss = { showExitDialog = false }
            )
        }

        if (showDatePicker != null) {
            val titleKey = if (showDatePicker?.first == "start") "catalog_discount_start" else "catalog_discount_end"
            DatePickerDialog(
                onDismissRequest = { showDatePicker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedDateUtc = datePickerState.selectedDateMillis
                        if (selectedDateUtc != null) {
                            // Convert UTC selection to local date at 00:00:00
                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            calendar.timeInMillis = selectedDateUtc
                            
                            val localCalendar = Calendar.getInstance()
                            localCalendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                            localCalendar.set(Calendar.MILLISECOND, 0)
                            
                            if (showDatePicker?.first == "start") {
                                startDate = localCalendar.timeInMillis
                            } else {
                                endDate = localCalendar.timeInMillis
                            }
                        }
                        showDatePicker = null
                    }) {
                        Text(UiTranslations.getString(context, "btn_confirm", language))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = null }) {
                        Text(UiTranslations.getString(context, "btn_cancel", language))
                    }
                }
            ) {
                DatePicker(state = datePickerState, title = { Text(UiTranslations.getString(context, titleKey, language), modifier = Modifier.padding(16.dp)) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionRow(label: String, date: Long?, language: String, onDateChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var showModal by remember { mutableStateOf(false) }
    
    var textFieldValue by remember(date) {
        val dateStr = date?.let { sdf.format(Date(it)) } ?: ""
        mutableStateOf(TextFieldValue(dateStr, TextRange(dateStr.length)))
    }

    val isError = remember(textFieldValue.text) {
        val text = textFieldValue.text
        if (text.isEmpty()) return@remember false
        if (text.length < 10) return@remember true
        
        try {
            val parts = text.split("/")
            if (parts.size != 3) return@remember true
            val day = parts[0].toInt()
            val month = parts[1].toInt()
            val year = parts[2].toInt()
            
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            
            if (day !in 1..31) return@remember true
            if (month !in 1..12) return@remember true
            if (year < currentYear) return@remember true
            
            val tempCal = Calendar.getInstance()
            tempCal.set(year, month - 1, day, 0, 0, 0)
            tempCal.set(Calendar.MILLISECOND, 0)
            
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            if (day > tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)) return@remember true
            
            tempCal.timeInMillis < today.timeInMillis
        } catch (e: Exception) {
            true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val text = newValue.text
                if (text.length <= 10 && text.all { it.isDigit() || it == '/' }) {
                    var formatted = text.replace("/", "")
                    if (formatted.length >= 2) formatted = formatted.substring(0, 2) + "/" + formatted.substring(2)
                    if (formatted.length >= 5) formatted = formatted.substring(0, 5) + "/" + formatted.substring(5)
                    
                    textFieldValue = newValue.copy(text = formatted, selection = TextRange(formatted.length))
                    
                    if (formatted.length == 10 && !isError) {
                        try {
                            val parsed = sdf.parse(formatted)
                            onDateChange(parsed?.time)
                        } catch (e: Exception) {}
                    } else if (formatted.isEmpty()) {
                        onDateChange(null)
                    }
                }
            },
            placeholder = { Text("DD/MM/YYYY") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = isError,
            supportingText = if (isError) {
                { Text(UiTranslations.getString(context, "product_date_invalid_past", language), color = MaterialTheme.colorScheme.error) }
            } else null,
            trailingIcon = {
                IconButton(onClick = { showModal = true }) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            singleLine = true
        )
    }

    if (showModal) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showModal = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { utcMillis ->
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = utcMillis
                        val localCal = Calendar.getInstance()
                        localCal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                        localCal.set(Calendar.MILLISECOND, 0)
                        
                        val today = Calendar.getInstance()
                        today.set(Calendar.HOUR_OF_DAY, 0)
                        today.set(Calendar.MINUTE, 0)
                        today.set(Calendar.SECOND, 0)
                        today.set(Calendar.MILLISECOND, 0)
                        
                        if (localCal.timeInMillis < today.timeInMillis) {
                            // Show toast or warning
                            android.widget.Toast.makeText(context, UiTranslations.getString(context, "product_date_not_before_today", language), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            onDateChange(localCal.timeInMillis)
                            showModal = false
                        }
                    }
                }) {
                    Text(UiTranslations.getString(context, "btn_confirm", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { showModal = false }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}
