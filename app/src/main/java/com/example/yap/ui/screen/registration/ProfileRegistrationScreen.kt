package com.example.yap.ui.screen.registration

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.yap.R
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.compose.SystemBarsIconsColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


val UriSaver = Saver<Uri?, String>(
    save = { it?.toString() ?: "" },
    restore = { if (it.isEmpty()) null else Uri.parse(it) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRegistrationScreen(
    initialName: String,
    initialUsername: String = "",
    initialBio: String = "",
    initialDob: Long? = null,
    initialShowOnlyDay: Boolean = false,
    initialAvatarUrl: String? = null,
    onComplete: (name: String, username: String, dob: Long?, showOnlyDay: Boolean, bio: String, photoUri: Uri?, isPhotoRemoved: Boolean) -> Unit,
    isEdit: Boolean = false,
    externalPadding: PaddingValues = PaddingValues(0.dp))
{
    SystemBarsIconsColor(isLight = true)

    var isInitialImageRemoved by rememberSaveable { mutableStateOf(false) }
    var photoUri by rememberSaveable(stateSaver = UriSaver) { mutableStateOf(null) }

    var name by rememberSaveable { mutableStateOf(initialName) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var dobTimestamp by rememberSaveable { mutableStateOf<Long?>(initialDob) }
    var showOnlyDay by rememberSaveable { mutableStateOf(initialShowOnlyDay) }
    var bio by rememberSaveable { mutableStateOf(initialBio) }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }


    val baseScale = LocalBaseScale.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            isInitialImageRemoved = true
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dobTimestamp,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= System.currentTimeMillis()
            }

            override fun isSelectableYear(year: Int): Boolean {
                return year <= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            }
        }
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = externalPadding.calculateTopPadding())
            .padding(horizontal = 16.dp*baseScale)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp*baseScale))

        RegistrationSectionTitle(stringResource(R.string.select_a_photo))

        Text(
            text = stringResource(R.string.dnt_user_other_photo),
            fontSize = 14.sp * baseScale,
            fontWeight = FontWeight.Medium,
            color = LocalAdditionColors.current.secondTextColor,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp * baseScale) )


        val currentImageModel = when {
            photoUri != null -> photoUri
            !isInitialImageRemoved && !initialAvatarUrl.isNullOrEmpty() -> initialAvatarUrl
            else -> null
        }
        val hasImage = currentImageModel != null

        Box(
            modifier = Modifier
                .size(138.dp * baseScale)
                .align(Alignment.Start)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(LocalAdditionColors.current.surfacePhotoColor)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (hasImage) {
                    AsyncImage(
                        model = currentImageModel,
                        contentDescription = "Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Заглушка
                    Box(
                        modifier = Modifier
                            .size(74.dp * baseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.add_image),
                            contentDescription = "Add photo",
                            modifier = Modifier.size(48.dp * baseScale),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (hasImage) {
                Surface(
                    onClick = {
                        if (photoUri != null) {
                            photoUri = null
                        } else {
                            isInitialImageRemoved = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp * baseScale)
                        .size(32.dp * baseScale)
                        .offset(x = -(4).dp, y = 4.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = Color.Black,
                        modifier = Modifier.padding(6.dp * baseScale).fillMaxSize()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Имя
        RegistrationInputField(
            sectionTitle = stringResource(R.string.your_name),
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.input_your_name),
            leadingIconRes = R.drawable.leading_user,
            maxLength = 30
        )

        Spacer(modifier = Modifier.height(20.dp * baseScale))

        // 2. Имя пользователя
        RegistrationInputField(
            sectionTitle = stringResource(R.string.user_name),
            value = username,
            onValueChange = { username = it },
            placeholder = stringResource(R.string.input_your_name),
            leadingIconRes = R.drawable.leading_email,
            maxLength = 30,
            supportingText = stringResource(R.string.unique_nick)
        )

        Spacer(modifier = Modifier.height(20.dp * baseScale))

        // 3. Дата рождения (как поле-кнопка)
        val displayDate = dobTimestamp?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))
        } ?: ""

        RegistrationInputField(
            sectionTitle = stringResource(R.string.how_old),
            value = displayDate,
            onValueChange = {},
            placeholder = stringResource(R.string.birthday),
            leadingIconRes = R.drawable.leading_calendar,
            readOnly = true,
            enabled = false,
            onClick = { showDatePicker = true }
        )

        // Чекбокс под датой
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp * baseScale, start = 16.dp*baseScale)
        ) {
            // Кастомный чекбокс
            Box(
                modifier = Modifier
                    .size(16.dp * baseScale)
                    .clip(RoundedCornerShape(6.dp * baseScale))
                    .background(
                        if (showOnlyDay) MaterialTheme.colorScheme.onSurface
                        else Color.LightGray
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(
                            bounded = false,
                            radius = 16.dp * baseScale
                        )
                    ) {
                        showOnlyDay = !showOnlyDay
                    },
                contentAlignment = Alignment.Center
            ) {
                if (showOnlyDay) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp * baseScale)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp * baseScale))

            Text(
                text = stringResource(R.string.only_birthday),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp * baseScale,
                color = LocalAdditionColors.current.secondTextColor,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showOnlyDay = !showOnlyDay
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp * baseScale))

        // 4. О себе
        RegistrationInputField(
            sectionTitle = stringResource(R.string.about_me),
            value = bio,
            onValueChange = { bio = it },
            placeholder = stringResource(R.string.write_about_me),
            singleLine = false,
            maxLength = 70,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            RegistrationActionButton(
                text =
                    if (isEdit)
                        stringResource(R.string.apply)
                    else
                        stringResource(R.string.complete),
                enabled = name.isNotBlank(),
                onClick = {
                    val finalPhotoUri = if (photoUri != null) {
                        photoUri
                    } else if (isInitialImageRemoved) {
                        null
                    } else {
                        null
                    }
                    onComplete(name, username, dobTimestamp, showOnlyDay, bio,
                        photoUri,
                        isInitialImageRemoved)
                },
            )
        }


        Spacer(modifier = Modifier.height(32.dp * baseScale))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dobTimestamp = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text(
                        text = stringResource(R.string.ok),
                        color = LocalAdditionColors.current.purpleSurfaceColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp * baseScale
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = Color.Gray,
                        fontSize = 18.sp * baseScale
                    )
                }
            },
            shape = RoundedCornerShape(28.dp * baseScale),
            colors = DatePickerDefaults.colors(
                containerColor = LocalAdditionColors.current.purpleLightBackColor
            )
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = {
                    Text(
                        text = stringResource(R.string.birthday),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        color = Color.Gray
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = LocalAdditionColors.current.purpleLightBackColor,
                    selectedDayContainerColor = LocalAdditionColors.current.purpleSurfaceColor,
                    selectedDayContentColor = Color.White,
                    todayContentColor = LocalAdditionColors.current.purpleSurfaceColor,
                    todayDateBorderColor = LocalAdditionColors.current.purpleSurfaceColor,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    weekdayContentColor = Color.Gray,
                    navigationContentColor = LocalAdditionColors.current.purpleSurfaceColor,
                    titleContentColor = Color.Gray,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedYearContainerColor = LocalAdditionColors.current.purpleSurfaceColor,
                    selectedYearContentColor = Color.White,
                    currentYearContentColor = LocalAdditionColors.current.purpleSurfaceColor,
                )
            )
        }
    }
}




