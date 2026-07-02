package yupay.turismo.ui.features.profile

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.components.CountdownConfirmationDialog
import yupay.turismo.ui.components.ServiceOption
import yupay.turismo.ui.components.ServiceSelectorGrid
import yupay.turismo.ui.components.UnsavedChangesDialog
import yupay.turismo.utils.UiTranslations
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    val language = settings?.language ?: "Español"

    // IMPORTANTE: keyear en el VALOR del campo (businessName/businessCategory), NO en el objeto
    // `settings` completo. Una sync en segundo plano reescribe AppSettings en cada ciclo (avanza
    // lastSyncAt/lastModified) y el Flow `appSettings` re-emite un objeto nuevo. Con key=`settings`,
    // remember re-inicializaba estos estados a los valores del servidor y borraba lo que el usuario
    // estaba escribiendo → los cambios "se revertían" mientras esperaba. Keyeando al valor, una sync
    // que no tocó el perfil no cambia el key y la edición en curso se conserva.
    var businessName by remember(settings?.businessName) { mutableStateOf(settings?.businessName ?: "") }
    var selectedService by remember(settings?.businessCategory) { mutableStateOf(settings?.businessCategory ?: "") }
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var editingUri by remember { mutableStateOf<Uri?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showSectorWarning by remember { mutableStateOf(false) }

    val hasChanges = (businessName != (settings?.businessName ?: "")) ||
            (selectedService != (settings?.businessCategory ?: "")) ||
            (pendingBitmap != null)

    BackHandler(enabled = hasChanges) {
        showExitDialog = true
    }

    val services = listOf(
        ServiceOption(UiTranslations.translateService("Hospedaje", language, context), Icons.Default.Bed),
        ServiceOption(UiTranslations.translateService("Alimentación", language, context), Icons.Default.Restaurant),
        ServiceOption(UiTranslations.translateService("Artesanía", language, context), Icons.Default.Checkroom),
        ServiceOption(UiTranslations.translateService("Varios", language, context), Icons.Default.Storefront)
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            editingUri = uri
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        UiTranslations.getString(context, "profile_edit_title", language),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) showExitDialog = true else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = UiTranslations.getString(context, "btn_back", language),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp && configuration.screenWidthDp > 600

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left Column: Photo and Name
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { showImageSourceDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (pendingBitmap != null) {
                            AsyncImage(
                                model = pendingBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (settings?.profilePicture != null) {
                            AsyncImage(
                                model = settings?.profilePicture,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 8.dp) // Center properly on the circle edge
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = UiTranslations.getString(context, "profile_business_name", language),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { if (!it.contains("\n")) businessName = it },
                            placeholder = { Text(UiTranslations.getString(context, "setup_business_name_hint", language)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            val byteArray = pendingBitmap?.let { bitmap ->
                                val outputStream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                outputStream.toByteArray()
                            }
                            viewModel.saveProfile(businessName, selectedService, byteArray)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = businessName.isNotBlank() && selectedService.isNotBlank()
                    ) {
                        Text(UiTranslations.getString(context, "profile_save_changes", language), fontWeight = FontWeight.Bold)
                    }
                }

                // Right Column: Service Grid
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = UiTranslations.getString(context, "profile_sector", language),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ServiceSelectorGrid(
                        services = services,
                        selectedServices = if (selectedService.isEmpty()) emptySet() else setOf(selectedService.let { 
                            UiTranslations.translateService(it, language, context)
                        }),
                        onServiceToggle = { serviceNameTranslated ->
                            val originalName = when(serviceNameTranslated) {
                                UiTranslations.translateService("Hospedaje", language, context) -> "Hospedaje"
                                UiTranslations.translateService("Alimentación", language, context) -> "Alimentación"
                                UiTranslations.translateService("Artesanía", language, context) -> "Artesanía"
                                UiTranslations.translateService("Varios", language, context) -> "Varios"
                                else -> serviceNameTranslated
                            }
                            if (originalName != (settings?.businessCategory ?: "")) {
                                showSectorWarning = true
                            }
                            selectedService = originalName
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { showImageSourceDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (pendingBitmap != null) {
                        AsyncImage(
                            model = pendingBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (settings?.profilePicture != null) {
                        AsyncImage(
                            model = settings?.profilePicture,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp) // Offset to position on the edge circle
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = UiTranslations.getString(context, "profile_business_name", language),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { 
                            if (!it.contains("\n")) businessName = it 
                        },
                        placeholder = { Text(UiTranslations.getString(context, "setup_business_name_hint", language)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = UiTranslations.getString(context, "profile_sector", language),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                ServiceSelectorGrid(
                    services = services,
                    selectedServices = if (selectedService.isEmpty()) emptySet() else setOf(selectedService.let { 
                        UiTranslations.translateService(it, language, context)
                    }),
                    onServiceToggle = { serviceNameTranslated ->
                    val originalName = when(serviceNameTranslated) {
                        UiTranslations.translateService("Hospedaje", language, context) -> "Hospedaje"
                        UiTranslations.translateService("Alimentación", language, context) -> "Alimentación"
                        UiTranslations.translateService("Artesanía", language, context) -> "Artesanía"
                        UiTranslations.translateService("Varios", language, context) -> "Varios"
                        else -> serviceNameTranslated
                    }
                    selectedService = originalName
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (selectedService != (settings?.businessCategory ?: "")) {
                        showSectorWarning = true
                    } else {
                        val byteArray = pendingBitmap?.let { bitmap ->
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.toByteArray()
                        }
                        viewModel.saveProfile(businessName, selectedService, byteArray)
                        onBack()
                    }
                },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = businessName.isNotBlank() && selectedService.isNotBlank()
                ) {
                    Text(UiTranslations.getString(context, "profile_save_changes", language), fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        editingUri?.let { uri ->
            ImageEditorDialog(
                uri = uri,
                language = language,
                onDismiss = { editingUri = null },
                onConfirm = { bitmap ->
                    pendingBitmap = bitmap
                    editingUri = null
                }
            )
        }

        if (showImageSourceDialog) {
            AlertDialog(
                onDismissRequest = { showImageSourceDialog = false },
                title = { Text(UiTranslations.getString(context, "profile_photo_change", language)) },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text(UiTranslations.getString(context, "edit_profile_gallery", language)) },
                            leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            modifier = Modifier.clickable {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                showImageSourceDialog = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImageSourceDialog = false }) {
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

        if (showSectorWarning) {
            CountdownConfirmationDialog(
                language = language,
                titleKey = "profile_sector_change_title",
                descKey = "profile_sector_change_desc",
                seconds = 10,
                onConfirm = {
                    val byteArray = pendingBitmap?.let { bitmap ->
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.toByteArray()
                    }
                    viewModel.saveProfile(businessName, selectedService, byteArray)
                    showSectorWarning = false
                    onBack()
                },
                onDismiss = { showSectorWarning = false }
            )
        }
    }
}

@Composable
fun ImageEditorDialog(
    uri: Uri,
    language: String,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Referencias para el recorte
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(uri) {
        val inputStream = context.contentResolver.openInputStream(uri)
        sourceBitmap = BitmapFactory.decodeStream(inputStream)
    }

    if (sourceBitmap == null) {
        AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, text = { CircularProgressIndicator() })
        return
    }

    val bitmap = sourceBitmap!!
    
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text(UiTranslations.getString(context, "profile_photo_change", language)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Calcular límites para que el selector siempre esté dentro de la imagen
                                val imgWidth = bitmap.width.toFloat()
                                val imgHeight = bitmap.height.toFloat()
                                
                                val selectorSizeOnCanvas = canvasSize.width * 0.8f
                                
                                // El factor de escala base que hace que la imagen cubra el canvas es:
                                val baseDrawScale = Math.max(canvasSize.width / imgWidth, canvasSize.height / imgHeight)
                                
                                val minScaleX = selectorSizeOnCanvas / (baseDrawScale * imgWidth)
                                val minScaleY = selectorSizeOnCanvas / (baseDrawScale * imgHeight)
                                val absoluteMinScale = Math.max(minScaleX, minScaleY)

                                scale = (scale * zoom).coerceAtLeast(absoluteMinScale)
                                
                                // Limit Pan to keep selector inside image bounds
                                val scaledW = imgWidth * baseDrawScale * scale
                                val scaledH = imgHeight * baseDrawScale * scale
                                
                                val maxPanX = (scaledW - selectorSizeOnCanvas) / 2
                                val maxPanY = (scaledH - selectorSizeOnCanvas) / 2
                                
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxPanX.coerceAtLeast(0f), maxPanX.coerceAtLeast(0f)),
                                    (offset.y + pan.y).coerceIn(-maxPanY.coerceAtLeast(0f), maxPanY.coerceAtLeast(0f))
                                )
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        canvasSize = size
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        withTransform({
                            translate(offset.x, offset.y)
                            scale(scale, scale, Offset(canvasWidth/2, canvasHeight/2))
                        }) {
                            val imgWidth = bitmap.width.toFloat()
                            val imgHeight = bitmap.height.toFloat()
                            val drawScale = Math.max(canvasWidth / imgWidth, canvasHeight / imgHeight)
                            
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = IntOffset(
                                    ((canvasWidth - imgWidth * drawScale) / 2).toInt(),
                                    ((canvasHeight - imgHeight * drawScale) / 2).toInt()
                                ),
                                dstSize = IntSize(
                                    (imgWidth * drawScale).toInt(),
                                    (imgHeight * drawScale).toInt()
                                )
                            )
                        }

                        // Selector cuadrado
                        val selectorSize = canvasWidth * 0.8f
                        val left = (canvasWidth - selectorSize) / 2
                        val top = (canvasHeight - selectorSize) / 2
                        
                        val squarePath = Path().apply {
                            addRect(Rect(left, top, left + selectorSize, top + selectorSize))
                        }
                        
                        clipPath(squarePath, clipOp = ClipOp.Difference) {
                            drawRect(color = Color.Black.copy(alpha = 0.6f))
                        }
                        
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(left, top),
                            size = Size(selectorSize, selectorSize),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = UiTranslations.getString(context, "edit_profile_crop_hint", language),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cropped = performCrop(bitmap, scale, offset, canvasSize)
                onConfirm(cropped)
            }) {
                Text(UiTranslations.getString(context, "btn_confirm", language))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(UiTranslations.getString(context, "btn_cancel", language))
            }
        }
    )
}

/**
 * Realiza el recorte de la imagen basado en la transformación visual del usuario.
 */
private fun performCrop(bitmap: Bitmap, scale: Float, offset: Offset, canvasSize: Size): Bitmap {
    val imgWidth = bitmap.width.toFloat()
    val imgHeight = bitmap.height.toFloat()
    
    // 1. Escala base utilizada en el drawImage del Canvas
    val baseDrawScale = Math.max(canvasSize.width / imgWidth, canvasSize.height / imgHeight)
    
    // 2. El tamaño del selector en el canvas
    val selectorSize = canvasSize.width * 0.8f
    val selectorLeft = (canvasSize.width - selectorSize) / 2
    val selectorTop = (canvasSize.height - selectorSize) / 2

    // 3. Crear un bitmap de salida cuadrado (ej. 512x512 para buena calidad)
    val outputSize = 512
    val croppedBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(croppedBitmap)
    
    // 4. Mapear las coordenadas del selector de vuelta a los píxeles originales del bitmap
    // La imagen en el canvas está centrada, luego desplazada por 'offset' y escalada por 'scale'
    
    val matrix = android.graphics.Matrix()
    
    // Replicar la lógica del withTransform de Compose:
    // a) Centrar y escalar base
    val initialTranslateX = (canvasSize.width - imgWidth * baseDrawScale) / 2
    val initialTranslateY = (canvasSize.height - imgHeight * baseDrawScale) / 2
    
    matrix.postScale(baseDrawScale, baseDrawScale)
    matrix.postTranslate(initialTranslateX, initialTranslateY)
    
    // b) Transformaciones del usuario (Translate -> Scale alrededor del centro del canvas)
    matrix.postTranslate(offset.x, offset.y)
    matrix.postScale(scale, scale, canvasSize.width / 2f, canvasSize.height / 2f)
    
    // c) Ahora queremos la inversa para saber qué parte del bitmap original está bajo el selector
    val inverse = android.graphics.Matrix()
    matrix.invert(inverse)
    
    // d) Dibujar el bitmap original en el canvas de salida usando la inversa de la matriz
    // pero desplazado para que el selector (selectorLeft, selectorTop) sea el (0,0) del output
    val outputMatrix = android.graphics.Matrix()
    outputMatrix.set(matrix)
    outputMatrix.postTranslate(-selectorLeft, -selectorTop)
    outputMatrix.postScale(outputSize / selectorSize, outputSize / selectorSize)
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(bitmap, outputMatrix, paint)
    
    return croppedBitmap
}
