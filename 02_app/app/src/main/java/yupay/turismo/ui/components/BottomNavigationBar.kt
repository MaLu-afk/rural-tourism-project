package yupay.turismo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yupay.turismo.utils.UiTranslations

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    language: String,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val horizontalPadding = if (isLandscape && configuration.screenWidthDp > 600) {
        (configuration.screenWidthDp * 0.2f).dp
    } else {
        0.dp
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.padding(horizontal = horizontalPadding)
    ) {
        val items = getNavItems()

        items.forEach { item ->
            val isSelected = currentRoute == item.route
            val title = UiTranslations.getString(context, item.titleKey, language)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = title,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun MainNavigationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    language: String = "Español"
) {
    val context = LocalContext.current
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Spacer(modifier = Modifier.height(16.dp))
        }
    ) {
        val items = getNavItems()

        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                val title = UiTranslations.getString(context, item.titleKey, language)
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = title,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

private fun getNavItems() = listOf(
    NavItem("nav_home", Icons.Default.Home, "home"),
    NavItem("nav_visits", Icons.Default.BarChart, "visits"),
    NavItem("nav_map", Icons.Default.Explore, "map"),
    NavItem("nav_profile", Icons.Default.Person, "profile")
)

data class NavItem(
    val titleKey: String,
    val icon: ImageVector,
    val route: String
)