@Composable
fun RegistrationSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val baseScale = LocalBaseScale.current

    Text(
        text = text,
        fontSize = 24.sp * baseScale,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth(),
        color = LocalAdditionColors.current.secondTextColor,
        lineHeight = 28.sp * baseScale
    )
}

@Composable
fun RegistrationInputField(
    sectionTitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    @DrawableRes leadingIconRes: Int? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    supportingText: String? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val baseScale = LocalBaseScale.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animationSpec = tween<Color>(durationMillis = 300)

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.LightGray.copy(alpha = 0.5f)
            isFocused -> LocalAdditionColors.current.borderFieldColor
            else -> Color.LightGray.copy(alpha = 0.5f)
        },
        animationSpec = animationSpec,
        label = "BorderColor"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isFocused || value.isNotEmpty()) {
            LocalAdditionColors.current.fieldBackColor
        } else {
            Color.Transparent
        },
        animationSpec = animationSpec,
        label = "ContainerColor"
    )

    val animatedIconTint by animateColorAsState(
        targetValue = if (isFocused && enabled) {
            LocalAdditionColors.current.borderFieldColor
        } else {
            LocalAdditionColors.current.lightGreyColor
        },
        animationSpec = animationSpec,
        label = "IconTint"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        RegistrationSectionTitle(text = sectionTitle)
        Spacer(modifier = Modifier.height(8.dp * baseScale))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 62.dp * baseScale else 120.dp * baseScale)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(if (singleLine) 41.dp * baseScale else 20.dp * baseScale),
            color = animatedContainerColor,
            border = BorderStroke(width = 2.dp, color = animatedBorderColor)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (maxLength == null || it.length <= maxLength) onValueChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    fontSize = 16.sp * baseScale,
                    fontWeight = FontWeight.Bold,
                    color = LocalAdditionColors.current.secondTextColor
                ),
                placeholder = {
                    Text(
                        text = placeholder,
                        modifier = Modifier.padding(start = 4.dp * baseScale),
                        color = LocalAdditionColors.current.lightGreyColor,
                        fontSize = 16.sp * baseScale,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = leadingIconRes?.let { resId ->
                    {
                        Icon(
                            painter = painterResource(id = resId),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp * baseScale),
                            tint = animatedIconTint
                        )
                    }
                },
                readOnly = readOnly,
                enabled = enabled,
                singleLine = singleLine,
                shape = RoundedCornerShape(if (singleLine) 41.dp * baseScale else 20.dp * baseScale),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = LocalAdditionColors.current.borderFieldColor,
                    focusedTextColor = LocalAdditionColors.current.secondTextColor,
                    unfocusedTextColor = LocalAdditionColors.current.secondTextColor,
                    disabledTextColor = LocalAdditionColors.current.secondTextColor
                )
            )
        }

        // Supporting Text
        if (supportingText != null || maxLength != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp * baseScale, start = 16.dp * baseScale, end = 16.dp * baseScale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp * baseScale,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAdditionColors.current.secondTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (maxLength != null) {
                    Text(
                        text = "${value.length}/$maxLength",
                        fontSize = 14.sp * baseScale,
                        fontWeight = FontWeight.Medium,
                        color = LocalAdditionColors.current.secondTextColor,
                    )
                }
            }
        }
    }
}

@Composable
fun RegistrationActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes iconRes: Int? = R.drawable.outline_arrow_forward_24
) {
    val baseScale = LocalBaseScale.current

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(44.dp * baseScale),
        shape = RoundedCornerShape(28.dp * baseScale),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = MaterialTheme.colorScheme.background,
            disabledContainerColor = Color.Black.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(
            start = 32.dp * baseScale,
            end = 16.dp * baseScale
        )
    ) {
        // Используем Row БЕЗ fillMaxSize.
        // Теперь ширина кнопки = ширина Текста + Spacer + Иконка
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp * baseScale,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
            )

            if (iconRes != null) {
                Spacer(modifier = Modifier.width(12.dp * baseScale))
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp * baseScale),
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}