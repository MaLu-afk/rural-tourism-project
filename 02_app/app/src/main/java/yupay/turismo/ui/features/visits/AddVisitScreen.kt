package yupay.turismo.ui.features.visits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextDecoration
import yupay.turismo.data.local.DiscountType
import yupay.turismo.data.local.Product
import yupay.turismo.data.local.SelectedProduct
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.UnsavedChangesDialog
import yupay.turismo.utils.UiTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVisitScreen(
    viewModel: MainViewModel,
    language: String,
    onBack: () -> Unit,
    onSave: (String, String, List<SelectedProduct>, Double, Double, DiscountType, Double) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val products by viewModel.allProducts.collectAsState(initial = emptyList())
    
    var selectedNationality by remember { mutableStateOf<Country?>(null) }
    var isNationalityExpanded by remember { mutableStateOf(false) }
    var nationalitySearch by remember { mutableStateOf("") }
    
    var productSearch by remember { mutableStateOf("") }
    val filteredProducts = remember(products, productSearch) {
        products.filter { it.name.contains(productSearch, ignoreCase = true) }
    }
    
    val selectedQuantities = remember { mutableStateMapOf<Int, Int>() }
    
    var discountValue by remember { mutableStateOf("") }
    var discountType by remember { mutableStateOf(DiscountType.FIXED) }
    var showExitDialog by remember { mutableStateOf(false) }

    val hasChanges = selectedNationality != null || selectedQuantities.isNotEmpty() || discountValue.isNotEmpty()

    val countries = listOf(
        Country("Perú", "🇵🇪"), Country("Argentina", "🇦🇷"), Country("Brasil", "🇧🇷"),
        Country("Chile", "🇨🇱"), Country("Colombia", "🇨🇴"), Country("Ecuador", "🇪🇨"),
        Country("México", "🇲🇽"), Country("España", "🇪🇸"), Country("Estados Unidos", "🇺🇸"),
        Country("Francia", "🇫🇷"), Country("Alemania", "🇩🇪"), Country("Reino Unido", "🇬🇧")
    )

    val settings by viewModel.appSettings.collectAsState()
    val prefCurrency = settings?.preferredCurrency ?: "S/"
    val usdRate = settings?.usdExchangeRate ?: 3.8
    val eurRate = settings?.eurExchangeRate ?: 4.1

    fun convertPrice(price: Double, from: String): Double {
        if (from == prefCurrency) return price
        
        // Convert to Soles first
        val priceInSoles = when(from) {
            "$" -> price * usdRate
            "€" -> price * eurRate
            else -> price
        }
        
        // Convert from Soles to preferred
        return when(prefCurrency) {
            "$" -> priceInSoles / usdRate
            "€" -> priceInSoles / eurRate
            else -> priceInSoles
        }
    }

    val subtotal = products.sumOf { (selectedQuantities[it.id] ?: 0) * convertPrice(it.getActivePrice(), it.currency) }
    val discountNum = discountValue.toDoubleOrNull() ?: 0.0
    val total = if (discountType == DiscountType.FIXED) {
        (subtotal - discountNum).coerceAtLeast(0.0)
    } else {
        (subtotal * (1 - discountNum / 100.0)).coerceAtLeast(0.0)
    }

    BackHandler(enabled = hasChanges) {
        showExitDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiTranslations.getString(context, "add_visit_title", language)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) showExitDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Left Column: Nationality and Products
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = UiTranslations.getString(context, "add_visit_country_label", language),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = isNationalityExpanded,
                        onExpandedChange = { isNationalityExpanded = !isNationalityExpanded }
                    ) {
                        OutlinedTextField(
                            value = if (selectedNationality != null && !isNationalityExpanded) 
                                "${selectedNationality?.flag} ${selectedNationality?.name}" else nationalitySearch,
                            onValueChange = { nationalitySearch = it; isNationalityExpanded = true },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            placeholder = { Text(UiTranslations.getString(context, "search_country_hint", language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNationalityExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { 
                        // Intelligent selection on Check
                        val filtered = countries.filter { it.name.contains(nationalitySearch, true) }
                        if (filtered.size == 1) {
                            selectedNationality = filtered.first()
                            nationalitySearch = ""
                            isNationalityExpanded = false
                        }
                        focusManager.clearFocus() 
                    })
                        )
                        ExposedDropdownMenu(
                            expanded = isNationalityExpanded,
                            onDismissRequest = { isNationalityExpanded = false }
                        ) {
                            countries.filter { it.name.contains(nationalitySearch, true) }.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text("${country.flag} ${country.name}") },
                                    onClick = { 
                                        selectedNationality = country
                                        nationalitySearch = ""
                                        isNationalityExpanded = false 
                                        focusManager.clearFocus()
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = UiTranslations.getString(context, "product_label", language),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = productSearch,
                        onValueChange = { productSearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(UiTranslations.getString(context, "search_product_hint", language)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { 
                        // Intelligent selection on Check
                        val filtered = countries.filter { it.name.contains(nationalitySearch, true) }
                        if (filtered.size == 1) {
                            selectedNationality = filtered.first()
                            nationalitySearch = ""
                            isNationalityExpanded = false
                        }
                        focusManager.clearFocus() 
                    })
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            filteredProducts.forEach { product ->
                                ProductSelectionRow(
                                    product = product,
                                    quantity = selectedQuantities[product.id] ?: 0,
                                    preferredCurrency = prefCurrency,
                                    usdRate = usdRate,
                                    eurRate = eurRate,
                                    onQuantityChange = { selectedQuantities[product.id] = it }
                                )
                            }
                        }
                    }
                }

                // Right Column: Summary and Save
                Column(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(UiTranslations.getString(context, "subtotal_label", language))
                                Text("$prefCurrency ${String.format("%.2f", subtotal)}", fontWeight = FontWeight.Bold)
                            }
                            
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(UiTranslations.getString(context, "discount_label", language), modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = discountValue,
                                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) discountValue = it },
                                    modifier = Modifier.width(90.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { 
                        val filtered = countries.filter { it.name.contains(nationalitySearch, true) }
                        if (filtered.size == 1) {
                            selectedNationality = filtered.first()
                            nationalitySearch = ""
                            isNationalityExpanded = false
                        }
                        focusManager.clearFocus() 
                    }),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    suffix = { Text(if (discountType == DiscountType.FIXED) prefCurrency else "%") }
                                )
                                IconButton(onClick = { 
                                    discountType = if (discountType == DiscountType.FIXED) DiscountType.PERCENTAGE else DiscountType.FIXED 
                                }) {
                                    Icon(
                                        Icons.Default.SwapHoriz, 
                                        contentDescription = UiTranslations.getString(context, "content_desc_switch_discount", language), 
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(UiTranslations.getString(context, "total_label", language), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                Text("$prefCurrency ${String.format("%.2f", total)}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val selProducts = products.filter { (selectedQuantities[it.id] ?: 0) > 0 }.map { 
                                SelectedProduct(
                                    id = it.id, 
                                    name = it.name, 
                                    originalPrice = convertPrice(it.basePrice, it.currency),
                                    priceAtSale = convertPrice(it.getActivePrice(), it.currency), 
                                    quantity = selectedQuantities[it.id]!!,
                                    hasDiscount = it.getActivePrice() < it.basePrice,
                                    currency = prefCurrency
                                )
                            }
                            onSave(selectedNationality?.name ?: "", selectedNationality?.flag ?: "", selProducts, subtotal, discountNum, discountType, total)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = selectedNationality != null && subtotal > 0
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(UiTranslations.getString(context, "add_visit_save", language), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Nacionalidad
                Text(
                    text = UiTranslations.getString(context, "add_visit_country_label", language),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = isNationalityExpanded,
                    onExpandedChange = { isNationalityExpanded = !isNationalityExpanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedNationality != null && !isNationalityExpanded) 
                            "${selectedNationality?.flag} ${selectedNationality?.name}" else nationalitySearch,
                        onValueChange = { nationalitySearch = it; isNationalityExpanded = true },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        placeholder = { Text(UiTranslations.getString(context, "search_country_hint", language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNationalityExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { 
                        // Intelligent selection on Check
                        val filtered = countries.filter { it.name.contains(nationalitySearch, true) }
                        if (filtered.size == 1) {
                            selectedNationality = filtered.first()
                            nationalitySearch = ""
                            isNationalityExpanded = false
                        }
                        focusManager.clearFocus() 
                    })
                    )
                    ExposedDropdownMenu(
                        expanded = isNationalityExpanded,
                        onDismissRequest = { isNationalityExpanded = false }
                    ) {
                        countries.filter { it.name.contains(nationalitySearch, true) }.forEach { country ->
                            DropdownMenuItem(
                                text = { Text("${country.flag} ${country.name}") },
                                onClick = { 
                                    selectedNationality = country
                                    nationalitySearch = ""
                                    isNationalityExpanded = false 
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. Sección de Productos con Buscador
                Text(
                    text = UiTranslations.getString(context, "product_label", language),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Buscador de productos estilizado
                OutlinedTextField(
                    value = productSearch,
                    onValueChange = { productSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(UiTranslations.getString(context, "search_product_hint", language)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = if (productSearch.isNotEmpty()) {
                        { IconButton(onClick = { productSearch = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { 
                        val filtered = countries.filter { it.name.contains(nationalitySearch, true) }
                        if (filtered.size == 1) {
                            selectedNationality = filtered.first()
                            nationalitySearch = ""
                            isNationalityExpanded = false
                        }
                        focusManager.clearFocus() 
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Lista de productos con scroll interno si hay más de 3
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        filteredProducts.forEach { product ->
                            ProductSelectionRow(
                                product = product,
                                quantity = selectedQuantities[product.id] ?: 0,
                                preferredCurrency = prefCurrency,
                                usdRate = usdRate,
                                eurRate = eurRate,
                                onQuantityChange = { selectedQuantities[product.id] = it }
                            )
                            if (product != filteredProducts.last()) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                        if (filteredProducts.isEmpty()) {
                            Text(
                                text = UiTranslations.getString(context, "catalog_empty", language),
                                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Resumen y Descuento
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(UiTranslations.getString(context, "subtotal_label", language), style = MaterialTheme.typography.bodyLarge)
                            Text("$prefCurrency ${String.format("%.2f", subtotal)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = UiTranslations.getString(context, "discount_label", language),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            OutlinedTextField(
                                value = discountValue,
                                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) discountValue = it },
                                modifier = Modifier.width(110.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { 
                        val filtered = countries.filter { it.name.contains(nationalitySearch, true) }
                        if (filtered.size == 1) {
                            selectedNationality = filtered.first()
                            nationalitySearch = ""
                            isNationalityExpanded = false
                        }
                        focusManager.clearFocus() 
                    }),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                suffix = { 
                                    Text(
                                        text = if (discountType == DiscountType.FIXED) prefCurrency else "%",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    ) 
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            IconButton(
                                onClick = { 
                                    discountType = if (discountType == DiscountType.FIXED) DiscountType.PERCENTAGE else DiscountType.FIXED 
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz, 
                                    contentDescription = UiTranslations.getString(context, "content_desc_switch_discount", language), 
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(UiTranslations.getString(context, "total_label", language), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                            Text(
                                text = "$prefCurrency ${String.format("%.2f", total)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 4. Botón Guardar
                Button(
                    onClick = {
                        val selProducts = products.filter { (selectedQuantities[it.id] ?: 0) > 0 }.map { 
                            SelectedProduct(
                                id = it.id, 
                                name = it.name, 
                                originalPrice = convertPrice(it.basePrice, it.currency),
                                priceAtSale = convertPrice(it.getActivePrice(), it.currency), 
                                quantity = selectedQuantities[it.id]!!,
                                hasDiscount = it.getActivePrice() < it.basePrice,
                                currency = prefCurrency
                            )
                        }
                        onSave(selectedNationality?.name ?: "", selectedNationality?.flag ?: "", selProducts, subtotal, discountNum, discountType, total)
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    enabled = selectedNationality != null && subtotal > 0,
                    elevation = ButtonDefaults.buttonColors().let { ButtonDefaults.elevatedButtonElevation(defaultElevation = 4.dp) }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = UiTranslations.getString(context, "add_visit_save", language),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showExitDialog) {
            UnsavedChangesDialog(
                language = language,
                onContinueEditing = { showExitDialog = false },
                onExitWithoutSaving = onBack,
                onDismiss = { showExitDialog = false }
            )
        }
    }
}

data class Country(val name: String, val flag: String)

@Composable
fun ProductSelectionRow(product: Product, quantity: Int, preferredCurrency: String, usdRate: Double, eurRate: Double, onQuantityChange: (Int) -> Unit) {
    val activePriceRaw = product.getActivePrice()
    
    fun convert(price: Double, from: String): Double {
        if (from == preferredCurrency) return price
        val priceInSoles = when(from) {
            "$" -> price * usdRate
            "€" -> price * eurRate
            else -> price
        }
        return when(preferredCurrency) {
            "$" -> priceInSoles / usdRate
            "€" -> priceInSoles / eurRate
            else -> priceInSoles
        }
    }

    val basePrice = convert(product.basePrice, product.currency)
    val activePrice = convert(activePriceRaw, product.currency)
    val hasDiscount = activePriceRaw < product.basePrice

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasDiscount) {
                    Text(
                        text = "$preferredCurrency ${String.format("%.2f", basePrice)}", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$preferredCurrency ${String.format("%.2f", activePrice)}", 
                        color = Color(0xFFE53935),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "$preferredCurrency ${String.format("%.2f", basePrice)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(horizontal = 4.dp)
        ) {
            IconButton(
                onClick = { if (quantity > 0) onQuantityChange(quantity - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = quantity.toString(),
                modifier = Modifier.padding(horizontal = 12.dp),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (quantity > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = { onQuantityChange(quantity + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
