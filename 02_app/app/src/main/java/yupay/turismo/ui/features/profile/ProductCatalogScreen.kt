package yupay.turismo.ui.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.data.local.AppSettings
import yupay.turismo.data.local.Product
import yupay.turismo.ui.MainViewModel
import yupay.turismo.utils.CurrencyUtils
import yupay.turismo.utils.UiTranslations

enum class SortOption {
    NAME, PRICE, CREATION_DATE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCatalogScreen(
    viewModel: MainViewModel,
    language: String,
    isSetupFlow: Boolean = false,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onNavigateToEditor: (Int?) -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.allProducts.collectAsState(initial = emptyList())
    val settings by viewModel.appSettings.collectAsState()
    val prefCurrency = settings?.preferredCurrency ?: "S/"
    val usdRate = settings?.usdExchangeRate ?: 3.8
    val eurRate = settings?.eurExchangeRate ?: 4.1
    
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }
    var sortAscending by remember { mutableStateOf(true) }

    val filteredProducts = remember(products, searchQuery, sortOption, sortAscending, prefCurrency, usdRate, eurRate) {
        val filtered = products.filter { it.name.contains(searchQuery, ignoreCase = true) }
        when (sortOption) {
            SortOption.NAME -> if (sortAscending) filtered.sortedBy { it.name } else filtered.sortedByDescending { it.name }
            SortOption.PRICE -> if (sortAscending) {
                filtered.sortedBy { CurrencyUtils.convert(it.getActivePrice(), it.currency, prefCurrency, usdRate, eurRate) }
            } else {
                filtered.sortedByDescending { CurrencyUtils.convert(it.getActivePrice(), it.currency, prefCurrency, usdRate, eurRate) }
            }
            SortOption.CREATION_DATE -> if (sortAscending) filtered.sortedBy { it.createdAt } else filtered.sortedByDescending { it.createdAt }
        }
    }

    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > 600

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = UiTranslations.getString(context, "catalog_title", language),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (!isSetupFlow) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = UiTranslations.getString(context, "btn_back", language))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = UiTranslations.getString(context, "catalog_add_product", language))
                    }
                }
            )
        },
        bottomBar = {
            if (isSetupFlow) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = products.isNotEmpty()
                    ) {
                        Text(UiTranslations.getString(context, "catalog_finish_setup", language))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isSetupFlow) {
                Text(
                    text = UiTranslations.getString(context, "catalog_setup_desc", language),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(UiTranslations.getString(context, "search_product_hint", language)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                } else null,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            // Fila de Ordenamiento (Estilo Clash Royale)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = UiTranslations.getString(context, "sort_by_label", language),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                SortChip(
                    label = UiTranslations.getString(context, "sort_option_name", language), 
                    isSelected = sortOption == SortOption.NAME,
                    isAscending = sortAscending,
                    onClick = {
                        if (sortOption == SortOption.NAME) sortAscending = !sortAscending
                        else { sortOption = SortOption.NAME; sortAscending = true }
                    }
                )
                SortChip(
                    label = UiTranslations.getString(context, "sort_option_price", language), 
                    isSelected = sortOption == SortOption.PRICE,
                    isAscending = sortAscending,
                    onClick = {
                        if (sortOption == SortOption.PRICE) sortAscending = !sortAscending
                        else { sortOption = SortOption.PRICE; sortAscending = true }
                    }
                )
                SortChip(
                    label = UiTranslations.getString(context, "sort_option_date", language), 
                    isSelected = sortOption == SortOption.CREATION_DATE,
                    isAscending = sortAscending,
                    onClick = {
                        if (sortOption == SortOption.CREATION_DATE) sortAscending = !sortAscending
                        else { sortOption = SortOption.CREATION_DATE; sortAscending = true }
                    }
                )
            }

            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = UiTranslations.getString(context, "catalog_empty", language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                if (isLandscape) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredProducts) { product ->
                            ProductItem(
                                product = product,
                                language = language,
                                preferredCurrency = prefCurrency,
                                usdRate = usdRate,
                                eurRate = eurRate,
                                onEdit = { onNavigateToEditor(it.id) },
                                onDelete = { productToDelete = it }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredProducts) { product ->
                            ProductItem(
                                product = product,
                                language = language,
                                preferredCurrency = prefCurrency,
                                usdRate = usdRate,
                                eurRate = eurRate,
                                onEdit = { onNavigateToEditor(it.id) },
                                onDelete = { productToDelete = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text(UiTranslations.getString(context, "catalog_delete_confirm_title", language)) },
            text = { Text(UiTranslations.getString(context, "catalog_delete_confirm_desc", language, productToDelete!!.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(productToDelete!!)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(UiTranslations.getString(context, "btn_confirm", language))
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text(UiTranslations.getString(context, "btn_cancel", language))
                }
            }
        )
    }
}

@Composable
fun SortChip(label: String, isSelected: Boolean, isAscending: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ProductItem(
    product: Product,
    language: String,
    preferredCurrency: String,
    usdRate: Double,
    eurRate: Double,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    val context = LocalContext.current
    val activePriceRaw = product.getActivePrice()
    val basePriceConverted = CurrencyUtils.convert(product.basePrice, product.currency, preferredCurrency, usdRate, eurRate)
    val activePriceConverted = CurrencyUtils.convert(activePriceRaw, product.currency, preferredCurrency, usdRate, eurRate)
    val hasDiscount = activePriceRaw < product.basePrice

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasDiscount) {
                        Text(
                            text = "$preferredCurrency ${String.format("%.2f", basePriceConverted)}", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$preferredCurrency ${String.format("%.2f", activePriceConverted)}", 
                            color = Color(0xFFE53935), // Rojo vibrante para oferta
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalOffer, 
                                contentDescription = null, 
                                modifier = Modifier.size(12.dp).padding(2.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        Text(
                            text = "$preferredCurrency ${String.format("%.2f", basePriceConverted)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = UiTranslations.translateService(product.category, language, context),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { onEdit(product) }) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = UiTranslations.getString(context, "catalog_edit_product", language),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onDelete(product) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = UiTranslations.getString(context, "catalog_cd_delete", language),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
